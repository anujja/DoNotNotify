package com.example.donotnotify

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SimpleNotification(
    val appLabel: String?,
    val packageName: String?,
    val title: String?,
    val text: String?
) : Parcelable
