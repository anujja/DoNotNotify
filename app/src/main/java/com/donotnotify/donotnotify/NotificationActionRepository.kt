package com.donotnotify.donotnotify

import android.app.PendingIntent
import java.util.concurrent.ConcurrentHashMap

object NotificationActionRepository {
    private val actions = ConcurrentHashMap<String, PendingIntent>()

    fun saveAction(id: String, action: PendingIntent) {
        actions[id] = action
    }

    fun getAction(id: String): PendingIntent? {
        return actions[id]
    }
    
    fun clear() {
        actions.clear()
    }
}
