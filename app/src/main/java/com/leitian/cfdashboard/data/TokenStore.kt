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

    private val EMAIL = stringPreferencesKey("email")
    private val API_KEY = stringPreferencesKey("api_key")
    private val ACCOUNT_ID = stringPreferencesKey("account_id")
    private val ACCOUNT_NAME = stringPreferencesKey("account_name")

    val emailFlow: Flow<String?> = context.dataStore.data.map { it[EMAIL] }

    suspend fun save(email: String, apiKey: String, accountId: String, accountName: String) {
        context.dataStore.edit {
            it[EMAIL] = email
            it[API_KEY] = apiKey
            it[ACCOUNT_ID] = accountId
            it[ACCOUNT_NAME] = accountName
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun getCredentials(): Triple<String?, String?, String?> {
        val prefs = context.dataStore.data.first()
        return Triple(prefs[EMAIL], prefs[API_KEY], prefs[ACCOUNT_ID])
    }
}
