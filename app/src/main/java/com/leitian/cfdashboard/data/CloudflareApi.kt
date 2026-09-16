package com.leitian.cfdashboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CloudflareApi {

    private const val BASE = "https://api.cloudflare.com/client/v4"

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

    private fun authRequest(token: String, url: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()
    }

    suspend fun verifyToken(token: String): ApiResult<Account> = withContext(Dispatchers.IO) {
        try {
            val req = authRequest(token, "$BASE/accounts?per_page=1")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                return@withContext ApiResult(false, error = "Token 无效或无权限 (${resp.code})")
            }
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) {
                val msg = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message") ?: "验证失败"
                return@withContext ApiResult(false, error = msg)
            }
            val result = json.getJSONArray("result")
            if (result.length() == 0) return@withContext ApiResult(false, error = "没有找到 Account")
            val acc = result.getJSONObject(0)
            ApiResult(true, Account(acc.getString("id"), acc.optString("name", "")))
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun getApps(token: String, accountId: String): ApiResult<List<AppItem>> =
        withContext(Dispatchers.IO) {
            try {
                val items = mutableListOf<AppItem>()

                // Workers
                val wReq = authRequest(token, "$BASE/accounts/$accountId/workers/scripts")
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

                // Pages
                val pReq = authRequest(token, "$BASE/accounts/$accountId/pages/projects")
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
}
