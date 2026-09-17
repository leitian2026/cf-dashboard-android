package com.leitian.cfdashboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 详情页接口：部署 / 域 / Access / 设置（读 + 写）
 */
object CloudflareDetailApi {

    private const val BASE = "https://api.cloudflare.com/client/v4"

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

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private fun authGet(email: String, apiKey: String, url: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .get()
            .build()
    }

    private fun authPatch(email: String, apiKey: String, url: String, jsonBody: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .patch(jsonBody.toRequestBody(JSON_MEDIA))
            .build()
    }

    private fun authPut(email: String, apiKey: String, url: String, jsonBody: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .put(jsonBody.toRequestBody(JSON_MEDIA))
            .build()
    }

    private fun authPost(email: String, apiKey: String, url: String, jsonBody: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .build()
    }

    private fun authDelete(email: String, apiKey: String, url: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .delete()
            .build()
    }

    private fun parseCfError(json: JSONObject, fallback: String = "请求失败"): String {
        val arr = json.optJSONArray("errors")
        if (arr != null && arr.length() > 0) {
            val msg = arr.optJSONObject(0)?.optString("message").orEmpty()
            if (msg.isNotBlank()) return msg
        }
        val messages = json.optJSONArray("messages")
        if (messages != null && messages.length() > 0) {
            val msg = messages.optJSONObject(0)?.optString("message").orEmpty()
            if (msg.isNotBlank()) return msg
        }
        return fallback
    }

    private fun executeWrite(req: Request, httpFailPrefix: String): ApiResult<Unit> {
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: ""
        if (body.isBlank() && resp.isSuccessful) {
            return ApiResult(true, Unit)
        }
        val json = try {
            if (body.isBlank()) JSONObject() else JSONObject(body)
        } catch (_: Exception) {
            return if (resp.isSuccessful) ApiResult(true, Unit)
            else ApiResult(false, error = "$httpFailPrefix (${resp.code})")
        }
        if (!resp.isSuccessful) {
            return ApiResult(false, error = parseCfError(json, "$httpFailPrefix (${resp.code})"))
        }
        if (!json.optBoolean("success", true) && json.has("success")) {
            return ApiResult(false, error = parseCfError(json, httpFailPrefix))
        }
        return ApiResult(true, Unit)
    }

