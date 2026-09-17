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
