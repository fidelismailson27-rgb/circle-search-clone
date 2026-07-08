package com.circulesearch.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single DataStore instance backing both [EndpointProfileLocalDataSource] (values
 * encrypted before being placed under [Preferences]) and
 * [SearchPreferencesLocalDataSource] (plain — nothing secret in search preferences).
 */
internal val Context.circleSearchSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "circle_search_settings",
)
