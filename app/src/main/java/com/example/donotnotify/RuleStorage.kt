package com.example.donotnotify

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class RuleStorage(private val context: Context) {

    private val gson = Gson()
    private val rulesFile = File(context.filesDir, "rules.json")

    fun getRules(): List<BlockerRule> {
        if (!rulesFile.exists()) {
            return emptyList()
        }
        val json = rulesFile.readText()
        val type = object : TypeToken<List<BlockerRule>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveRules(rules: List<BlockerRule>) {
        val json = gson.toJson(rules)
        rulesFile.writeText(json)
    }
}
