package com.leitian.cfdashboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
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
            // BODY 才能在 Logcat 看到 GraphQL 真实错误内容
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
        val isPages: Boolean = false
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

    private fun authPost(email: String, apiKey: String, url: String, jsonBody: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun utcNowMinusHours(hours: Int): Pair<String, String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val end = Date()
        val start = Date(end.time - hours * 60L * 60L * 1000L)
        return sdf.format(start) to sdf.format(end)
    }

    private fun truncate(s: String, max: Int = 300): String {
        return if (s.length <= max) s else s.take(max) + "..."
    }

    suspend fun verifyGlobalKey(email: String, apiKey: String): ApiResult<Account> =
        withContext(Dispatchers.IO) {
            try {
                val req = authGet(email, apiKey, "$BASE/accounts?per_page=1")
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return@withContext ApiResult(false, error = "邮箱或 Key 错误 (${resp.code})")
                }
                val json = JSONObject(body)
                if (!json.optBoolean("success", false)) {
                    val msg = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message") ?: "验证失败"
                    return@withContext ApiResult(false, error = msg)
                }
                val result = json.getJSONArray("result")
                if (result.length() == 0) return@withContext ApiResult(false, error = "没有 Account")
                val acc = result.getJSONObject(0)
                ApiResult(true, Account(acc.getString("id"), acc.optString("name", "")))
            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "网络错误")
            }
        }

    suspend fun getApps(email: String, apiKey: String, accountId: String): ApiResult<List<AppItem>> =
        withContext(Dispatchers.IO) {
            try {
                val items = mutableListOf<AppItem>()

                val wReq = authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts")
                val wResp = client.newCall(wReq).execute()
                val wBody = wResp.body?.string() ?: ""
                if (wResp.isSuccessful) {
                    val json = JSONObject(wBody)
                    if (json.optBoolean("success", false)) {
                        val arr = json.getJSONArray("result")
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val name = o.optString("id", o.optString("name"))
                            items.add(
                                AppItem(
                                    id = name,
                                    name = name,
                                    subtitle = "$name.workers.dev",
                                    updatedAt = (o.optString("modified_on", o.optString("created_on", ""))).take(10).ifBlank { "—" },
                                    isPages = false
                                )
                            )
                        }
                    }
                }

                val pReq = authGet(email, apiKey, "$BASE/accounts/$accountId/pages/projects")
                val pResp = client.newCall(pReq).execute()
                val pBody = pResp.body?.string() ?: ""
                if (pResp.isSuccessful) {
                    val json = JSONObject(pBody)
                    if (json.optBoolean("success", false)) {
                        val arr = json.getJSONArray("result")
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val name = o.getString("name")
                            val sub = o.optString("subdomain", "$name.pages.dev")
                            items.add(
                                AppItem(
                                    id = o.getString("id"),
                                    name = name,
                                    subtitle = sub,
                                    updatedAt = o.optString("created_on", "").take(10).ifBlank { "—" },
                                    isPages = true
                                )
                            )
                        }
                    }
                }

                ApiResult(true, items)
            } catch (e: Exception) {
                ApiResult(false, error = e.message ?: "网络错误")
            }
        }

    /** 账号级最近 24h 汇总（列表页顶部统计） */
    suspend fun getAccountStats(
        email: String,
        apiKey: String,
        accountId: String
    ): ApiResult<AccountStats> = withContext(Dispatchers.IO) {
        try {
            val (start, end) = utcNowMinusHours(24)
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

            // 不再静默吞错：HTTP 失败直接带错误信息返回
            if (!resp.isSuccessful) {
                return@withContext ApiResult(
                    false,
                    error = "HTTP ${resp.code}: ${truncate(body)}"
                )
            }

            val json = JSONObject(body)

            // GraphQL 业务错误也要暴露
            if (json.has("errors")) {
                val errArr = json.optJSONArray("errors")
                val msg = errArr?.optJSONObject(0)?.optString("message")
                    ?: truncate(body)
                return@withContext ApiResult(false, error = "GraphQL: $msg")
            }

            val rows = json.optJSONObject("data")
                ?.optJSONObject("viewer")
                ?.optJSONArray("accounts")
                ?.optJSONObject(0)
                ?.optJSONArray("workersInvocationsAdaptive")
                ?: JSONArray()

            var requests = 0L
            var errors = 0L
            var cpuSum = 0.0
            var cpuCount = 0

            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val sum = row.optJSONObject("sum")
                val q = row.optJSONObject("quantiles")
                requests += sum?.optLong("requests") ?: 0L
                errors += sum?.optLong("errors") ?: 0L
                val cpuUs = q?.optDouble("cpuTimeP50") ?: 0.0
                if (cpuUs > 0) {
                    cpuSum += cpuUs / 1000.0
                    cpuCount++
                }
            }

            val avgCpu = if (cpuCount > 0) cpuSum / cpuCount else 0.0
            // 真正没流量：success=true, data 全 0, error=null
            ApiResult(true, AccountStats(requests, errors, avgCpu))
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    /** 单个 Worker 最近 24h 真实指标 */
    suspend fun getWorkerMetrics(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String
    ): ApiResult<WorkerMetrics> = withContext(Dispatchers.IO) {
        try {
            val (start, end) = utcNowMinusHours(24)

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
                          subrequests
                        }
                        quantiles {
                          cpuTimeP50
                          cpuTimeP99
                        }
                        dimensions {
                          datetime
                          scriptName
                          status
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
                return@withContext ApiResult(
                    false,
                    error = "HTTP ${resp.code}: ${truncate(body)}"
                )
            }

            val json = JSONObject(body)

            if (json.has("errors")) {
                val msg = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                    ?: truncate(body)
                return@withContext ApiResult(false, error = "GraphQL: $msg")
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
                val cpuMs = cpuUs / 1000.0

                totalReq += reqs
                totalErr += errs
                if (cpuMs > 0) {
                    cpuSum += cpuMs
                    cpuCnt++
                }

                val dt = dim?.optString("datetime") ?: continue
                val hourKey = if (dt.length >= 13) dt.take(13) + ":00:00Z" else dt

                hourReq[hourKey] = (hourReq[hourKey] ?: 0L) + reqs
                hourErr[hourKey] = (hourErr[hourKey] ?: 0L) + errs
                hourCpu.getOrPut(hourKey) { mutableListOf() }.add(cpuMs)
            }

            val sortedHours = hourReq.keys.sorted()
            val requestPoints = sortedHours.map { (hourReq[it] ?: 0L).toFloat() }
            val errorPoints = sortedHours.map { (hourErr[it] ?: 0L).toFloat() }
            val cpuPoints = sortedHours.map { h ->
                val list = hourCpu[h]
                if (list.isNullOrEmpty()) 0f else list.average().toFloat()
            }

            val avgCpu = if (cpuCnt > 0) cpuSum / cpuCnt else 0.0

            ApiResult(
                true,
                WorkerMetrics(
                    totalRequests = totalReq,
                    totalErrors = totalErr,
                    cpuTimeMs = avgCpu,
                    requestPoints = requestPoints,
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
            var logsEnabled = false
            var tracesEnabled = false
            var bindingCount = 0

            val settingsReq = authGet(
                email, apiKey,
                "$BASE/accounts/$accountId/workers/scripts/$scriptName/script-settings"
            )
            val settingsResp = client.newCall(settingsReq).execute()
            val settingsBody = settingsResp.body?.string() ?: ""
            if (settingsResp.isSuccessful) {
                val json = JSONObject(settingsBody)
                if (json.optBoolean("success", false)) {
                    val result = json.optJSONObject("result")
                    val obs = result?.optJSONObject("observability")
                    logsEnabled = obs?.optBoolean("enabled", false) == true ||
                            obs?.optJSONObject("logs")?.optBoolean("enabled", false) == true
                    tracesEnabled = obs?.optJSONObject("traces")?.optBoolean("enabled", false) == true
                }
            }

            val bindReq = authGet(
                email, apiKey,
                "$BASE/accounts/$accountId/workers/scripts/$scriptName/settings"
            )
            val bindResp = client.newCall(bindReq).execute()
            val bindBody = bindResp.body?.string() ?: ""
            if (bindResp.isSuccessful) {
                val json = JSONObject(bindBody)
                if (json.optBoolean("success", false)) {
                    val bindings = json.optJSONObject("result")?.optJSONArray("bindings")
                    bindingCount = bindings?.length() ?: 0
                }
            }

            ApiResult(
                true,
                ScriptInfo(
                    subdomain = "$scriptName.workers.dev",
                    logsEnabled = logsEnabled,
                    tracesEnabled = tracesEnabled,
                    bindingCount = bindingCount
                )
            )
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "读取脚本设置失败")
        }
    }

    fun formatCount(n: Long): String {
        return when {
            n >= 1_000_000 -> String.format(Locale.US, "%.2fM", n / 1_000_000.0)
            n >= 1_000 -> String.format(Locale.US, "%.2fk", n / 1_000.0)
            else -> n.toString()
        }
    }

    fun formatCpu(ms: Double): String {
        return when {
            ms <= 0 -> "0 ms"
            ms < 1 -> String.format(Locale.US, "%.2f ms", ms)
            else -> String.format(Locale.US, "%.1f ms", ms)
        }
    }
}
