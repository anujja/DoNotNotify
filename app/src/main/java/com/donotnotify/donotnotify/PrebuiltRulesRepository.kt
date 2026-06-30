package com.donotnotify.donotnotify

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

class PrebuiltRulesRepository(private val context: Context) {
    /**
     * Loads the locale-appropriate prebuilt rules. The file lives in `res/raw` with
     * locale qualifiers (`raw-ja`, `raw-fr`, …), so Android resolves the right variant
     * automatically and falls back to the English default in `res/raw`.
     *
     * Reads with explicit UTF-8 and closes the stream. A malformed (e.g. mistranslated)
     * file must never crash startup via [com.donotnotify.donotnotify.MainActivity]'s
     * rule auto-install, so parse failures degrade to an empty list.
     */
    fun getPrebuiltRules(): List<BlockerRule> {
        return try {
            context.resources.openRawResource(R.raw.prebuilt_rules).use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    val type = object : TypeToken<List<BlockerRule>>() {}.type
                    Gson().fromJson<List<BlockerRule>>(reader, type) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("PrebuiltRulesRepository", "Failed to load prebuilt rules", e)
            emptyList()
        }
    }
}
