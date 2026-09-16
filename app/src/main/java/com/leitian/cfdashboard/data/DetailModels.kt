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
    val usageModel: String,
    val bindings: List<BindingItem>,
    val tags: List<String>,
    val logpush: Boolean,
    val placementMode: String
)

data class BindingItem(
    val name: String,
    val type: String,
    val detail: String
)
