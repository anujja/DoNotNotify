package com.donotnotify.donotnotify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Single place external URLs are handed to the system. A device without a browser (or a
 * restricted profile) throws from startActivity; callers get `false` (after a toast)
 * instead of a crash, so they can avoid committing state changes — e.g. the
 * community-share nudge is only marked handled after a successful handoff. Only the
 * launch failures startActivity is documented to throw are caught; anything else is a
 * genuine bug and propagates.
 */
object ExternalLinks {
    fun open(context: Context, url: String): Boolean {
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (e: ActivityNotFoundException) {
            notifyFailure(context)
            false
        } catch (e: SecurityException) {
            notifyFailure(context)
            false
        }
    }

    private fun notifyFailure(context: Context) {
        Toast.makeText(context, R.string.open_link_failed, Toast.LENGTH_SHORT).show()
    }
}
