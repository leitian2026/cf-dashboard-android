package com.leitian.cfdashboard.ui.viewmodel

/**
 * 通用写操作状态机（设置 / 绑定 / 域名 / 触发器 / 危险操作等共用）。
 * 与 [MainViewModel.UploadState] 模式一致：Idle → Loading → Success/Error。
 */
sealed class WriteState {
    data object Idle : WriteState()
    data object Loading : WriteState()
    data class Success(val message: String) : WriteState()
    data class Error(val message: String) : WriteState()
}
