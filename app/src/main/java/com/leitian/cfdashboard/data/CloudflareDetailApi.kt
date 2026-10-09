package com.leitian.cfdashboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        // 和 CloudflareApi 共用同一个 NetworkLogging.interceptor，两边的日志开关联动。
        OkHttpClient.Builder()
            .addInterceptor(NetworkLogging.interceptor)
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

    private fun authDelete(email: String, apiKey: String, url: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("X-Auth-Email", email)
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .delete()
            .build()
    }

    /** 统一解析 { success, errors: [{message}], result } 里的错误信息 */
    private fun parseCfError(body: String): String {
        return try {
            val json = JSONObject(body)
            json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                ?: "请求失败"
        } catch (e: Exception) {
            "请求失败"
        }
    }

    /** 部署列表 */
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
                val msg = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message") ?: "失败"
                return@withContext ApiResult(false, error = msg)
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
                        fullVersionId = versionId,
                        isLatest = i == 0
                    )
                )
            }
            ApiResult(true, list)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    /** Workers 自定义域名（按 service 过滤） */
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
                // 无权限时返回空列表而不是硬失败
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
            // 始终带上 workers.dev 子域名
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

    /** Access Applications（账号级列表，可能与当前 Worker 无直接关联） */
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

    /** Worker 设置详情 */
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
                    "secret_text" -> "••••••" // Secret 不回显明文
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
                        detail = detail,
                        rawJson = b.toString()
                    )
                )
            }

            val tagsArr = result.optJSONArray("tags") ?: org.json.JSONArray()
            val tags = mutableListOf<String>()
            for (i in 0 until tagsArr.length()) {
                tags.add(tagsArr.getString(i))
            }

            val flagsArr = result.optJSONArray("compatibility_flags") ?: org.json.JSONArray()
            val flags = mutableListOf<String>()
            for (i in 0 until flagsArr.length()) flags.add(flagsArr.getString(i))

            val placement = result.optJSONObject("placement")?.optString("mode") ?: "默认"

            val observability = result.optJSONObject("observability")
            val obsEnabled = observability?.optBoolean("enabled", false) ?: result.optBoolean("logpush", false)
            val sampleRate = observability?.optDouble("head_sampling_rate", 1.0) ?: 1.0

            val logpush = result.optBoolean("logpush", false)

            ApiResult(
                true,
                WorkerSettingsDetail(
                    compatibilityDate = result.optString("compatibility_date", "—"),
                    compatibilityFlags = flags,
                    usageModel = result.optString("usage_model", "standard"),
                    bindings = bindings,
                    tags = tags,
                    logpush = logpush,
                    observabilityEnabled = obsEnabled,
                    headSamplingRate = sampleRate,
                    placementMode = placement
                    // cronTriggers 单独通过 listSchedules 拉取，见下方
                )
            )
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    // ---------------------------------------------------------------------
    // 写操作
    // ---------------------------------------------------------------------

    /**
     * 通用：PATCH /workers/scripts/{name}/settings
     * body 只包含本次要改的顶层字段（observability / compatibility_date / placement 等），
     * 但 "bindings" 一旦出现在 body 里就是整体覆盖，调用方必须自己拼好完整数组。
     */
    private suspend fun patchSettings(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        body: JSONObject
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
            if (!json.optBoolean("success", false)) {
                return@withContext ApiResult(false, error = parseCfError(respBody))
            }
            ApiResult(true, Unit)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    /** 把 BindingItem 列表还原成可提交的 JSONArray（未改动的用 rawJson 原样带回） */
    private fun bindingsToJsonArray(bindings: List<BindingItem>): JSONArray {
        val arr = JSONArray()
        bindings.forEach { arr.put(bindingForResubmit(JSONObject(it.rawJson))) }
        return arr
    }

    /**
     * 通用绑定新增/编辑：传入已经拼好的绑定 JSON（plain_text/secret_text/kv_namespace/r2_bucket/d1/service 都走这个）。
     * originalName 传旧名字用于定位替换；新增传 null。
     */
    private suspend fun upsertBindingRaw(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        currentBindings: List<BindingItem>,
        originalName: String?,
        newBindingJson: JSONObject
    ): ApiResult<Unit> {
        val targetName = originalName ?: newBindingJson.optString("name")
        val kept = currentBindings.filter { it.name != targetName }
        val arr = bindingsToJsonArray(kept)
        arr.put(newBindingJson)
        val body = JSONObject().put("bindings", arr)
        return patchSettings(email, apiKey, accountId, scriptName, body)
    }

    /** 添加一个资源绑定：KV / R2 / D1 / Service */
    suspend fun addResourceBinding(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        currentBindings: List<BindingItem>,
        bindingName: String,
        type: String, // "kv_namespace" | "r2_bucket" | "d1" | "service"
        resourceIdOrName: String
    ): ApiResult<Unit> {
        val json = JSONObject().apply {
            put("type", type)
            put("name", bindingName)
            when (type) {
                "kv_namespace" -> put("namespace_id", resourceIdOrName)
                "r2_bucket" -> put("bucket_name", resourceIdOrName)
                "d1" -> put("id", resourceIdOrName)
                "service" -> { put("service", resourceIdOrName); put("environment", "production") }
            }
        }
        return upsertBindingRaw(email, apiKey, accountId, scriptName, currentBindings, null, json)
    }

    /**
     * 新增或编辑一个 plain_text / secret_text 变量。
     * 会把 currentBindings 里其它类型的绑定原样带上，避免整体覆盖时把 KV/R2/D1/Service 绑定冲掉。
     *
     * @param originalName 编辑时传入旧名字（用于定位替换哪一项）；新增传 null
     */
    suspend fun upsertVariable(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        currentBindings: List<BindingItem>,
        originalName: String?,
        newName: String,
        newValue: String,
        isSecret: Boolean
    ): ApiResult<Unit> {
        val kept = currentBindings.filter { it.name != (originalName ?: newName) }
        val newBindingJson = JSONObject().apply {
            put("type", if (isSecret) "secret_text" else "plain_text")
            put("name", newName)
            put("text", newValue)
        }
        val mergedArr = JSONArray()
        kept.forEach { mergedArr.put(bindingForResubmit(JSONObject(it.rawJson))) }
        mergedArr.put(newBindingJson)
        val body = JSONObject().put("bindings", mergedArr)
        return patchSettings(email, apiKey, accountId, scriptName, body)
    }

    /** 删除一个变量 / 绑定（按名称） */
    suspend fun deleteBinding(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        currentBindings: List<BindingItem>,
        name: String
    ): ApiResult<Unit> {
        val kept = currentBindings.filter { it.name != name }
        val body = JSONObject().put("bindings", bindingsToJsonArray(kept))
        return patchSettings(email, apiKey, accountId, scriptName, body)
    }

    /** 可观察性：日志/跟踪开关 + 采样比例（0~100，会转换成 0~1 传给接口） */
    suspend fun updateObservability(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        enabled: Boolean,
        samplingPercent: Double
    ): ApiResult<Unit> {
        val body = JSONObject().put(
            "observability",
            JSONObject().apply {
                put("enabled", enabled)
                put("head_sampling_rate", (samplingPercent / 100.0).coerceIn(0.0, 1.0))
            }
        )
        return patchSettings(email, apiKey, accountId, scriptName, body)
    }

    /** 运行时设置：兼容日期 / 兼容性标志 / placement */
    suspend fun updateRuntimeSettings(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        compatibilityDate: String,
        compatibilityFlags: List<String>,
        placementMode: String // "" 或 "off" 表示默认，"smart" 表示 Smart Placement
    ): ApiResult<Unit> {
        val body = JSONObject().apply {
            if (compatibilityDate.isNotBlank()) put("compatibility_date", compatibilityDate)
            put("compatibility_flags", JSONArray(compatibilityFlags))
            put(
                "placement",
                if (placementMode.isBlank() || placementMode == "off") JSONObject().put("mode", "off")
                else JSONObject().put("mode", placementMode)
            )
        }
        return patchSettings(email, apiKey, accountId, scriptName, body)
    }

    /** 读取某个 Worker 的 settings 原文（失败返回 null） */
    private fun fetchSettingsBody(email: String, apiKey: String, accountId: String, scriptName: String): String? {
        return try {
            val resp = client.newCall(authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName/settings")).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && JSONObject(body).optBoolean("success", false)) body else null
        } catch (e: Exception) { null }
    }

    /** Worker 绑定的可删除资源：kind = kv / d1 / r2；key = KV/D1 的 ID，或 R2 的存储桶名 */
    private data class BoundRes(val kind: String, val key: String) {
        val id: String get() = "$kind:${key.lowercase()}"
        val kindName: String get() = when (kind) { "kv" -> "KV 命名空间"; "d1" -> "D1 数据库"; else -> "R2 存储桶" }
    }

    private fun parseBoundResources(settingsBody: String): List<BoundRes> {
        val arr = JSONObject(settingsBody).optJSONObject("result")?.optJSONArray("bindings") ?: JSONArray()
        val out = linkedMapOf<String, BoundRes>()
        for (i in 0 until arr.length()) {
            val bd = arr.getJSONObject(i)
            val r = when (bd.optString("type")) {
                "kv_namespace" -> bd.optString("namespace_id").takeIf { it.isNotBlank() }?.let { BoundRes("kv", it) }
                "d1" -> bd.optString("id").takeIf { it.isNotBlank() }?.let { BoundRes("d1", it) }
                "r2_bucket" -> bd.optString("bucket_name").takeIf { it.isNotBlank() }?.let { BoundRes("r2", it) }
                else -> null
            }
            if (r != null) out.putIfAbsent(r.id, r)
        }
        return out.values.toList()
    }

    /**
     * 删除 Worker；deleteResources 为 true 时，顺带删除它绑定的 KV 命名空间 / D1 数据库 / R2 存储桶。
     * 安全规则：
     *  - 先读出它绑定了哪些资源；读不出来就整体取消（不会只删一半）。没有绑定任何资源时，只删 Worker。
     *  - 只删"独占"的：账号下其他 Worker、Pages 项目只要还引用同一个资源，就保留，并在结果里说明。
     *    只要有一个检查读不出来，就当作"可能还在用"，全部保留。
     *  - 先删 Worker，成功后才删资源；Worker 删除失败则一个资源都不动。
     *  - R2 存储桶非空时 Cloudflare 会拒绝删除，这里不会去清空里面的文件，只提示手动处理。
     * 返回值是要展示给用户的说明行（没有资源时为空）。
     */
    suspend fun deleteWorker(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        deleteResources: Boolean = false
    ): ApiResult<List<String>> = withContext(Dispatchers.IO) {
        try {
            var resources: List<BoundRes> = emptyList()
            val keepReasons = mutableMapOf<String, String>() // BoundRes.id -> 保留原因
            var globalKeepReason: String? = null

            if (deleteResources) {
                val mine = fetchSettingsBody(email, apiKey, accountId, scriptName)
                    ?: return@withContext ApiResult(false, error = "读取该 Worker 的绑定失败，为避免留下找不到的资源，已取消删除。可取消勾选「同时删除绑定的资源」后再删除。")
                resources = parseBoundResources(mine)

                if (resources.isNotEmpty()) {
                    // 其他 Worker 是否还在用
                    try {
                        val listResp = client.newCall(authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts")).execute()
                        val listBody = listResp.body?.string() ?: ""
                        if (!listResp.isSuccessful) throw RuntimeException("列出 Worker 失败 (${listResp.code})")
                        val others = JSONObject(listBody).optJSONArray("result") ?: JSONArray()
                        for (i in 0 until others.length()) {
                            val id = others.getJSONObject(i).optString("id")
                            if (id.isBlank() || id == scriptName) continue
                            val body = fetchSettingsBody(email, apiKey, accountId, id)
                                ?: throw RuntimeException("读取 Worker「$id」的绑定失败")
                            val used = parseBoundResources(body).map { it.id }.toSet()
                            resources.forEach { r -> if (r.id in used) keepReasons.putIfAbsent(r.id, "仍被 Worker「$id」使用") }
                        }
                    } catch (e: Exception) {
                        globalKeepReason = "无法确认其他 Worker 是否还在使用（${e.message}）"
                    }
                    // Pages 项目是否还在用（Pages 的绑定在项目配置里，直接在列表原文里找，宁可多保留）
                    try {
                        val pr = client.newCall(authGet(email, apiKey, "$BASE/accounts/$accountId/pages/projects?per_page=100")).execute()
                        val pb = pr.body?.string() ?: ""
                        if (!pr.isSuccessful) throw RuntimeException("列出 Pages 项目失败 (${pr.code})")
                        val pbn = pb.replace(Regex("\\s"), "")
                        resources.forEach { r ->
                            val hit = if (r.kind == "r2") pbn.contains("\"name\":\"${r.key}\"") || pbn.contains("\"bucket_name\":\"${r.key}\"")
                                      else pbn.contains(r.key, ignoreCase = true)
                            if (hit) keepReasons.putIfAbsent(r.id, "仍被某个 Pages 项目使用")
                        }
                    } catch (e: Exception) {
                        if (globalKeepReason == null) globalKeepReason = "无法确认 Pages 项目是否还在使用（${e.message}）"
                    }
                }
            }

            // 先查名称（删除后就查不到了），仅用于说明
            val titles = mutableMapOf<String, String>()
            resources.forEach { r ->
                try {
                    val url = when (r.kind) {
                        "kv" -> "$BASE/accounts/$accountId/storage/kv/namespaces/${r.key}"
                        "d1" -> "$BASE/accounts/$accountId/d1/database/${r.key}"
                        else -> null
                    }
                    if (url != null) {
                        val rr = client.newCall(authGet(email, apiKey, url)).execute()
                        val rb = rr.body?.string() ?: ""
                        if (rr.isSuccessful) {
                            val res = JSONObject(rb).optJSONObject("result")
                            val t = if (r.kind == "kv") res?.optString("title") else res?.optString("name")
                            if (!t.isNullOrBlank()) titles[r.id] = t
                        }
                    }
                } catch (_: Exception) {}
            }
            fun label(r: BoundRes) = if (r.kind == "r2") "${r.kindName}「${r.key}」" else titles[r.id]?.let { "${r.kindName}「$it」（${r.key}）" } ?: "${r.kindName} ${r.key}"

            // 删除 Worker
            val req = authDelete(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "删除失败 (${resp.code})：${parseCfError(body)}")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = parseCfError(body))

            // 删除独占的资源
            val notes = mutableListOf<String>()
            for (r in resources) {
                val keep = globalKeepReason ?: keepReasons[r.id]
                if (keep != null) { notes.add("已保留${label(r)}：$keep，请自行确认后手动删除。"); continue }
                try {
                    val url = when (r.kind) {
                        "kv" -> "$BASE/accounts/$accountId/storage/kv/namespaces/${r.key}"
                        "d1" -> "$BASE/accounts/$accountId/d1/database/${r.key}"
                        else -> "$BASE/accounts/$accountId/r2/buckets/${r.key}"
                    }
                    val dr = client.newCall(authDelete(email, apiKey, url)).execute()
                    val db = dr.body?.string() ?: ""
                    val err = parseCfError(db)
                    when {
                        dr.isSuccessful -> notes.add("已删除${label(r)}。")
                        dr.code == 404 -> notes.add("${label(r)}已经不存在，无需删除。")
                        r.kind == "r2" && err.contains("not empty", ignoreCase = true) ->
                            notes.add("${label(r)}里还有文件，Cloudflare 不允许删除非空存储桶。为避免误删数据，没有自动清空，请手动清空后再删除。")
                        else -> notes.add("${label(r)}删除失败 (${dr.code})：$err，请手动删除。")
                    }
                } catch (e: Exception) {
                    notes.add("${label(r)}删除失败：${e.message ?: "网络错误"}，请手动删除。")
                }
            }
            ApiResult(true, notes)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    /** 拉取 Cron 触发器列表 */
    suspend fun listSchedules(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String
    ): ApiResult<List<String>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName/schedules")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(true, emptyList()) // 无 cron 时 CF 也可能返回非 200，静默按空处理
            val json = JSONObject(body)
            val arr = json.optJSONObject("result")?.optJSONArray("schedules") ?: JSONArray()
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getJSONObject(i).optString("cron"))
            }
            ApiResult(true, list)
        } catch (e: Exception) {
            ApiResult(false, error = e.message ?: "网络错误")
        }
    }

    /**
     * 覆盖式更新 Cron 触发器列表（整体提交，传入改动后的完整列表）
     */
    suspend fun updateSchedules(
        email: String,
        apiKey: String,
        accountId: String,
        scriptName: String,
        crons: List<String>
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            crons.forEach { arr.put(JSONObject().put("cron", it)) }
            val req = authPut(
                email, apiKey,
                "$BASE/accounts/$accountId/workers/scripts/$scriptName/schedules",
                arr.toString()
            )
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

    // ---------------------------------------------------------------------
    // 资源列表拉取（绑定 Tab 的资源选择器 / 域 Tab 的 Zone 选择器 / 触发事件的 Queue 选择器）
    // ---------------------------------------------------------------------

    suspend fun listKvNamespaces(email: String, apiKey: String, accountId: String): ApiResult<List<KvNamespaceItem>> =
        withContext(Dispatchers.IO) {
            try {
                val req = authGet(email, apiKey, "$BASE/accounts/$accountId/storage/kv/namespaces?per_page=50")
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext ApiResult(false, error = "拉取 KV 命名空间失败 (${resp.code})")
                val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
                val list = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    KvNamespaceItem(o.optString("id"), o.optString("title"))
                }
                ApiResult(true, list)
            } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
        }

    suspend fun listR2Buckets(email: String, apiKey: String, accountId: String): ApiResult<List<R2BucketItem>> =
        withContext(Dispatchers.IO) {
            try {
                val req = authGet(email, apiKey, "$BASE/accounts/$accountId/r2/buckets")
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext ApiResult(false, error = "拉取 R2 存储桶失败 (${resp.code})")
                val arr = JSONObject(body).optJSONObject("result")?.optJSONArray("buckets") ?: JSONArray()
                val list = (0 until arr.length()).map { R2BucketItem(arr.getJSONObject(it).optString("name")) }
                ApiResult(true, list)
            } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
        }

    suspend fun listD1Databases(email: String, apiKey: String, accountId: String): ApiResult<List<D1DatabaseItem>> =
        withContext(Dispatchers.IO) {
            try {
                val req = authGet(email, apiKey, "$BASE/accounts/$accountId/d1/database")
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext ApiResult(false, error = "拉取 D1 数据库失败 (${resp.code})")
                val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
                val list = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    D1DatabaseItem(o.optString("uuid"), o.optString("name"))
                }
                ApiResult(true, list)
            } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
        }

    /** 账号下所有 Zone（域名添加、邮件路由都要选归属 Zone） */
    suspend fun listZones(email: String, apiKey: String, accountId: String): ApiResult<List<ZoneItem>> =
        withContext(Dispatchers.IO) {
            try {
                val req = authGet(email, apiKey, "$BASE/zones?account.id=$accountId&per_page=50")
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext ApiResult(false, error = "拉取 Zone 列表失败 (${resp.code})")
                val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
                val list = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    ZoneItem(o.optString("id"), o.optString("name"))
                }
                ApiResult(true, list)
            } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
        }

    /** 账号下所有 Queue，并标出当前脚本是否已经是某个 Queue 的消费者 */
    suspend fun listQueues(email: String, apiKey: String, accountId: String, scriptName: String): ApiResult<List<QueueItem>> =
        withContext(Dispatchers.IO) {
            try {
                val req = authGet(email, apiKey, "$BASE/accounts/$accountId/queues")
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext ApiResult(false, error = "拉取 Queues 失败 (${resp.code})")
                val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
                val list = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    var consumerId: String? = null
                    val consumers = o.optJSONArray("consumers")
                    if (consumers != null) {
                        for (c in 0 until consumers.length()) {
                            val consumer = consumers.getJSONObject(c)
                            if (consumer.optString("script") == scriptName) {
                                consumerId = consumer.optString("consumer_id").ifBlank { consumer.optString("id") }
                            }
                        }
                    }
                    QueueItem(o.optString("queue_id").ifBlank { o.optString("id") }, o.optString("queue_name").ifBlank { o.optString("name") }, consumerId)
                }
                ApiResult(true, list)
            } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
        }

    // ---------------------------------------------------------------------
    // 域：自定义域名（Custom Domains）增删
    // ---------------------------------------------------------------------

    suspend fun addCustomDomain(
        email: String, apiKey: String, accountId: String, scriptName: String, hostname: String, zoneId: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("hostname", hostname)
                put("zone_id", zoneId)
                put("service", scriptName)
                put("environment", "production")
            }
            val req = authPut(email, apiKey, "$BASE/accounts/$accountId/workers/domains", body.toString())
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "添加失败 (${resp.code})：${parseCfError(respBody)}")
            val json = JSONObject(respBody)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = parseCfError(respBody))
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
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "删除失败 (${resp.code})：${parseCfError(body)}")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = parseCfError(body))
            ApiResult(true, Unit)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    // ---------------------------------------------------------------------
    // 触发事件：Queues 消费者
    // ---------------------------------------------------------------------

    suspend fun addQueueConsumer(
        email: String, apiKey: String, accountId: String, scriptName: String, queueId: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("script_name", scriptName)
            val req = authPost(email, apiKey, "$BASE/accounts/$accountId/queues/$queueId/consumers", body.toString())
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "添加失败 (${resp.code})：${parseCfError(respBody)}")
            val json = JSONObject(respBody)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = parseCfError(respBody))
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
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "删除失败 (${resp.code})：${parseCfError(body)}")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = parseCfError(body))
            ApiResult(true, Unit)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    // ---------------------------------------------------------------------
    // 触发事件：邮件路由规则（挂在 Zone 上，转发到当前 Worker）
    // ---------------------------------------------------------------------

    suspend fun listEmailRoutingRules(
        email: String, apiKey: String, zoneId: String, scriptName: String
    ): ApiResult<List<EmailRoutingRuleItem>> = withContext(Dispatchers.IO) {
        try {
            val req = authGet(email, apiKey, "$BASE/zones/$zoneId/email/routing/rules?per_page=50")
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "拉取邮件规则失败 (${resp.code})")
            val arr = JSONObject(body).optJSONArray("result") ?: JSONArray()
            val list = mutableListOf<EmailRoutingRuleItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val actions = o.optJSONArray("actions") ?: JSONArray()
                var targetsThisScript = false
                for (a in 0 until actions.length()) {
                    val action = actions.getJSONObject(a)
                    if (action.optString("type") == "worker") {
                        val values = action.optJSONArray("value") ?: JSONArray()
                        for (v in 0 until values.length()) if (values.getString(v) == scriptName) targetsThisScript = true
                    }
                }
                if (!targetsThisScript) continue
                val matchers = o.optJSONArray("matchers") ?: JSONArray()
                val matchValue = if (matchers.length() > 0) matchers.getJSONObject(0).optString("value") else "—"
                list.add(EmailRoutingRuleItem(o.optString("id"), matchValue, o.optBoolean("enabled", true)))
            }
            ApiResult(true, list)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    suspend fun addEmailRoutingRule(
        email: String, apiKey: String, zoneId: String, scriptName: String, matchAddress: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("matchers", JSONArray().put(JSONObject().apply {
                    put("type", "literal"); put("field", "to"); put("value", matchAddress)
                }))
                put("actions", JSONArray().put(JSONObject().apply {
                    put("type", "worker"); put("value", JSONArray().put(scriptName))
                }))
                put("enabled", true)
            }
            val req = authPost(email, apiKey, "$BASE/zones/$zoneId/email/routing/rules", body.toString())
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "添加失败 (${resp.code})：${parseCfError(respBody)}")
            val json = JSONObject(respBody)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = parseCfError(respBody))
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
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "删除失败 (${resp.code})：${parseCfError(body)}")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = parseCfError(body))
            ApiResult(true, Unit)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }

    // ---------------------------------------------------------------------
    // 部署：回滚（把某个历史版本设为 100% 流量）
    // ---------------------------------------------------------------------

    suspend fun rollbackDeployment(
        email: String, apiKey: String, accountId: String, scriptName: String, versionId: String
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("strategy", "percentage")
                put("versions", JSONArray().put(JSONObject().apply {
                    put("version_id", versionId); put("percentage", 100)
                }))
            }
            val req = authPost(email, apiKey, "$BASE/accounts/$accountId/workers/scripts/$scriptName/deployments", body.toString())
            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext ApiResult(false, error = "回滚失败 (${resp.code})：${parseCfError(respBody)}")
            val json = JSONObject(respBody)
            if (!json.optBoolean("success", false)) return@withContext ApiResult(false, error = parseCfError(respBody))
            ApiResult(true, Unit)
        } catch (e: Exception) { ApiResult(false, error = e.message ?: "网络错误") }
    }
}
