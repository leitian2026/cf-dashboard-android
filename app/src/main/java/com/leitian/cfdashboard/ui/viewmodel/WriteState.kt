package com.leitian.cfdashboard.ui.viewmodel

/**
 * 通用写操作状态机：Idle → Loading → Success/Error。
 * 变量增删改、可观察性、运行时设置、Cron、删除 Worker 等各类写操作复用同一套状态定义，
 * 每个功能区在 MainViewModel 里持有各自独立的 StateFlow<WriteState> 实例，避免互相覆盖。
 */
sealed class WriteState {
    object Idle : WriteState()
    object Loading : WriteState()
    data class Success(val message: String) : WriteState()
    data class Error(val message: String) : WriteState()
}
