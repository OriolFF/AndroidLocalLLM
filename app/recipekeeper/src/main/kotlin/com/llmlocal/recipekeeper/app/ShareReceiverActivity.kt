package com.llmlocal.recipekeeper.app

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Target of the system share sheet (pictures and links). Has no UI: it checks
 * that the share is something the app understands, forwards it to
 * [MainActivity] with the read grants attached and finishes. Declared
 * `noHistory` + `excludeFromRecents` + a translucent theme so it never shows.
 */
class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parsed = ShareIntentParser.parse(intent)
        try {
            if (parsed != null) startActivity(forwardIntent(intent, this))
        } catch (e: SecurityException) {
            // The sender attached a URI it never granted us (I4 QA P2-1): forwarding it
            // with a read grant throws. Dropping the share beats taking the process down
            // (an in-flight extraction would die with it).
            Log.w(TAG, "Share dropped: sender did not grant its URIs", e)
        } finally {
            finish()
        }
    }

    private companion object {
        const val TAG = "ShareReceiver"

        /**
         * A copy of [source] aimed at [MainActivity]. Some senders put the
         * pictures only in `EXTRA_STREAM`; grants travel with `ClipData`, so the
         * stream URIs are re-attached to it.
         */
        fun forwardIntent(source: Intent, activity: Activity): Intent {
            val forward = Intent(source).setClass(activity, MainActivity::class.java)
            forward.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            if (source.clipData == null) {
                val uris: List<Uri> = ShareIntentParser.streamUris(source)
                if (uris.isNotEmpty()) {
                    forward.clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
                        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                    }
                }
            }
            return forward
        }
    }
}
