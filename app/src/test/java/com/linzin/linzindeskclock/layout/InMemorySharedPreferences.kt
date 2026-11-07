package com.linzin.linzindeskclock.layout

import android.content.SharedPreferences

/** In-memory storage boundary; tests exercise the real migration and persistence code. */
internal class InMemorySharedPreferences(initial: Map<String, Any> = emptyMap()) : SharedPreferences {
    private val values = initial.toMutableMap()
    var writeCount = 0
        private set

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun getString(key: String?, defValue: String?): String? = values[key]?.let { it as String } ?: defValue
    override fun getInt(key: String?, defValue: Int): Int = values[key]?.let { it as Int } ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key]?.let { it as Long } ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key]?.let { it as Float } ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key]?.let { it as Boolean } ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key]?.let { (it as Set<String>).toMutableSet() } ?: defValues
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clear = false
        private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply { changes[requireNotNull(key)] = value }
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = put(key, values?.toSet())
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)
        override fun remove(key: String?): SharedPreferences.Editor = put(key, null)
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            for ((key, value) in changes) {
                if (value == null) values.remove(key) else values[key] = value
            }
            writeCount++
        }
    }
}
