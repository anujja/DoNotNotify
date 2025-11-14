package com.donotnotify.donotnotify

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BlockerRule(
    val appName: String? = null,
    val titleRegex: String? = null,
    val textRegex: String? = null,
    val blockedCount: Int = 0 // Add blockedCount with a default value
) : Parcelable
