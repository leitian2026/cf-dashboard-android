package com.leitian.cfdashboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("cf_settings")

/** 一个已登录的 Cloudflare 账号（邮箱 + Global API Key + 对应的 account id/name）。 */
data class SavedAccount(
    val email: String,
    val apiKey: String,
    val accountId: String,
    val accountName: String
)

class TokenStore(private val context: Context) {

    // 旧版本（单账号）用的字段，仅用于从旧数据迁移；新版本统一存在 ACCOUNTS_JSON 里。
    private val EMAIL = stringPreferencesKey("email")
    private val API_KEY = stringPreferencesKey("api_key")
    private val ACCOUNT_ID = stringPreferencesKey("account_id")
    private val ACCOUNT_NAME = stringPreferencesKey("account_name")
    private val ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
    // 首页 Workers 列表里"已折叠"的账号 id，重启 App 后保持上次的折叠/展开状态。
    private val COLLAPSED_ACCOUNT_IDS = stringSetPreferencesKey("collapsed_account_ids")

    /** 保留字段：第一个已登录账号的邮箱，仅用于兼容旧代码里对单一邮箱的展示需求。 */
    val emailFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        decode(prefs[ACCOUNTS_JSON]).firstOrNull()?.email ?: prefs[EMAIL]
    }

    val accountsFlow: Flow<List<SavedAccount>> = context.dataStore.data.map { decode(it[ACCOUNTS_JSON]) }

    private fun decode(json: String?): List<SavedAccount> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedAccount(
                    email = o.optString("email"),
                    apiKey = o.optString("apiKey"),
                    accountId = o.optString("accountId"),
                    accountName = o.optString("accountName")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encode(accounts: List<SavedAccount>): String {
        val arr = JSONArray()
        accounts.forEach { a ->
            arr.put(
                JSONObject()
                    .put("email", a.email)
                    .put("apiKey", a.apiKey)
                    .put("accountId", a.accountId)
                    .put("accountName", a.accountName)
            )
        }
        return arr.toString()
    }

    /**
     * 读取所有已登录账号。如果本地只有旧版本留下的单账号数据（升级前安装的），
     * 自动把它迁移成新的账号列表格式（列表里的第一项），迁移只做一次。
     */
    suspend fun getAccounts(): List<SavedAccount> {
        val prefs = context.dataStore.data.first()
        val stored = decode(prefs[ACCOUNTS_JSON])
        if (stored.isNotEmpty()) return stored
        val e = prefs[EMAIL]; val k = prefs[API_KEY]; val a = prefs[ACCOUNT_ID]; val n = prefs[ACCOUNT_NAME]
        if (!e.isNullOrBlank() && !k.isNullOrBlank() && !a.isNullOrBlank()) {
            val migrated = listOf(SavedAccount(e, k, a, n?.takeIf { it.isNotBlank() } ?: e))
            saveAccounts(migrated)
            return migrated
        }
        return emptyList()
    }

    suspend fun saveAccounts(accounts: List<SavedAccount>) {
        context.dataStore.edit { prefs ->
            prefs[ACCOUNTS_JSON] = encode(accounts)
            // 清掉旧版单账号字段，避免残留数据和新格式冲突
            prefs.remove(EMAIL); prefs.remove(API_KEY); prefs.remove(ACCOUNT_ID); prefs.remove(ACCOUNT_NAME)
        }
    }

    /** 新增一个账号；如果这个 Cloudflare account id 已经登录过，用新凭据覆盖旧的（而不是重复添加）。 */
    suspend fun addAccount(account: SavedAccount) {
        val current = getAccounts().filterNot { it.accountId == account.accountId }
        saveAccounts(current + account)
    }

    suspend fun removeAccount(accountId: String) {
        saveAccounts(getAccounts().filterNot { it.accountId == accountId })
    }

    suspend fun getCollapsedAccountIds(): Set<String> =
        context.dataStore.data.first()[COLLAPSED_ACCOUNT_IDS] ?: emptySet()

    suspend fun setCollapsedAccountIds(ids: Collection<String>) {
        context.dataStore.edit { prefs -> prefs[COLLAPSED_ACCOUNT_IDS] = ids.toSet() }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
