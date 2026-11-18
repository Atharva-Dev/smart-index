package com.kuriousdragon.smartindex.start

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.kuriousdragon.smartindex.core.model.SiLog
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Persisted "onboarding completed" flag (DataStore file `sidx_app`). */
@Singleton
class OnboardingStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    /** False when absent; an [IOException] reading the file is logged and treated as false. */
    val completed: Flow<Boolean> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                SiLog.w("App", "onboarding flag read failed", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[KEY] ?: false }

    suspend fun markCompleted() {
        dataStore.edit { it[KEY] = true }
    }

    private companion object {
        val KEY = booleanPreferencesKey("onboarding_completed")
    }
}
