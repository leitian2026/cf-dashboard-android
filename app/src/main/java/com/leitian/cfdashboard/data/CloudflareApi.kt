package com.leitian.cfdashboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        val domain: String,
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

    suspend fun getApps(email: String, apiKey: String, accountId: String): ApiResult<List<AppItem>> =
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
                                isPages = false
                            )
                        )
                    }
                }
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
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "worker.js",
                        "worker.js",
                        defaultScript.toRequestBody("application/javascript+module".toMediaType())
                    )
                    .addFormDataPart(
                        "metadata",
                        "metadata.json",
                        metadataJson.toRequestBody("application/json".toMediaType())
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
            val moduleName = when {
                fileName.endsWith(".mjs", ignoreCase = true) -> fileName.substringAfterLast('/')
                fileName.endsWith(".js", ignoreCase = true) -> fileName.substringAfterLast('/')
                else -> "worker.js"
            }
            val metadataJson = """{"main_module":"$moduleName","compatibility_date":"2024-01-01"}"""
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart(
                    moduleName,
                    moduleName,
                    bytes.toRequestBody("application/javascript+module".toMediaType())
                )
                .addFormDataPart(
                    "metadata",
                    "metadata.json",
                    metadataJson.toRequestBody("application/json".toMediaType())
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
            val filter = """scriptName: "$scriptName", datetime_geq: "$start", datetime_leq: "$end""""

            val summaryQuery = """
                query {
                  viewer {
                    accounts(filter: {accountTag: "$accountId"}) {
                      workersInvocationsAdaptive(
                        limit: 1,
                        filter: { $filter }
                      ) {
                        sum { requests errors }
                        quantiles { cpuTimeP50 }
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
                        sum { requests errors }
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

            val (summary, series) = coroutineScope {
                val s = async { runQuery(summaryQuery) }
                val t = async { runQuery(seriesQuery) }
                s.await() to t.await()
            }

            if (!summary.ok && !series.ok) {
                return@withContext ApiResult(false, error = summary.error ?: series.error ?: "指标查询失败")
            }

            var totalReq = 0L
            var totalErr = 0L
            var cpuMs = 0.0
            if (summary.ok && summary.rows.length() > 0) {
                for (i in 0 until summary.rows.length()) {
                    val row = summary.rows.getJSONObject(i)
                    val sum = row.optJSONObject("sum")
                    val q = row.optJSONObject("quantiles")
                    totalReq += sum?.optLong("requests") ?: 0L
                    totalErr += sum?.optLong("errors") ?: 0L
                    val cpuUs = q?.optDouble("cpuTimeP50") ?: 0.0
                    if (cpuUs > 0) {
                        cpuMs = cpuUs / 1000.0
                    }
                }
            }

            val hourReq = linkedMapOf<String, Long>()
            val hourErr = linkedMapOf<String, Long>()
            val hourCpu = linkedMapOf<String, MutableList<Double>>()
            if (series.ok) {
                for (i in 0 until series.rows.length()) {
                    val row = series.rows.getJSONObject(i)
                    val sum = row.optJSONObject("sum")
                    val q = row.optJSONObject("quantiles")
                    val dim = row.optJSONObject("dimensions")
                    val reqs = sum?.optLong("requests") ?: 0L
                    val errs = sum?.optLong("errors") ?: 0L
                    val cpuUs = q?.optDouble("cpuTimeP50") ?: 0.0
                    if (!summary.ok) {
                        totalReq += reqs
                        totalErr += errs
                    }
                    val dt = dim?.optString("datetime") ?: continue
                    val hourKey = bucketKey15m(dt)
                    hourReq[hourKey] = (hourReq[hourKey] ?: 0L) + reqs
                    hourErr[hourKey] = (hourErr[hourKey] ?: 0L) + errs
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
            val cpuPoints = sortedKeys.map { k ->
                val list = hourCpu[k]
                if (list.isNullOrEmpty()) 0f else list.average().toFloat()
            }

            ApiResult(
                true,
                WorkerMetrics(
                    totalRequests = totalReq,
                    totalErrors = totalErr,
                    cpuTimeMs = cpuMs,
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
