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
import java.util.concurrent.TimeUnit

/**
 * 详情页各 Tab 的真实接口：部署 / 域 / Access / 设置（读 + 写）
 * NOTE: 请用第二批 ZIP 中的完整版覆盖本文件
 */
object CloudflareDetailApi {

    private const val BASE = "https://api.cloudflare.com/client/v4"
    private val JSON = "application/json".toMediaType()

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
            .patch(jsonBody.toRequestBody(JSON))
            .build()
    }

    private fun authPut(email: String, apiKey: String, url: String, jsonBody: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .put(jsonBody.toRequestBody(JSON))
            .build()
    }

    private fun authPost(email: String, apiKey: String, url: String, jsonBody: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON))
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

    private fun parseCfError(body: String): String {
        return try {
            val json = JSONObject(body)
            json.optJSONArray("errors")?.optJSONObject(0)?.optString("message") ?: "请求失败"
        } catch (e: Exception) {
            "请求失败"
        }
    }

    suspend fun listDeployments(
        email: String, apiKey: String, accountId: String, scriptName: String
    ): ApiResult<List<DeploymentItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName/deployments")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "部署列表失败 (${resp.code})")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) {
                val msg = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message") ?: "失败"
                return@withContext ApiResult(false, error = msg)
            }
            val arr = json.optJSONObject("result")?.optJSONArray("deployments") ?: org.json.JSONArray()
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
        email: String, apiKey: String, accountId: String, scriptName: String
    ): ApiResult<List<DomainItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/workers/domains?service=$scriptName")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                if (resp.code == 403 || resp.code == 404) return@withContext ApiResult(true, emptyList())
                return@withContext ApiResult(false, error = "域名列表失败 (${resp.code})")
            }
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(true, emptyList())
            val arr = json.optJSONArray("result") ?: org.json.JSONArray()
            val list = mutableListOf<DomainItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(DomainItem(o.optString("id"), o.optString("hostname"), o.optString("service"), o.optString("environment", "production")))
            }
            if (list.none { it.hostname.endsWith(".workers.dev") }) {
                list.add(0, DomainItem("workers-dev", "$scriptName.workers.dev", scriptName, "production"))
            }
            ApiResult(true, list)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun listAccessApps(
        email: String, apiKey: String, accountId: String
    ): ApiResult<List<AccessAppItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/access/apps")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                if (resp.code == 403 || resp.code == 404) return@withContext ApiResult(true, emptyList())
                return@withContext ApiResult(false, error = "Access 列表失败 (${resp.code})")
            }
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(true, emptyList())
            val arr = json.optJSONArray("result") ?: org.json.JSONArray()
            val list = mutableListOf<AccessAppItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val domain = o.optString("domain").ifBlank {
                    o.optJSONArray("self_hosted_domains")?.optString(0) ?: "—"
                }
                list.add(AccessAppItem(o.optString("id"), o.optString("name"), domain, o.optString("type", "self_hosted")))
            }
            ApiResult(true, list)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun getSettingsDetail(
        email: String, apiKey: String, accountId: String, scriptName: String
    ): ApiResult<WorkerSettingsDetail> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName/settings")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "设置读取失败 (${resp.code})")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) {
                val msg = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message") ?: "失败"
                return@withContext ApiResult(false, error = msg)
            }
            val result = json.optJSONObject("result") ?: JSONObject()
            val bindingsArr = result.optJSONArray("bindings") ?: org.json.JSONArray()
            val bindings = mutableListOf<BindingItem>()
            for (i in 0 until bindingsArr.length()) {
                val b = bindingsArr.getJSONObject(i)
                val type = b.optString("type")
                val detail = when (type) {
                    "plain_text" -> b.optString("text", "")
                    "secret_text" -> "••••••"
                    "kv_namespace" -> b.optString("namespace_id")
                    "r2_bucket" -> b.optString("bucket_name")
                    "d1" -> b.optString("id")
                    "service" -> b.optString("service")
                    else -> type
                }
                bindings.add(BindingItem(b.optString("name"), type, detail, b.toString()))
            }
            val tagsArr = result.optJSONArray("tags") ?: org.json.JSONArray()
            val tags = mutableListOf<String>()
            for (i in 0 until tagsArr.length()) tags.add(tagsArr.getString(i))
            val flagsArr = result.optJSONArray("compatibility_flags") ?: org.json.JSONArray()
            val flags = mutableListOf<String>()
            for (i in 0 until flagsArr.length()) flags.add(flagsArr.getString(i))
            val placement = result.optJSONObject("placement")?.optString("mode") ?: "默认"
            val observability = result.optJSONObject("observability")
            val obsEnabled = observability?.optBoolean("enabled", false) ?: result.optBoolean("logpush", false)
            val sampleRate = observability?.optDouble("head_sampling_rate", 1.0) ?: 1.0
            val logpush = result.optBoolean("logpush", false)
            ApiResult(true, WorkerSettingsDetail(
                compatibilityDate = result.optString("compatibility_date", "—"),
                compatibilityFlags = flags,
                usageModel = result.optString("usage_model", "standard"),
                bindings = bindings,
                tags = tags,
                logpush = logpush,
                observabilityEnabled = obsEnabled,
                headSamplingRate = sampleRate,
                placementMode = placement
            ))
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    private suspend fun patchSettings(
        email: String, apiKey: String, accountId: String, scriptName: String, body: JSONObject
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = authPatch(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName/settings", body.toString())
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "保存失败 (${resp.code})：${parseCfError(respBody)}")
            val json = JSONObject(respBody)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = parseCfError(respBody))
            ApiResult(true, Unit)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    private fun bindingsToJsonArray(bindings: List<BindingItem>): JSONArray {
        val arr = JSONArray()
        bindings.forEach { arr.put(JSONObject(it.rawJson)) }
        return arr
    }

    suspend fun upsertVariable(
        email: String, apiKey: String, accountId: String, scriptName: String,
        currentBindings: List<BindingItem>, originalName: String?, newName: String, newValue: String, isSecret: Boolean
    ): ApiResult<Unit> {
        val kept = currentBindings.filter { it.name != (originalName ?: newName) }
        val newBindingJson = JSONObject().apply {
            put("type", if (isSecret) "secret_text" else "plain_text")
            put("name", newName)
            put("text", newValue)
        }
        val mergedArr = JSONArray()
        kept.forEach { mergedArr.put(JSONObject(it.rawJson)) }
        mergedArr.put(newBindingJson)
        return patchSettings(email, apiKey, accountId, scriptName, JSONObject().put("bindings", mergedArr))
    }

    suspend fun deleteBinding(
        email: String, apiKey: String, accountId: String, scriptName: String,
        currentBindings: List<BindingItem>, name: String
    ): ApiResult<Unit> {
        val kept = currentBindings.filter { it.name != name }
        return patchSettings(email, apiKey, accountId, scriptName, JSONObject().put("bindings", bindingsToJsonArray(kept)))
    }

    suspend fun updateObservability(
        email: String, apiKey: String, accountId: String, scriptName: String, enabled: Boolean, samplingPercent: Double
    ): ApiResult<Unit> {
        val body = JSONObject().put("observability", JSONObject().apply {
            put("enabled", enabled)
            put("head_sampling_rate", (samplingPercent / 100.0).coerceIn(0.0, 1.0))
        })
        return patchSettings(email, apiKey, accountId, scriptName, body)
    }

    suspend fun updateRuntimeSettings(
        email: String, apiKey: String, accountId: String, scriptName: String,
        compatibilityDate: String, compatibilityFlags: List<String>, placementMode: String
    ): ApiResult<Unit> {
        val body = JSONObject().apply {
            if (compatibilityDate.isNotBlank()) put("compatibility_date", compatibilityDate)
            put("compatibility_flags", JSONArray(compatibilityFlags))
            put("placement", if (placementMode.isBlank() || placementMode == "off") JSONObject().put("mode", "off") else JSONObject().put("mode", placementMode))
        }
        return patchSettings(email, apiKey, accountId, scriptName, body)
    }

    suspend fun deleteWorker(
        email: String, apiKey: String, accountId: String, scriptName: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = authDelete(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "删除失败 (${resp.code})：${parseCfError(body)}")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = parseCfError(body))
            ApiResult(true, Unit)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun listSchedules(
        email: String, apiKey: String, accountId: String, scriptName: String
    ): ApiResult<List<String>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName/schedules")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(true, emptyList())
            val json = JSONObject(body)
            val arr = json.optJSONObject("result")?.optJSONArray("schedules") ?: JSONArray()
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) list.add(arr.getJSONObject(i).optString("cron"))
            ApiResult(true, list)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun updateSchedules(
        email: String, apiKey: String, accountId: String, scriptName: String, crons: List<String>
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            crons.forEach { arr.put(JSONObject().put("cron", it)) }
            val req = authPut(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName/schedules", arr.toString())
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "保存失败 (${resp.code})：${parseCfError(body)}")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = parseCfError(body))
            ApiResult(true, Unit)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }
}
