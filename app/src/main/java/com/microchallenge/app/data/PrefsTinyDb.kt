package com.microchallenge.app.data

import android.content.Context
import android.content.SharedPreferences
import com.microchallenge.core.store.KeyValueStore

/**
 * TinyDB equivalent: a flat key/value store on SharedPreferences.
 * Writes use apply() (async to disk, sync in memory), which is fine for
 * a handful of keys written once per 10-second play.
 */
class PrefsTinyDb(context: Context, name: String = "tinydb") : KeyValueStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun clear() = prefs.edit().clear().apply()

    fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
}
