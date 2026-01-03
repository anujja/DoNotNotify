package com.donotnotify.donotnotify

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Keep
@Parcelize
data class SimpleNotification(
    val appLabel: String?,
    val packageName: String?,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val wasOngoing: Boolean = false,
    val id: String? = UUID.randomUUID().toString()
) : Parcelable
