package com.leitian.cfdashboard.data

data class DeploymentItem(
    val id: String,
    val createdOn: String,
    val source: String,
    val authorEmail: String,
    val message: String,
    val versionId: String,
    val isLatest: Boolean
)

data class DomainItem(
    val id: String,
    val hostname: String,
    val service: String,
    val environment: String
)

data class AccessAppItem(
    val id: String,
    val name: String,
    val domain: String,
    val type: String
)

data class WorkerSettingsDetail(
    val compatibilityDate: String,
    val compatibilityFlags: List<String> = emptyList(),
    val usageModel: String,
    val bindings: List<BindingItem>,
    val tags: List<String>,
    val logpush: Boolean,
    val observabilityEnabled: Boolean = false,
    val headSamplingRate: Double = 1.0,
    val placementMode: String,
    val cronTriggers: List<String> = emptyList()
)

data class BindingItem(
    val name: String,
    val type: String,
    val detail: String,
    /** 原始 JSON（未做任何转换），用于"整体覆盖"式提交时把没改动的绑定原样带回去 */
    val rawJson: String = "{}"
)

// ---- 资源选择器用的轻量列表项（绑定 Tab / 域 Tab / 触发事件用） ----

data class KvNamespaceItem(val id: String, val title: String)
data class R2BucketItem(val name: String)
data class D1DatabaseItem(val id: String, val name: String)
data class ZoneItem(val id: String, val name: String)

data class QueueItem(
    val id: String,
    val name: String,
    /** 若当前 Worker 已是该队列的消费者，这里是对应 consumer 记录的 id，否则为 null */
    val consumerId: String? = null
)

data class EmailRoutingRuleItem(
    val id: String,
    val matchValue: String,
    val enabled: Boolean
)
