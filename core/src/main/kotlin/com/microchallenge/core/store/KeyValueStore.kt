package com.microchallenge.core.store

/**
 * TinyDB-style key/value store (as in MIT App Inventor's TinyDB).
 * Android backs it with SharedPreferences; tests use [InMemoryStore].
 */
interface KeyValueStore {
    fun getInt(key: String, default: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, default: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getString(key: String, default: String = ""): String
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

class InMemoryStore : KeyValueStore {
    private val map = HashMap<String, Any>()
    override fun getInt(key: String, default: Int) = map[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getLong(key: String, default: Long) = map[key] as? Long ?: default
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getString(key: String, default: String) = map[key] as? String ?: default
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun clear() = map.clear()
}
