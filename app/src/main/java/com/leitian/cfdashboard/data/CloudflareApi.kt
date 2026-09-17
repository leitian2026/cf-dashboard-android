package com.leitian.cfdashboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
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
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
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
        val updatedAt: String,
        val isPages: Boolean
    )

    data class AccountStats(
        val requests: Long,
        val errors: Long,
        val cpuTimeMs: Double
    )

    data class WorkerMetrics(
        val totalRequests: Long,
        val totalErrors: Long,
        val cpuTimeMs: Double,
        val requestPoints: List<Float>,
        /** 每桶请求数 / 桶时长(秒)，用于速率曲线 */
        val requestRatePoints: List<Float>,
        val cpuPoints: List<Float>,
        val errorPoints: List<Float>
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

    /**
     * Cloudflare Free 套餐每日限额在 UTC 00:00 重置（北京时间 08:00）。
     * 返回「UTC 当天 0 点 → 现在」的时间范围，与官方每日额度口径一致。
     */
    private fun utcTodayRange(): Pair<String, String> {
        val utc = TimeZone.getTimeZone("UTC")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = utc
        }
        val end = Date()
        val cal = java.util.Calendar.getInstance(utc).apply {
            time = end
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val start = cal.time
        return sdf.format(start) to sdf.format(end)
    }

    /** 滚动过去 24 小时（now-24h → now），用于 Worker 指标图表。 */
    private fun last24HoursRange(): Pair<String, String> {
        val utc = TimeZone.getTimeZone("UTC")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = utc
        }
        val end = Date()
        val start = Date(end.time - 24L * 60 * 60 * 1000)
        return sdf.format(start) to sdf.format(end)
    }

    /** GraphQL datetime → 15 分钟桶 key，例如 2026-09-17T14:37:xx → 2026-09-17T14:30 */
    private fun bucketKey15m(dt: String): String {
        if (dt.length < 16) return if (dt.length >= 13) dt.substring(0, 13) else dt
        val hourPart = dt.substring(0, 13) // yyyy-MM-dd'T'HH
        val minute = dt.substring(14, 16).toIntOrNull() ?: 0
        val m = (minute / 15) * 15
        return "$hourPart:${m.toString().padStart(2, '0')}"
    }

    private fun truncate(s: String, max: Int = 300): String {
        return if (s.length <= max) s else s.take(max) + "..."
    }

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
                if (!resp.isSuccessful) {
                    return@withContext ApiResult(false, error = "验证失败 (${resp.code}): ${truncate(body)}")
                }
                val json = JSONObject(body)
                if (!json.optBoolean("success", false)) {
                    val err = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                    return@withContext ApiResult(false, error = err ?: "登录失败")
                }
                val arr = json.optJSONArray("result") ?: JSONArray()
                if (arr.length() == 0) {
                    return@withContext ApiResult(false, error = "未找到账号")
                }
                val o = arr.getJSONObject(0)
                ApiResult(true, Account(o.optString("id"), o.optString("name")))
            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "网络错误")
            }
        }

    suspend fun getApps(email: String, apiKey: String, accountId: String): ApiResult<List<AppItem>> =
        withContext(Dispatchers.IO) {
            try {
                val workersReq = authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts")
                val workersResp = client.newCall(workersReq).execute()
                val workersBody = workersResp.body?.string() ?: ""
                val list = mutableListOf<AppItem>()
                if (workersResp.isSuccessful) {
                    val json = JSONObject(workersBody)
                    val arr = json.optJSONArray("result") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.optString("id")
                        val modified = o.optString("modified_on").take(19).replace("T", " ")
                        list.add(
                            AppItem(
                                id = id,
                                name = id,
                                subtitle = "Worker",
                                updatedAt = modified.ifBlank { "—" },
                                isPages = false
                            )
                        )
                    }
                }
                val pagesReq = authGet(email, apiKey, "$BASE/accounts/$accountId/pages/projects")
                val pagesResp = client.newCall(pagesReq).execute()
                val pagesBody = pagesResp.body?.string() ?: ""
                if (pagesResp.isSuccessful) {
                    val json = JSONObject(pagesBody)
                    val arr = json.optJSONArray("result") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val name = o.optString("name")
                        val id = o.optString("id", name)
                        val modified = o.optString("created_on").take(19).replace("T", " ")
                        list.add(
                            AppItem(
                                id = id,
                                name = name,
                                subtitle = "Pages",
                                updatedAt = modified.ifBlank { "—" },
                                isPages = true
                            )
                        )
                    }
                }
                ApiResult(true, list)
            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "网络错误")
            }
        }

    suspend fun createWorker(
        email: String,
        apiKey: String,
        accountId: String,
        name: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val scriptName = name.trim()
            if (!scriptName.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                return@withContext ApiResult(false, error = "名称只能包含字母、数字、下划线和短横线")
            }
            val defaultScript = "export default {\n  async fetch(request, env, ctx) {\n    return new Response('Hello from $scriptName!');\n  }\n}\n"
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "worker.js",
                    "worker.js",
                    defaultScript.toRequestBody("application/javascript".toMediaType())
                )
                .addFormDataPart("metadata", "{\"main_module\":\"worker.js\",\"compatibility_date\":\"2024-01-01\"}")
                .build()
            val req = Request.Builder()
                .url("$BASE/accounts/$accountId/workers/scripts/$scriptName")
                .addHeader("X-Auth-Email", email)
                .addHeader("X-Auth-Key", apiKey)
                .put(body)
                .build()
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val err = try {
                    JSONObject(respBody).optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                } catch (_: Exception) { null }
                return@withContext ApiResult(false, error = err ?: "创建失败 (${resp.code})")
            }
            ApiResult(true, Unit)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun uploadWorkerScript(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        fileName: String,
        bytes: ByteArray
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val moduleName = when {
                fileName.endsWith(".mjs", ignoreCase = true) -> fileName.substringAfterLast('/')
                fileName.endsWith(".js", ignoreCase = true) -> fileName.substringAfterLast('/')
                else -> "worker.js"
            }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    moduleName,
                    moduleName,
                    bytes.toRequestBody("application/javascript".toMediaType())
                )
                .addFormDataPart("metadata", "{\"main_module\":\"$moduleName\",\"compatibility_date\":\"2024-01-01\"}")
                .build()
            val req = Request.Builder()
                .url("$BASE/accounts/$accountId/workers/scripts/$scriptName")
                .addHeader("X-Auth-Email", email)
                .addHeader("X-Auth-Key", apiKey)
                .put(body)
                .build()
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val err = try {
                    JSONObject(respBody).optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                } catch (_: Exception) { null }
                return@withContext ApiResult(false, error = err ?: "上传失败 (${resp.code})")
            }
            ApiResult(true, Unit)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun getAccountStats(
        email: String,
        apiKey: String,
        accountId: String
    ): ApiResult<AccountStats> = withContext(Dispatchers.IO) {
        try {
            val (start, end) = utcTodayRange()
            val query = """
                query {
                  viewer {
                    accounts(filter: {accountTag: "$accountId"}) {
                      workersInvocationsAdaptive(
                        limit: 10000,
                        filter: {
                          datetime_geq: "$start",
                          datetime_leq: "$end"
                        }
                      ) {
                        sum {
                          requests
                          errors
                        }
                        quantiles {
                          cpuTimeP50
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
            val payload = JSONObject().put("query", query).toString()
            val req = authPost(email, apiKey, GRAPHQL, payload)
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                return@withContext ApiResult(false, error = "统计查询失败 (${resp.code})")
            }
            val json = JSONObject(body)
            graphQlHasRealErrors(json)?.let {
                return@withContext ApiResult(false, error = it)
            }
            val rows = json.optJSONObject("data")
                ?.optJSONObject("viewer")
                ?.optJSONArray("accounts")
                ?.optJSONObject(0)
                ?.optJSONArray("workersInvocationsAdaptive")
                ?: JSONArray()
            var reqs = 0L
            var errs = 0L
            var cpuSum = 0.0
            var cpuCnt = 0
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val sum = row.optJSONObject("sum")
                val q = row.optJSONObject("quantiles")
                reqs += sum?.optLong("requests") ?: 0L
                errs += sum?.optLong("errors") ?: 0L
                val cpuUs = q?.optDouble("cpuTimeP50") ?: 0.0
                if (cpuUs > 0) {
                    cpuSum += cpuUs / 1000.0
                    cpuCnt++
                }
            }
            val avgCpu = if (cpuCnt > 0) cpuSum / cpuCnt else 0.0
            ApiResult(true, AccountStats(reqs, errs, avgCpu))
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun getWorkerMetrics(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String
    ): ApiResult<WorkerMetrics> = withContext(Dispatchers.IO) {
        try {
            val (start, end) = last24HoursRange()

            val query = """
                query {
                  viewer {
                    accounts(filter: {accountTag: "$accountId"}) {
                      workersInvocationsAdaptive(
                        limit: 10000,
                        filter: {
                          scriptName: "$scriptName",
                          datetime_geq: "$start",
                          datetime_leq: "$end"
                        }
                      ) {
                        sum {
                          requests
                          errors
                        }
                        quantiles {
                          cpuTimeP50
                        }
                        dimensions {
                          datetime
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
            val payload = JSONObject().put("query", query).toString()
            val req = authPost(email, apiKey, GRAPHQL, payload)
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                return@withContext ApiResult(false, error = "指标查询失败 (${resp.code})")
            }
            val json = JSONObject(body)
            graphQlHasRealErrors(json)?.let {
                return@withContext ApiResult(false, error = it)
            }

            val rows = json.optJSONObject("data")
                ?.optJSONObject("viewer")
                ?.optJSONArray("accounts")
                ?.optJSONObject(0)
                ?.optJSONArray("workersInvocationsAdaptive")
                ?: JSONArray()

            val hourReq = linkedMapOf<String, Long>()
            val hourErr = linkedMapOf<String, Long>()
            val hourCpu = linkedMapOf<String, MutableList<Double>>()

            var totalReq = 0L
            var totalErr = 0L
            var cpuSum = 0.0
            var cpuCnt = 0

            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val sum = row.optJSONObject("sum")
                val q = row.optJSONObject("quantiles")
                val dim = row.optJSONObject("dimensions")
                val reqs = sum?.optLong("requests") ?: 0L
                val errs = sum?.optLong("errors") ?: 0L
                val cpuUs = q?.optDouble("cpuTimeP50") ?: 0.0
                totalReq += reqs
                totalErr += errs
                if (cpuUs > 0) {
                    cpuSum += cpuUs / 1000.0
                    cpuCnt++
                }
                val dt = dim?.optString("datetime") ?: continue
                val hourKey = bucketKey15m(dt)
                hourReq[hourKey] = (hourReq[hourKey] ?: 0L) + reqs
                hourErr[hourKey] = (hourErr[hourKey] ?: 0L) + errs
                if (cpuUs > 0) {
                    hourCpu.getOrPut(hourKey) { mutableListOf() }.add(cpuUs / 1000.0)
                }
            }

            val sortedKeys = hourReq.keys.sorted()
            // 15 分钟桶 → 速率 = 请求数 / 900 秒
            val bucketSeconds = 15f * 60f
            val requestPoints = sortedKeys.map { (hourReq[it] ?: 0L).toFloat() }
            val requestRatePoints = sortedKeys.map { (hourReq[it] ?: 0L).toFloat() / bucketSeconds }
            val errorPoints = sortedKeys.map { (hourErr[it] ?: 0L).toFloat() }
            val cpuPoints = sortedKeys.map { k ->
                val list = hourCpu[k]
                if (list.isNullOrEmpty()) 0f else (list.average()).toFloat()
            }

            val avgCpu = if (cpuCnt > 0) cpuSum / cpuCnt else 0.0
            ApiResult(
                true,
                WorkerMetrics(
                    totalRequests = totalReq,
                    totalErrors = totalErr,
                    cpuTimeMs = avgCpu,
                    requestPoints = requestPoints,
                    requestRatePoints = requestRatePoints,
                    cpuPoints = cpuPoints,
                    errorPoints = errorPoints
                )
            )
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun getScriptInfo(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String
    ): ApiResult<ScriptInfo> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                return@withContext ApiResult(false, error = "脚本信息失败 (${resp.code})")
            }
            val json = JSONObject(body)
            val result = json.optJSONObject("result") ?: JSONObject()
            val subdomain = result.optString("id", scriptName).let { "$it.workers.dev" }
            val bindings = result.optJSONObject("script")?.optJSONArray("bindings")
                ?: result.optJSONArray("bindings")
                ?: JSONArray()
            ApiResult(
                true,
                ScriptInfo(
                    subdomain = subdomain,
                    logsEnabled = false,
                    tracesEnabled = false,
                    bindingCount = bindings.length()
                )
            )
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    fun formatCount(n: Long): String {
        return when {
            n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
            n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
            else -> n.toString()
        }
    }

    fun formatCpu(ms: Double): String {
        return when {
            ms >= 1000 -> String.format(Locale.US, "%.2fs", ms / 1000.0)
            else -> String.format(Locale.US, "%.1f ms", ms)
        }
    }
}
