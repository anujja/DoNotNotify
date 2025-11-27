package com.donotnotify.donotnotify

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

class PrebuiltRulesRepository(private val context: Context) {
    fun getPrebuiltRules(): List<BlockerRule> {
        val inputStream = context.assets.open("prebuilt_rules.json")
        val reader = InputStreamReader(inputStream)
        val type = object : TypeToken<List<BlockerRule>>() {}.type
        return Gson().fromJson(reader, type)
    }
}
