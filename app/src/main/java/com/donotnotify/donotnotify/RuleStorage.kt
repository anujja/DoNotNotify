package com.donotnotify.donotnotify

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class RuleStorage(private val context: Context) {

    companion object {
        @Volatile
        private var cachedRules: List<BlockerRule>? = null
        private val lock = Any()
    }

    private val gson = Gson()
    private val rulesFile = File(context.filesDir, "rules.json")

    fun getRules(): List<BlockerRule> {
        cachedRules?.let { return it }
        synchronized(lock) {
            cachedRules?.let { return it }
            if (!rulesFile.exists()) {
                return emptyList<BlockerRule>().also { cachedRules = it }
            }
            val json = rulesFile.readText()
            val type = object : TypeToken<List<BlockerRule>>() {}.type
            val rules = gson.fromJson<List<BlockerRule>>(json, type) ?: emptyList()
            cachedRules = rules
            return rules
        }
    }

    fun saveRules(rules: List<BlockerRule>) {
        synchronized(lock) {
            val json = gson.toJson(rules)
            rulesFile.writeText(json)
            cachedRules = rules
        }
    }
}
