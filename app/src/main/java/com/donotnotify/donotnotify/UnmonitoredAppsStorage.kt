package com.donotnotify.donotnotify

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class UnmonitoredAppsStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("unmonitored_apps_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "unmonitored_apps"

    fun getUnmonitoredApps(): Set<String> {
        val json = prefs.getString(key, null) ?: return emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        return gson.fromJson(json, type)
    }

    fun addApp(packageName: String) {
        val currentApps = getUnmonitoredApps().toMutableSet()
        currentApps.add(packageName)
        saveApps(currentApps)
    }

    fun removeApp(packageName: String) {
        val currentApps = getUnmonitoredApps().toMutableSet()
        currentApps.remove(packageName)
        saveApps(currentApps)
    }

    fun isAppUnmonitored(packageName: String): Boolean {
        return getUnmonitoredApps().contains(packageName)
    }

    private fun saveApps(apps: Set<String>) {
        val json = gson.toJson(apps)
        prefs.edit().putString(key, json).apply()
    }
}
