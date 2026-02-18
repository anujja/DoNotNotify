package com.donotnotify.donotnotify

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File

class RuleStorage(private val context: Context) {

    companion object {
        private const val TAG = "RuleStorage"
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
            return try {
                val json = rulesFile.readText()
                val type = object : TypeToken<List<BlockerRule>>() {}.type
                val rules = gson.fromJson<List<BlockerRule>>(json, type) ?: emptyList()
                cachedRules = rules
                rules
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Corrupted rules file, deleting", e)
                rulesFile.delete()
                emptyList<BlockerRule>().also { cachedRules = it }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading rules", e)
                emptyList<BlockerRule>().also { cachedRules = it }
            }
        }
    }

    fun saveRules(rules: List<BlockerRule>) {
        synchronized(lock) {
            val json = gson.toJson(rules)
            rulesFile.writeText(json)
            cachedRules = rules
        }
    }

    fun invalidateCache() {
        synchronized(lock) {
            cachedRules = null
        }
    }
}
