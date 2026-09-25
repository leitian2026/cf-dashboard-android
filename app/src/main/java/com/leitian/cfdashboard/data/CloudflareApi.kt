package com.leitian.cfdashboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.MultipartReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object CloudflareApi {

    private const val BASE = "https://api.cloudflare.com/client/v4"
    private const val GRAPHQL = "https://api.cloudflare.com/client/v4/graphql"

    private val client: OkHttpClient by lazy {
        // 日志级别由 NetworkLogging 统一控制（默认关闭），打开/关闭时直接改这个共享
        // 拦截器实例的 level，不需要重建 OkHttpClient。
        OkHttpClient.Builder()
            .addInterceptor(NetworkLogging.interceptor)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    data class ApiResult<T>(val success: Boolean, val data: T? = null, val error: String? = null)
    data class Account(val id: String, val name: String)

    data class AppItem(
        val id: String,
        val name: String,
        val subtitle: String,
        val domain: String,
        val updatedAt: String,
        val isPages: Boolean,
        // 这个 App 属于哪个 Cloudflare 账号——同时显示多账号内容时，用来在列表里打标签、
        // 以及点进详情页时知道该用哪套邮箱/Key。
        val accountId: String = "",
        val accountName: String = ""
    )

    data class AccountStats(
        val requests: Long,
        val errors: Long,
        val cpuTimeMs: Double
    )

    data class WorkerMetrics(
        val totalRequests: Long,
        val totalErrors: Long,
        val totalSubrequests: Long,
        val cpuTimeMs: Double,
        val requestPoints: List<Float>,
        val requestRatePoints: List<Float>,
        val cpuPoints: List<Float>,
        val errorPoints: List<Float>,
        val subrequestPoints: List<Float>,
        // 每个采样点对应的 UTC 15 分钟桶 key（"yyyy-MM-ddTHH:mm"），和上面几个 points 一一对应，
        // 用来在图表上画时间刻度、以及比对部署时间落在哪个柱子上。
        val bucketKeys: List<String>,
        // 相比前一个 24 小时周期的变化百分比；前一周期基数为 0 时给 null（避免除零/无意义的巨大百分比）。
        val requestsChangePct: Double?,
        val subrequestsChangePct: Double?,
        val errorsChangePct: Double?
    )

    data class ScriptInfo(
        val subdomain: String,
        val logsEnabled: Boolean,
        val tracesEnabled: Boolean,
        val bindingCount: Int
    )

    private fun authGet(email: String, apiKey: String, url: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .get()
            .build()
    }

    private fun authPost(email: String, apiKey: String, url: String, body: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun utcTodayRange(): Pair<String, String> {
        val utc = TimeZone.getTimeZone("UTC")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = utc }
        val end = Date()
        val cal = java.util.Calendar.getInstance(utc).apply {
            time = end
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return sdf.format(cal.time) to sdf.format(end)
    }

    private fun last24HoursRange(): Pair<String, String> {
        val utc = TimeZone.getTimeZone("UTC")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = utc }
        val end = Date()
        val start = Date(end.time - 24L * 60 * 60 * 1000)
        return sdf.format(start) to sdf.format(end)
    }

    /** 再往前推 24 小时的同长度区间，用来算"相比前一周期"的百分比变化。 */
    private fun previous24HoursRange(): Pair<String, String> {
        val utc = TimeZone.getTimeZone("UTC")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = utc }
        val end = Date()
        val periodEnd = Date(end.time - 24L * 60 * 60 * 1000)
        val periodStart = Date(end.time - 48L * 60 * 60 * 1000)
        return sdf.format(periodStart) to sdf.format(periodEnd)
    }

    /** (当前值 - 上一周期值) / 上一周期值 * 100；上一周期为 0 时返回 null，不算无意义的百分比。 */
    private fun changePct(current: Long, previous: Long): Double? =
        if (previous <= 0L) null else (current - previous).toDouble() / previous.toDouble() * 100.0

    private fun bucketKey15m(dt: String): String {
        if (dt.length < 16) return if (dt.length >= 13) dt.substring(0, 13) else dt
        val hourPart = dt.substring(0, 13)
        val minute = dt.substring(14, 16).toIntOrNull() ?: 0
        val m = (minute / 15) * 15
        return "$hourPart:${m.toString().padStart(2, '0')}"
    }

    private fun truncate(s: String, max: Int = 300): String =
        if (s.length <= max) s else s.take(max) + "..."

    private fun graphQlHasRealErrors(json: JSONObject): String? {
        if (!json.has("errors") || json.isNull("errors")) return null
        val errArr = json.optJSONArray("errors") ?: return null
        if (errArr.length() == 0) return null
        return errArr.optJSONObject(0)?.optString("message") ?: "GraphQL error"
    }

    suspend fun verifyGlobalKey(email: String, apiKey: String): ApiResult<Account> =
        withContext(Dispatchers.IO) {
            try {
                val req = authGet(email, apiKey, "$BASE/accounts")
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext ApiResult(false, error = "验证失败 (${resp.code}): ${truncate(body)}")
                val json = JSONObject(body)
                if (!json.optBoolean("success", false)) {
                    val err = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                    return@withContext ApiResult(false, error = err ?: "登录失败")
                }
                val arr = json.optJSONArray("result") ?: JSONArray()
                if (arr.length() == 0) return@withContext ApiResult(false, error = "未找到账号")
                val o = arr.getJSONObject(0)
                ApiResult(true, Account(o.optString("id"), o.optString("name")))
            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "网络错误")
            }
        }

    private fun fetchWorkersSubdomainPrefix(email: String, apiKey: String, accountId: String): String? {
        return try {
            val resp = client.newCall(authGet(email, apiKey, "$BASE/accounts/$accountId/workers/subdomain")).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return null
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return null
            json.optJSONObject("result")?.optString("subdomain")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    // 原来 getApps() 是 fetchWorkersSubdomainPrefix -> workers/scripts -> pages/projects 三个请求全串行，
    // 一次性拼成一个大 list 才 emit，所以首页表现是转一下然后全部数据一起蹦出来。
    // 拆成 Workers / Pages 两个独立函数，ViewModel 那边并发发出、谁先回来谁先显示。
    suspend fun getWorkerScripts(email: String, apiKey: String, accountId: String, accountName: String = ""): ApiResult<List<AppItem>> =
        withContext(Dispatchers.IO) {
            try {
                val list = mutableListOf<AppItem>()
                val workersPrefix = fetchWorkersSubdomainPrefix(email, apiKey, accountId)
                val workersResp = client.newCall(authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts")).execute()
                val workersBody = workersResp.body?.string() ?: ""
                if (workersResp.isSuccessful) {
                    val arr = JSONObject(workersBody).optJSONArray("result") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.optString("id")
                        val domain = if (!workersPrefix.isNullOrBlank()) {
                            "$id.$workersPrefix.workers.dev"
                        } else {
                            "$id.workers.dev"
                        }
                        list.add(
                            AppItem(
                                id = id,
                                name = id,
                                subtitle = "Worker",
                                domain = domain,
                                updatedAt = o.optString("modified_on").take(19).replace("T", " ").ifBlank { "—" },
                                isPages = false,
                                accountId = accountId,
                                accountName = accountName
                            )
                        )
                    }
                    ApiResult(true, list)
                } else {
                    ApiResult(false, error = "加载 Workers 列表失败（${workersResp.code}）")
                }
            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "网络错误")
            }
        }

    suspend fun getPagesProjects(email: String, apiKey: String, accountId: String, accountName: String = ""): ApiResult<List<AppItem>> =
        withContext(Dispatchers.IO) {
            try {
                val list = mutableListOf<AppItem>()
                val pagesResp = client.newCall(authGet(email, apiKey, "$BASE/accounts/$accountId/pages/projects")).execute()
                val pagesBody = pagesResp.body?.string() ?: ""
                if (pagesResp.isSuccessful) {
                    val arr = JSONObject(pagesBody).optJSONArray("result") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val name = o.optString("name")
                        val subdomain = o.optString("subdomain").ifBlank { "$name.pages.dev" }
                        list.add(
                            AppItem(
                                id = o.optString("id", name),
                                name = name,
                                subtitle = "Pages",
                                domain = subdomain,
                                updatedAt = o.optString("created_on").take(19).replace("T", " ").ifBlank { "—" },
                                isPages = true,
                                accountId = accountId,
                                accountName = accountName
                            )
                        )
                    }
                    ApiResult(true, list)
                } else {
                    ApiResult(false, error = "加载 Pages 列表失败（${pagesResp.code}）")
                }
            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "网络错误")
            }
        }

    suspend fun createWorker(email: String, apiKey: String, accountId: String, name: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val scriptName = name.trim()
                if (!scriptName.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                    return@withContext ApiResult(false, error = "名称只能包含字母、数字、下划线和短横线")
                }
                // ES Module：脚本 part 用 application/javascript+module；metadata 用 application/json
                // 否则 CF 按旧式 Service Worker 解析，顶层 export 会报 Unexpected token 'export'
                val defaultScript = "export default {\n  async fetch(request, env, ctx) {\n    return new Response('Hello from $scriptName!');\n  }\n}\n"
                val metadataJson = """{"main_module":"worker.js","compatibility_date":"2024-01-01"}"""
                // metadata part 必须先于脚本 part，否则部分情况下 CF 会按旧式 Service Worker 解析
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "metadata",
                        null,
                        metadataJson.toRequestBody("application/json".toMediaType())
                    )
                    .addFormDataPart(
                        "worker.js",
                        "worker.js",
                        defaultScript.toRequestBody("application/javascript+module".toMediaType())
                    )
                    .build()
                val req = Request.Builder().url("$BASE/accounts/$accountId/workers/scripts/$scriptName")
                    .addHeader("X-Auth-Email", email).addHeader("X-Auth-Key", apiKey).put(body).build()
                val resp = client.newCall(req).execute()
                val respBody = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    val err = try { JSONObject(respBody).optJSONArray("errors")?.optJSONObject(0)?.optString("message") } catch (_: Exception) { null }
                    return@withContext ApiResult(false, error = err ?: "创建失败 (${resp.code})")
                }
                ApiResult(true, Unit)
            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "网络错误")
            }
        }

    suspend fun uploadWorkerScript(
        email: String, apiKey: String, accountId: String, scriptName: String, fileName: String, bytes: ByteArray
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            // CF 的 PUT /workers/scripts/{name} 是整体替换 metadata 的:凡是没有出现在
            // 这次提交的 metadata 里的字段(bindings 里的变量/Secret、compatibility_flags、
            // placement、observability 等),都会被直接清空,不是"只改动提到的部分"。
            // 所以这里先读一遍这个 Worker 当前的 settings,把已知这几项原样带回去,
            // 只换 main_module 和脚本内容本身,其余配置维持不变。
            //
            // 注意:Cron 触发器(schedules)不在这个 settings 对象里,是完全独立的接口,
            // 本来就不受这次上传影响。
            val settingsReq = Request.Builder()
                .url("$BASE/accounts/$accountId/workers/scripts/$scriptName/settings")
                .addHeader("X-Auth-Email", email).addHeader("X-Auth-Key", apiKey).get().build()
            val settingsResp = client.newCall(settingsReq).execute()
            val settingsBody = settingsResp.body?.string() ?: ""
            if (!settingsResp.isSuccessful) {
                return@withContext ApiResult(false, error = "上传前读取现有设置失败 (${settingsResp.code})，为避免误清空其他配置已取消上传")
            }
            val settingsJson = JSONObject(settingsBody)
            if (!settingsJson.optBoolean("success", false)) {
                val err = settingsJson.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                return@withContext ApiResult(false, error = "上传前读取现有设置失败：${err ?: "未知错误"}，为避免误清空其他配置已取消上传")
            }
            val existing = settingsJson.optJSONObject("result") ?: JSONObject()

            // 模块名固定写死成 worker.js,不用本地选中文件的真实文件名——
            // 这样不管上传的文件叫什么(带空格、中文、括号等特殊字符都行),
            // 都不会因为文件名本身导致 multipart part 名字和 main_module 对不上而报错。
            val metadata = JSONObject().put("main_module", "worker.js")
            if (existing.has("bindings")) metadata.put("bindings", existing.getJSONArray("bindings"))
            if (existing.has("compatibility_date")) metadata.put("compatibility_date", existing.getString("compatibility_date"))
            if (existing.has("compatibility_flags")) metadata.put("compatibility_flags", existing.getJSONArray("compatibility_flags"))
            if (existing.has("usage_model")) metadata.put("usage_model", existing.getString("usage_model"))
            if (existing.has("placement")) metadata.put("placement", existing.getJSONObject("placement"))
            if (existing.has("tags")) metadata.put("tags", existing.getJSONArray("tags"))
            if (existing.has("observability")) metadata.put("observability", existing.getJSONObject("observability"))
            if (existing.has("logpush")) metadata.put("logpush", existing.getBoolean("logpush"))
            if (existing.has("limits")) metadata.put("limits", existing.getJSONObject("limits"))
            if (!metadata.has("compatibility_date")) metadata.put("compatibility_date", "2024-01-01")

            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata",
                    null,
                    metadata.toString().toRequestBody("application/json".toMediaType())
                )
                .addFormDataPart(
                    "worker.js",
                    "worker.js",
                    bytes.toRequestBody("application/javascript+module".toMediaType())
                )
                .build()
            val req = Request.Builder().url("$BASE/accounts/$accountId/workers/scripts/$scriptName")
                .addHeader("X-Auth-Email", email).addHeader("X-Auth-Key", apiKey).put(body).build()
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val err = try { JSONObject(respBody).optJSONArray("errors")?.optJSONObject(0)?.optString("message") } catch (_: Exception) { null }
                return@withContext ApiResult(false, error = err ?: "上传失败 (${resp.code})")
            }
            ApiResult(true, Unit)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    /**
     * 下载 Worker 当前生效版本的脚本源码。
     * CF 的 GET /workers/scripts/{name} 对 module worker 返回 multipart/form-data
     * （metadata 部分 + 模块文件部分），对旧式 service worker 直接返回纯文本脚本，
     * 这里两种情况都做了兼容解析。返回值为 (建议文件名, 原始字节)。
     *
     * 注意：该接口只能拿到"当前生效"的这一份代码，CF 没有开放按 version_id 下载
     * 历史版本源码的接口（/versions/{version_id} 只返回该版本的元数据，不含源码）。
     */
    suspend fun downloadWorkerScript(
        email: String, apiKey: String, accountId: String, scriptName: String
    ): ApiResult<Pair<String, ByteArray>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName")
            val resp = client.newCall(req).execute()
            val body = resp.body
            if (!resp.isSuccessful) {
                val text = body?.string() ?: ""
                val err = try { JSONObject(text).optJSONArray("errors")?.optJSONObject(0)?.optString("message") } catch (_: Exception) { null }
                return@withContext ApiResult(false, error = err ?: "下载失败 (${resp.code})")
            }
            if (body == null) return@withContext ApiResult(false, error = "响应为空")
            val defaultName = if (scriptName.endsWith(".js", true) || scriptName.endsWith(".mjs", true)) scriptName else "$scriptName.js"
            val contentType = body.contentType()
            if (contentType != null && contentType.type.equals("multipart", ignoreCase = true)) {
                val boundary = contentType.parameter("boundary")
                if (boundary.isNullOrBlank()) return@withContext ApiResult(false, error = "无法解析返回的脚本格式")
                var fileName: String? = null
                var content: ByteArray? = null
                MultipartReader(body.source(), boundary).use { reader ->
                    var part = reader.nextPart()
                    while (part != null) {
                        val disposition = part.headers["Content-Disposition"] ?: ""
                        val partName = Regex("name=\"([^\"]*)\"").find(disposition)?.groupValues?.get(1)
                        val partFileName = Regex("filename=\"([^\"]*)\"").find(disposition)?.groupValues?.get(1)
                        val bytes = part.body.readByteArray()
                        if (content == null && !partName.equals("metadata", ignoreCase = true)) {
                            content = bytes
                            if (!partFileName.isNullOrBlank()) fileName = partFileName
                        }
                        part.close()
                        part = reader.nextPart()
                    }
                }
                val finalContent = content
                if (finalContent == null) ApiResult(false, error = "未能从返回结果中解析出脚本内容")
                else ApiResult(true, (fileName ?: defaultName) to finalContent)
            } else {
                val bytes = body.bytes()
                if (bytes.isEmpty()) ApiResult(false, error = "脚本内容为空")
                else ApiResult(true, defaultName to bytes)
            }
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun getAccountStats(email: String, apiKey: String, accountId: String): ApiResult<AccountStats> =
        withContext(Dispatchers.IO) {
            try {
                val (start, end) = utcTodayRange()
                val query = """
                    query {
                      viewer {
                        accounts(filter: {accountTag: "$accountId"}) {
                          workersInvocationsAdaptive(limit: 10000, filter: { datetime_geq: "$start", datetime_leq: "$end" }) {
                            sum { requests errors }
                            quantiles { cpuTimeP50 }
                          }
                        }
                      }
                    }
                """.trimIndent()
                val resp = client.newCall(authPost(email, apiKey, GRAPHQL, JSONObject().put("query", query).toString())).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext ApiResult(false, error = "统计查询失败 (${resp.code})")
                val json = JSONObject(body)
                graphQlHasRealErrors(json)?.let { return@withContext ApiResult(false, error = it) }
                val rows = json.optJSONObject("data")?.optJSONObject("viewer")?.optJSONArray("accounts")?.optJSONObject(0)?.optJSONArray("workersInvocationsAdaptive") ?: JSONArray()
                var reqs = 0L; var errs = 0L; var cpuSum = 0.0; var cpuCnt = 0
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val sum = row.optJSONObject("sum"); val q = row.optJSONObject("quantiles")
                    reqs += sum?.optLong("requests") ?: 0L
                    errs += sum?.optLong("errors") ?: 0L
                    val cpuUs = q?.optDouble("cpuTimeP50") ?: 0.0
                    if (cpuUs > 0) { cpuSum += cpuUs / 1000.0; cpuCnt++ }
                }
                ApiResult(true, AccountStats(reqs, errs, if (cpuCnt > 0) cpuSum / cpuCnt else 0.0))
            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "网络错误")
            }
        }

    suspend fun getWorkerMetrics(
        email: String, apiKey: String, accountId: String, scriptName: String
    ): ApiResult<WorkerMetrics> = withContext(Dispatchers.IO) {
        try {
            val (start, end) = last24HoursRange()
            val (prevStart, prevEnd) = previous24HoursRange()
            val filter = """scriptName: "$scriptName", datetime_geq: "$start", datetime_leq: "$end""""
            val prevFilter = """scriptName: "$scriptName", datetime_geq: "$prevStart", datetime_leq: "$prevEnd""""

            val summaryQuery = """
                query {
                  viewer {
                    accounts(filter: {accountTag: "$accountId"}) {
                      workersInvocationsAdaptive(
                        limit: 1,
                        filter: { $filter }
                      ) {
                        sum { requests errors subrequests }
                        quantiles { cpuTimeP50 }
                      }
                    }
                  }
                }
            """.trimIndent()

            // 上一个 24 小时周期，只要总量，用来算涨跌百分比（跟真正的 Cloudflare 面板一样）。
            val prevSummaryQuery = """
                query {
                  viewer {
                    accounts(filter: {accountTag: "$accountId"}) {
                      workersInvocationsAdaptive(
                        limit: 1,
                        filter: { $prevFilter }
                      ) {
                        sum { requests errors subrequests }
                      }
                    }
                  }
                }
            """.trimIndent()

            val seriesQuery = """
                query {
                  viewer {
                    accounts(filter: {accountTag: "$accountId"}) {
                      workersInvocationsAdaptive(
                        limit: 10000,
                        filter: { $filter }
                      ) {
                        sum { requests errors subrequests }
                        quantiles { cpuTimeP50 }
                        dimensions { datetime }
                      }
                    }
                  }
                }
            """.trimIndent()

            data class GqlOutcome(val ok: Boolean, val rows: JSONArray, val error: String? = null)

            fun runQuery(query: String): GqlOutcome {
                val resp = client.newCall(
                    authPost(email, apiKey, GRAPHQL, JSONObject().put("query", query).toString())
                ).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return GqlOutcome(false, JSONArray(), "指标查询失败 (${resp.code})")
                }
                val json = JSONObject(body)
                graphQlHasRealErrors(json)?.let {
                    return GqlOutcome(false, JSONArray(), it)
                }
                val rows = json.optJSONObject("data")
                    ?.optJSONObject("viewer")
                    ?.optJSONArray("accounts")
                    ?.optJSONObject(0)
                    ?.optJSONArray("workersInvocationsAdaptive")
                    ?: JSONArray()
                return GqlOutcome(true, rows)
            }

            val (summary, series, prevSummary) = coroutineScope {
                val s = async { runQuery(summaryQuery) }
                val t = async { runQuery(seriesQuery) }
                val p = async { runQuery(prevSummaryQuery) }
                Triple(s.await(), t.await(), p.await())
            }

            if (!summary.ok && !series.ok) {
                return@withContext ApiResult(false, error = summary.error ?: series.error ?: "指标查询失败")
            }

            var totalReq = 0L
            var totalErr = 0L
            var totalSub = 0L
            var cpuMs = 0.0
            if (summary.ok && summary.rows.length() > 0) {
                for (i in 0 until summary.rows.length()) {
                    val row = summary.rows.getJSONObject(i)
                    val sum = row.optJSONObject("sum")
                    val q = row.optJSONObject("quantiles")
                    totalReq += sum?.optLong("requests") ?: 0L
                    totalErr += sum?.optLong("errors") ?: 0L
                    totalSub += sum?.optLong("subrequests") ?: 0L
                    val cpuUs = q?.optDouble("cpuTimeP50") ?: 0.0
                    if (cpuUs > 0) {
                        cpuMs = cpuUs / 1000.0
                    }
                }
            }

            // 上一周期总量，只用来算百分比，查询失败就当没有基数，不显示涨跌箭头（而不是报错整个请求）。
            var prevReq = 0L
            var prevErr = 0L
            var prevSub = 0L
            if (prevSummary.ok) {
                for (i in 0 until prevSummary.rows.length()) {
                    val sum = prevSummary.rows.getJSONObject(i).optJSONObject("sum")
                    prevReq += sum?.optLong("requests") ?: 0L
                    prevErr += sum?.optLong("errors") ?: 0L
                    prevSub += sum?.optLong("subrequests") ?: 0L
                }
            }

            val hourReq = linkedMapOf<String, Long>()
            val hourErr = linkedMapOf<String, Long>()
            val hourSub = linkedMapOf<String, Long>()
            val hourCpu = linkedMapOf<String, MutableList<Double>>()
            if (series.ok) {
                for (i in 0 until series.rows.length()) {
                    val row = series.rows.getJSONObject(i)
                    val sum = row.optJSONObject("sum")
                    val q = row.optJSONObject("quantiles")
                    val dim = row.optJSONObject("dimensions")
                    val reqs = sum?.optLong("requests") ?: 0L
                    val errs = sum?.optLong("errors") ?: 0L
                    val subs = sum?.optLong("subrequests") ?: 0L
                    val cpuUs = q?.optDouble("cpuTimeP50") ?: 0.0
                    if (!summary.ok) {
                        totalReq += reqs
                        totalErr += errs
                        totalSub += subs
                    }
                    val dt = dim?.optString("datetime") ?: continue
                    val hourKey = bucketKey15m(dt)
                    hourReq[hourKey] = (hourReq[hourKey] ?: 0L) + reqs
                    hourErr[hourKey] = (hourErr[hourKey] ?: 0L) + errs
                    hourSub[hourKey] = (hourSub[hourKey] ?: 0L) + subs
                    if (cpuUs > 0) {
                        hourCpu.getOrPut(hourKey) { mutableListOf() }.add(cpuUs / 1000.0)
                    }
                }
            }
            if (!summary.ok && series.ok && cpuMs == 0.0) {
                val all = hourCpu.values.flatten()
                if (all.isNotEmpty()) cpuMs = all.average()
            }

            val sortedKeys = hourReq.keys.sorted()
            val bucketSeconds = 15f * 60f
            val requestPoints = sortedKeys.map { (hourReq[it] ?: 0L).toFloat() }
            val requestRatePoints = sortedKeys.map { (hourReq[it] ?: 0L).toFloat() / bucketSeconds }
            val errorPoints = sortedKeys.map { (hourErr[it] ?: 0L).toFloat() }
            val subrequestPoints = sortedKeys.map { (hourSub[it] ?: 0L).toFloat() }
            val cpuPoints = sortedKeys.map { k ->
                val list = hourCpu[k]
                if (list.isNullOrEmpty()) 0f else list.average().toFloat()
            }

            ApiResult(
                true,
                WorkerMetrics(
                    totalRequests = totalReq,
                    totalErrors = totalErr,
                    totalSubrequests = totalSub,
                    cpuTimeMs = cpuMs,
                    requestPoints = requestPoints,
                    requestRatePoints = requestRatePoints,
                    cpuPoints = cpuPoints,
                    errorPoints = errorPoints,
                    subrequestPoints = subrequestPoints,
                    bucketKeys = sortedKeys,
                    requestsChangePct = changePct(totalReq, prevReq),
                    subrequestsChangePct = changePct(totalSub, prevSub),
                    errorsChangePct = changePct(totalErr, prevErr)
                )
            )
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun getPagesProject(
        email: String, apiKey: String, accountId: String, projectName: String
    ): ApiResult<ScriptInfo> = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(
                authGet(email, apiKey, "$BASE/accounts/$accountId/pages/projects/$projectName")
            ).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "HTTP ${resp.code}")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) {
                return@withContext ApiResult(false, error = "获取 Pages 项目失败")
            }
            val result = json.optJSONObject("result") ?: JSONObject()
            val subdomain = result.optString("subdomain").ifBlank {
                "$projectName.pages.dev"
            }
            ApiResult(true, ScriptInfo(subdomain, false, false, 0))
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun getScriptInfo(
        email: String, apiKey: String, accountId: String, scriptName: String
    ): ApiResult<ScriptInfo> = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName")).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "HTTP ${resp.code}")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = "获取脚本信息失败")
            val result = json.optJSONObject("result") ?: JSONObject()
            val subdomain = result.optString("id", scriptName) + ".workers.dev"
            val bindings = result.optJSONArray("bindings")
            val logs = result.optJSONObject("logpush") != null || result.optBoolean("logpush", false)
            ApiResult(true, ScriptInfo(subdomain, logs, false, bindings?.length() ?: 0))
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    fun formatCount(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
        else -> n.toString()
    }

    fun formatCpu(ms: Double): String = when {
        ms >= 1000 -> String.format(Locale.US, "%.2fs", ms / 1000.0)
        else -> String.format(Locale.US, "%.1f ms", ms)
    }
}