    suspend fun listDeployments(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String
    ): ApiResult<List<DeploymentItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(
                email, apiKey,
                "$BASE/accounts/$accountId/workers/scripts/$scriptName/deployments"
            )
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                return@withContext ApiResult(false, error = "部署列表失败 (${resp.code})")
            }
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) {
                return@withContext ApiResult(false, error = parseCfError(json))
            }
            val arr = json.optJSONObject("result")?.optJSONArray("deployments")
                ?: org.json.JSONArray()
            val list = mutableListOf<DeploymentItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val versions = o.optJSONArray("versions")
                val versionId = versions?.optJSONObject(0)?.optString("version_id") ?: "—"
                val annotations = o.optJSONObject("annotations")
                list.add(
                    DeploymentItem(
                        id = o.optString("id"),
                        createdOn = o.optString("created_on").take(19).replace("T", " "),
                        source = o.optString("source", "—"),
                        authorEmail = o.optString("author_email", "—"),
                        message = annotations?.optString("workers/message") ?: "",
                        versionId = versionId.take(8),
                        isLatest = i == 0
                    )
                )
            }
            ApiResult(true, list)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun listDomains(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String
    ): ApiResult<List<DomainItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(
                email, apiKey,
                "$BASE/accounts/$accountId/workers/domains?service=$scriptName"
            )
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                if (resp.code == 403 || resp.code == 404) {
                    return@withContext ApiResult(true, emptyList())
                }
                return@withContext ApiResult(false, error = "域名列表失败 (${resp.code})")
            }
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) {
                return@withContext ApiResult(true, emptyList())
            }
            val arr = json.optJSONArray("result") ?: org.json.JSONArray()
            val list = mutableListOf<DomainItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    DomainItem(
                        id = o.optString("id"),
                        hostname = o.optString("hostname"),
                        service = o.optString("service"),
                        environment = o.optString("environment", "production")
                    )
                )
            }
            if (list.none { it.hostname.endsWith(".workers.dev") }) {
                list.add(
                    0,
                    DomainItem(
                        id = "workers-dev",
                        hostname = "$scriptName.workers.dev",
                        service = scriptName,
                        environment = "production"
                    )
                )
            }
            ApiResult(true, list)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun listAccessApps(
        email: String,
        apiKey: String,
        accountId: String
    ): ApiResult<List<AccessAppItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(
                email, apiKey,
                "$BASE/accounts/$accountId/access/apps"
            )
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                if (resp.code == 403 || resp.code == 404) {
                    return@withContext ApiResult(true, emptyList())
                }
                return@withContext ApiResult(false, error = "Access 列表失败 (${resp.code})")
            }
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) {
                return@withContext ApiResult(true, emptyList())
            }
            val arr = json.optJSONArray("result") ?: org.json.JSONArray()
            val list = mutableListOf<AccessAppItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val domain = o.optString("domain").ifBlank {
                    o.optJSONArray("self_hosted_domains")?.optString(0) ?: "—"
                }
                list.add(
                    AccessAppItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        domain = domain,
                        type = o.optString("type", "self_hosted")
                    )
                )
            }
            ApiResult(true, list)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun getSettingsDetail(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String
    ): ApiResult<WorkerSettingsDetail> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(
                email, apiKey,
                "$BASE/accounts/$accountId/workers/scripts/$scriptName/settings"
            )
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                return@withContext ApiResult(false, error = "设置读取失败 (${resp.code})")
            }
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) {
                return@withContext ApiResult(false, error = parseCfError(json))
            }
            val result = json.optJSONObject("result") ?: JSONObject()

            val bindingsArr = result.optJSONArray("bindings") ?: org.json.JSONArray()
            val bindings = mutableListOf<BindingItem>()
            for (i in 0 until bindingsArr.length()) {
                val b = bindingsArr.getJSONObject(i)
                val type = b.optString("type")
                val detail = when (type) {
                    "plain_text", "secret_text" -> b.optString("text", "***")
                    "kv_namespace" -> b.optString("namespace_id")
                    "r2_bucket" -> b.optString("bucket_name")
                    "d1" -> b.optString("id")
                    "service" -> b.optString("service")
                    else -> type
                }
                bindings.add(
                    BindingItem(
                        name = b.optString("name"),
                        type = type,
                        detail = detail
                    )
                )
            }

            val tagsArr = result.optJSONArray("tags") ?: org.json.JSONArray()
            val tags = mutableListOf<String>()
            for (i in 0 until tagsArr.length()) {
                tags.add(tagsArr.getString(i))
            }

            val placement = result.optJSONObject("placement")?.optString("mode") ?: "—"
            val logpush = result.optBoolean("logpush", false)

            ApiResult(
                true,
                WorkerSettingsDetail(
                    compatibilityDate = result.optString("compatibility_date", "—"),
                    usageModel = result.optString("usage_model", "standard"),
                    bindings = bindings,
                    tags = tags,
                    logpush = logpush,
                    placementMode = placement
                )
            )
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun patchScriptSettings(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        settingsJsonBody: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = authPatch(
                email, apiKey,
                "$BASE/accounts/$accountId/workers/scripts/$scriptName/settings",
                settingsJsonBody
            )
            executeWrite(req, "更新设置失败")
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun deleteWorker(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = authDelete(
                email, apiKey,
                "$BASE/accounts/$accountId/workers/scripts/$scriptName"
            )
            executeWrite(req, "删除 Worker 失败")
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun putSchedules(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        schedulesJsonArray: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = authPut(
                email, apiKey,
                "$BASE/accounts/$accountId/workers/scripts/$scriptName/schedules",
                schedulesJsonArray
            )
            executeWrite(req, "更新触发器失败")
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }
}
