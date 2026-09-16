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
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
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

    data class WorkerMetrics(
        val totalRequests: Long,
        val totalErrors: Long,
        val cpuTimeMs: Double,
        val requestPoints: List<Float>,
        val cpuPoints: List<Float>,
        val errorPoints: List<Float>
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

    suspend fun verifyGlobalKey(email: String, apiKey: String): ApiResult<Account> =
        withContext(Dispatchers.IO) {
            try {
                val req = authGet(email, apiKey, "$BASE/accounts?per_page=1")
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return@withContext ApiResult(
                        false,
                        error = when (resp.code) {
                            400, 401, 403 -> "邮箱或 Global API Key 错误 (${resp.code})"
                            else -> "请求失败 (${resp.code})"
                        }
                    )
                }
                val json = JSONObject(body)
                if (!json.optBoolean("success", false)) {
                    val msg = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message") ?: "验证失败"
                    return@withContext ApiResult(false, error = msg)
                }
                val result = json.getJSONArray("result")
                if (result.length() == 0) {
                    return@withContext ApiResult(false, error = "没有找到 Account")
                }
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

    /**
     * 用 GraphQL Analytics 拉取某个 Worker 最近 24 小时的真实指标
     */
    suspend fun getWorkerMetrics(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String
    ): ApiResult<WorkerMetrics> = withContext(Dispatchers.IO) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val end = Date()
            val start = Date(end.time - 24 * 60 * 60 * 1000L)
            val startStr = sdf.format(start)
            val endStr = sdf.format(end)

            // GraphQL 查询 Workers 调用自适应数据（按小时）
            val query = """
                query {
                  viewer {
                    accounts(filter: {accountTag: "$accountId"}) {
                      workersInvocationsAdaptive(
                        limit: 10000,
                        filter: {
                          datetime_geq: "$startStr",
                          datetime_leq: "$endStr",
                          scriptName: "$scriptName"
                        },
                        orderBy: [datetimeHour_ASC]
                      ) {
                        sum {
                          requests
                          errors
                          subrequests
                        }
                        quantiles {
                          cpuTimeP50
                        }
                        dimensions {
                          datetimeHour
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val bodyJson = JSONObject().put("query", query).toString()
            val req = authPost(email, apiKey, GRAPHQL, bodyJson)
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""

            if (!resp.isSuccessful) {
                return@withContext ApiResult(false, error = "获取指标失败 (${resp.code})")
            }

            val json = JSONObject(body)
            if (json.has("errors")) {
                val errMsg = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                    ?: "GraphQL 错误"
                // 某些账号没有 analytics 权限时会失败，返回空数据而不是硬失败
                return@withContext ApiResult(
                    true,
                    WorkerMetrics(0, 0, 0.0, emptyList(), emptyList(), emptyList())
                )
            }

            val accounts = json.optJSONObject("data")
                ?.optJSONObject("viewer")
                ?.optJSONArray("accounts")

            if (accounts == null || accounts.length() == 0) {
                return@withContext ApiResult(
                    true,
                    WorkerMetrics(0, 0, 0.0, emptyList(), emptyList(), emptyList())
                )
            }

            val adaptive = accounts.getJSONObject(0).optJSONArray("workersInvocationsAdaptive")
                ?: JSONArray()

            var totalRequests = 0L
            var totalErrors = 0L
            var totalCpu = 0.0
            val requestPoints = mutableListOf<Float>()
            val cpuPoints = mutableListOf<Float>()
            val errorPoints = mutableListOf<Float>()

            for (i in 0 until adaptive.length()) {
                val row = adaptive.getJSONObject(i)
                val sum = row.optJSONObject("sum")
                val quantiles = row.optJSONObject("quantiles")

                val reqs = sum?.optLong("requests") ?: 0L
                val errs = sum?.optLong("errors") ?: 0L
                // cpuTimeP50 单位是微秒，转成毫秒
                val cpuUs = quantiles?.optDouble("cpuTimeP50") ?: 0.0
                val cpuMs = cpuUs / 1000.0

                totalRequests += reqs
                totalErrors += errs
                totalCpu += cpuMs

                requestPoints.add(reqs.toFloat())
                cpuPoints.add(cpuMs.toFloat())
                errorPoints.add(errs.toFloat())
            }

            val avgCpu = if (adaptive.length() > 0) totalCpu / adaptive.length() else 0.0

            ApiResult(
                true,
                WorkerMetrics(
                    totalRequests = totalRequests,
                    totalErrors = totalErrors,
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

    fun formatCount(n: Long): String {
        return when {
            n >= 1_000_000 -> String.format(Locale.US, "%.2fM", n / 1_000_000.0)
            n >= 1_000 -> String.format(Locale.US, "%.2fk", n / 1_000.0)
            else -> n.toString()
        }
    }

    fun formatCpu(ms: Double): String {
        return if (ms < 1) String.format(Locale.US, "%.2f ms", ms)
        else String.format(Locale.US, "%.0f ms", ms)
    }
}
