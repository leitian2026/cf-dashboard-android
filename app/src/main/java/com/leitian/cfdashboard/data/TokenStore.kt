package com.leitian.cfdashboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("cf_settings")

class TokenStore(private val context: Context) {

    private val TOKEN = stringPreferencesKey("api_token")
    private val ACCOUNT_ID = stringPreferencesKey("account_id")
    private val ACCOUNT_NAME = stringPreferencesKey("account_name")

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[TOKEN] }
    val accountIdFlow: Flow<String?> = context.dataStore.data.map { it[ACCOUNT_ID] }

    suspend fun save(token: String, accountId: String, accountName: String) {
        context.dataStore.edit {
            it[TOKEN] = token
            it[ACCOUNT_ID] = accountId
            it[ACCOUNT_NAME] = accountName
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun getTokenAndAccount(): Pair<String?, String?> {
        val prefs = context.dataStore.data.first()
        return prefs[TOKEN] to prefs[ACCOUNT_ID]
    }
}
