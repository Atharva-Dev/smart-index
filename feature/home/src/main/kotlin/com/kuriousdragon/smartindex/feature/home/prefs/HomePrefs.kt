package com.kuriousdragon.smartindex.feature.home.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.kuriousdragon.smartindex.core.model.SiLog
import com.kuriousdragon.smartindex.feature.home.model.HomeClock
import com.kuriousdragon.smartindex.feature.home.model.HomeConfig
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Home's persisted settings (DataStore file "sidx_app", provided by :app). */
class HomePrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: HomeClock,
) {
    /** Epoch ms until which the permission banner is hidden; 0 when absent; an IOException reads as 0. */
    val permissionBannerSnoozedUntil: Flow<Long> = dataStore.data
        .catch { t ->
            if (t !is IOException) throw t
            SiLog.w("Home", "prefs read failed", t)
            emit(emptyPreferences())
        }
        .map { it[SNOOZED_UNTIL] ?: 0L }

    /** Hides the permission banner for seven days from now. */
    suspend fun snoozePermissionBanner() {
        dataStore.edit { it[SNOOZED_UNTIL] = clock.nowMs() + HomeConfig.PERMISSION_SNOOZE_MS }
    }

    private companion object {
        val SNOOZED_UNTIL = longPreferencesKey("perm_banner_snoozed_until")
    }
}
