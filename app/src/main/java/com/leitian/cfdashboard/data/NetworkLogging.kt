package com.leitian.cfdashboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.logging.HttpLoggingInterceptor

private val Context.debugDataStore: DataStore<Preferences> by preferencesDataStore("cf_debug_settings")

/**
 * 网络请求日志开关。默认关闭（Debug/Release 都一样），排查问题时在首页顶部菜单里手动打开，
 * 完整的请求/响应 body 才会打到 Logcat；[interceptor] 是 CloudflareApi / CloudflareDetailApi
 * 共用的同一个 HttpLoggingInterceptor 实例，切换开关时直接改它的 level，不需要重建 OkHttpClient。
 */
object NetworkLogging {
    private val VERBOSE = booleanPreferencesKey("verbose_network_logging")

    val interceptor: HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    val enabled: Boolean
        get() = interceptor.level != HttpLoggingInterceptor.Level.NONE

    fun enabledFlow(context: Context): Flow<Boolean> =
        context.debugDataStore.data.map { it[VERBOSE] ?: false }

    suspend fun setEnabled(context: Context, value: Boolean) {
        interceptor.level = if (value) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        context.debugDataStore.edit { it[VERBOSE] = value }
    }

    /** App 冷启动时调用一次，把上次关闭前的开关状态恢复出来。 */
    suspend fun restore(context: Context) {
        val stored = context.debugDataStore.data.map { it[VERBOSE] ?: false }.first()
        interceptor.level = if (stored) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }
}
