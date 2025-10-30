package com.example.donotnotify

data class BlockerRule(
    val appName: String? = null,
    val titleRegex: String? = null,
    val textRegex: String? = null
)
