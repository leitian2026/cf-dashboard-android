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
import java.util.concurrent.TimeUnit

/**
 * 详情页各 Tab 的真实接口：部署 / 域 / Access / 设置（读 + 写）
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

    /**
     * PATCH /workers/scripts/{name}/settings 只接受 multipart/form-data，
     * 且必须包含一个名为 "settings" 的 part（值是 JSON 字符串），
     * 直接发 application/json body 会被 CF 拒绝，返回 415。
     */
    private fun authPatchSettingsMultipart(email: String, apiKey: String, url: String, jsonBody: String): Request {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "settings",
                null,
                jsonBody.toRequestBody("application/json".toMediaType())
            )
            .build()
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .patch(multipart)
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
            val arr = json.optJSONObject("result")?.optJSONArray("deployments") ?: JSONArray()
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
            val arr = json.optJSONArray("result") ?: JSONArray()
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
            val arr = json.optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<AccessAppItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val domain = o.optString("domain").ifBlank { o.optJSONArray("self_hosted_domains")?.optString(0) ?: "—" }
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
            val bindingsArr = result.optJSONArray("bindings") ?: JSONArray()
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
            val tags = mutableListOf<String>()
            val tagsArr = result.optJSONArray("tags") ?: JSONArray()
            for (i in 0 until tagsArr.length()) tags.add(tagsArr.getString(i))
            val flags = mutableListOf<String>()
            val flagsArr = result.optJSONArray("compatibility_flags") ?: JSONArray()
            for (i in 0 until flagsArr.length()) flags.add(flagsArr.getString(i))
            val placement = result.optJSONObject("placement")?.optString("mode") ?: "默认"
            val observability = result.optJSONObject("observability")
            val obsEnabled = observability?.optBoolean("enabled", false) ?: result.optBoolean("logpush", false)
            val sampleRate = observability?.optDouble("head_sampling_rate", 1.0) ?: 1.0
            ApiResult(true, WorkerSettingsDetail(
                compatibilityDate = result.optString("compatibility_date", "—"),
                compatibilityFlags = flags,
                usageModel = result.optString("usage_model", "standard"),
                bindings = bindings,
                tags = tags,
                logpush = result.optBoolean("logpush", false),
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
            val req = authPatchSettingsMultipart(
                email, apiKey,
                "$BASE/accounts/$accountId/workers/scripts/$scriptName/settings",
                body.toString()
            )
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

    private suspend fun upsertBindingRaw(
        email: String, apiKey: String, accountId: String, scriptName: String,
        currentBindings: List<BindingItem>, originalName: String?, newBindingJson: JSONObject
    ): ApiResult<Unit> {
        val targetName = originalName ?: newBindingJson.optString("name")
        val kept = currentBindings.filter { it.name != targetName }
        val arr = bindingsToJsonArray(kept)
        arr.put(newBindingJson)
        return patchSettings(email, apiKey, accountId, scriptName, JSONObject().put("bindings", arr))
    }

    suspend fun addResourceBinding(
        email: String, apiKey: String, accountId: String, scriptName: String,
        currentBindings: List<BindingItem>, bindingName: String, type: String, resourceIdOrName: String
    ): ApiResult<Unit> {
        val json = JSONObject().apply {
            put("type", type); put("name", bindingName)
            when (type) {
                "kv_namespace" -> put("namespace_id", resourceIdOrName)
                "r2_bucket" -> put("bucket_name", resourceIdOrName)
                "d1" -> put("id", resourceIdOrName)
                "service" -> { put("service", resourceIdOrName); put("environment", "production") }
            }
        }
        return upsertBindingRaw(email, apiKey, accountId, scriptName, currentBindings, null, json)
    }

    suspend fun upsertVariable(
        email: String, apiKey: String, accountId: String, scriptName: String,
        currentBindings: List<BindingItem>, originalName: String?, newName: String, newValue: String, isSecret: Boolean
    ): ApiResult<Unit> {
        val kept = currentBindings.filter { it.name != (originalName ?: newName) }
        val newBindingJson = JSONObject().apply {
            put("type", if (isSecret) "secret_text" else "plain_text")
            put("name", newName); put("text", newValue)
        }
        val mergedArr = JSONArray()
        kept.forEach { mergedArr.put(JSONObject(it.rawJson)) }
        mergedArr.put(newBindingJson)
        return patchSettings(email, apiKey, accountId, scriptName, JSONObject().put("bindings", mergedArr))
    }

    suspend fun deleteBinding(
        email: String, apiKey: String, accountId: String, scriptName: String,
        currentBindings: List<BindingItem>, bindingName: String
    ): ApiResult<Unit> {
        val kept = currentBindings.filter { it.name != bindingName }
        return patchSettings(email, apiKey, accountId, scriptName, JSONObject().put("bindings", bindingsToJsonArray(kept)))
    }

    suspend fun updateObservability(
        email: String, apiKey: String, accountId: String, scriptName: String, enabled: Boolean, headSamplingRate: Double = 1.0
    ): ApiResult<Unit> {
        val body = JSONObject().put("observability", JSONObject().put("enabled", enabled).put("head_sampling_rate", headSamplingRate))
        return patchSettings(email, apiKey, accountId, scriptName, body)
    }

    suspend fun updateRuntimeSettings(
        email: String, apiKey: String, accountId: String, scriptName: String,
        compatibilityDate: String?, placementMode: String?
    ): ApiResult<Unit> {
        val body = JSONObject()
        if (!compatibilityDate.isNullOrBlank()) body.put("compatibility_date", compatibilityDate)
        if (!placementMode.isNullOrBlank()) body.put("placement", JSONObject().put("mode", placementMode))
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
            if (!resp.isSuccessful) {
                if (resp.code == 404) return@withContext ApiResult(true, emptyList())
                return@withContext ApiResult(false, error = "Cron 列表失败 (${resp.code})")
            }
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(true, emptyList())
            val arr = json.optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i)
                val cron = o?.optString("cron") ?: arr.optString(i)
                if (cron.isNotBlank()) list.add(cron)
            }
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
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "更新 Cron 失败 (${resp.code})：${parseCfError(body)}")
            ApiResult(true, Unit)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    suspend fun listKvNamespaces(email: String, apiKey: String, accountId: String): ApiResult<List<KvNamespaceItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/storage/kv/namespaces")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(true, emptyList())
            val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<KvNamespaceItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(KvNamespaceItem(o.optString("id"), o.optString("title")))
            }
            ApiResult(true, list)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun listR2Buckets(email: String, apiKey: String, accountId: String): ApiResult<List<R2BucketItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/r2/buckets")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(true, emptyList())
            val arr = JSONObject(body).optJSONObject("result")?.optJSONArray("buckets") ?: JSONObject(body).optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<R2BucketItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(R2BucketItem(o.optString("name")))
            }
            ApiResult(true, list)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun listD1Databases(email: String, apiKey: String, accountId: String): ApiResult<List<D1DatabaseItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/d1/database")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(true, emptyList())
            val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<D1DatabaseItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(D1DatabaseItem(o.optString("uuid", o.optString("id")), o.optString("name")))
            }
            ApiResult(true, list)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun listZones(email: String, apiKey: String): ApiResult<List<ZoneItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/zones?per_page=50")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(true, emptyList())
            val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<ZoneItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(ZoneItem(o.optString("id"), o.optString("name")))
            }
            ApiResult(true, list)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun listQueues(email: String, apiKey: String, accountId: String, scriptName: String): ApiResult<List<QueueItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/queues")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(true, emptyList())
            val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<QueueItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val consumers = o.optJSONArray("consumers") ?: JSONArray()
                var consumerId: String? = null
                for (j in 0 until consumers.length()) {
                    val c = consumers.optJSONObject(j)
                    if (c?.optString("script") == scriptName || c?.optString("script_name") == scriptName) {
                        consumerId = c.optString("consumer_id", c.optString("id")).ifBlank { null }
                        break
                    }
                }
                list.add(QueueItem(o.optString("queue_id", o.optString("id")), o.optString("queue_name", o.optString("name")), consumerId))
            }
            ApiResult(true, list)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun addCustomDomain(
        email: String, apiKey: String, accountId: String, scriptName: String, hostname: String, zoneId: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("hostname", hostname).put("service", scriptName).put("environment", "production").put("zone_id", zoneId)
            val req = authPut(email, apiKey, "$BASE/accounts/$accountId/workers/domains", body.toString())
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "添加域名失败 (${resp.code})：${parseCfError(respBody)}")
            ApiResult(true, Unit)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun deleteCustomDomain(
        email: String, apiKey: String, accountId: String, domainId: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = authDelete(email, apiKey, "$BASE/accounts/$accountId/workers/domains/$domainId")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "删除域名失败 (${resp.code})：${parseCfError(body)}")
            ApiResult(true, Unit)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun addQueueConsumer(
        email: String, apiKey: String, accountId: String, queueId: String, scriptName: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("script_name", scriptName).put("type", "worker")
            val req = authPost(email, apiKey, "$BASE/accounts/$accountId/queues/$queueId/consumers", body.toString())
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "添加 Queue 消费者失败 (${resp.code})：${parseCfError(respBody)}")
            ApiResult(true, Unit)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun deleteQueueConsumer(
        email: String, apiKey: String, accountId: String, queueId: String, consumerId: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = authDelete(email, apiKey, "$BASE/accounts/$accountId/queues/$queueId/consumers/$consumerId")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "删除 Queue 消费者失败 (${resp.code})：${parseCfError(body)}")
            ApiResult(true, Unit)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun listEmailRoutingRules(
        email: String, apiKey: String, zoneId: String, scriptName: String
    ): ApiResult<List<EmailRoutingRuleItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/zones/$zoneId/email/routing/rules")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(true, emptyList())
            val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<EmailRoutingRuleItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val actions = o.optJSONArray("actions") ?: JSONArray()
                var match = false
                for (j in 0 until actions.length()) {
                    val a = actions.optJSONObject(j)
                    if (a?.optString("type") == "worker" && a.optJSONArray("value")?.toString()?.contains(scriptName) == true) {
                        match = true; break
                    }
                }
                if (match) {
                    val matchers = o.optJSONArray("matchers") ?: JSONArray()
                    val matchValue = matchers.optJSONObject(0)?.optString("value") ?: "—"
                    list.add(EmailRoutingRuleItem(o.optString("id"), matchValue, o.optBoolean("enabled", true)))
                }
            }
            ApiResult(true, list)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun addEmailRoutingRule(
        email: String, apiKey: String, zoneId: String, scriptName: String, matchValue: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("matchers", JSONArray().put(JSONObject().put("type", "literal").put("field", "to").put("value", matchValue)))
                .put("actions", JSONArray().put(JSONObject().put("type", "worker").put("value", JSONArray().put(scriptName))))
                .put("enabled", true)
                .put("name", "worker-$scriptName")
            val req = authPost(email, apiKey, "$BASE/zones/$zoneId/email/routing/rules", body.toString())
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "添加邮件规则失败 (${resp.code})：${parseCfError(respBody)}")
            ApiResult(true, Unit)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun deleteEmailRoutingRule(
        email: String, apiKey: String, zoneId: String, ruleId: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = authDelete(email, apiKey, "$BASE/zones/$zoneId/email/routing/rules/$ruleId")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "删除邮件规则失败 (${resp.code})：${parseCfError(body)}")
            ApiResult(true, Unit)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun rollbackDeployment(
        email: String, apiKey: String, accountId: String, scriptName: String, versionId: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("annotations", JSONObject().put("workers/triggered_by", "rollback"))
                .put("strategy", "percentage")
                .put("versions", JSONArray().put(JSONObject().put("percentage", 100).put("version_id", versionId)))
            val req = authPost(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName/deployments", body.toString())
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "回滚失败 (${resp.code})：${parseCfError(respBody)}")
            ApiResult(true, Unit)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }
}
