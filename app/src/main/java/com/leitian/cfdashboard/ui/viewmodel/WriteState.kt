package com.leitian.cfdashboard.ui.viewmodel

data class WriteState(
    val loading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null
) {
    companion object {
        val Idle = WriteState()
        fun loading() = WriteState(loading = true)
        fun success() = WriteState(success = true)
        fun error(msg: String) = WriteState(error = msg)
    }
}
