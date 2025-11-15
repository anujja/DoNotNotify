package com.donotnotify.donotnotify

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SimpleNotification(
    val appLabel: String?,
    val packageName: String?,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val wasOngoing: Boolean = false // New field to indicate if it was an ongoing notification
) : Parcelable
