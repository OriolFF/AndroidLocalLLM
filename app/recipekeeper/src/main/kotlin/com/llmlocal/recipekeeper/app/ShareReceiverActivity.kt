package com.llmlocal.recipekeeper.app

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle

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
        if (parsed != null) startActivity(forwardIntent(intent, this))
        finish()
    }

    private companion object {
        /**
         * A copy of [source] aimed at [MainActivity]. Some senders put the
         * pictures only in `EXTRA_STREAM`; grants travel with `ClipData`, so the
         * stream URIs are re-attached to it.
         */
        @Suppress("DEPRECATION")
        fun forwardIntent(source: Intent, activity: Activity): Intent {
            val forward = Intent(source).setClass(activity, MainActivity::class.java)
            forward.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            if (source.clipData == null) {
                val uris: List<Uri> = if (source.action == Intent.ACTION_SEND_MULTIPLE) {
                    (if (Build.VERSION.SDK_INT >= 33) {
                        source.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        source.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    }).orEmpty()
                } else {
                    listOfNotNull(
                        if (Build.VERSION.SDK_INT >= 33) {
                            source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        } else {
                            source.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        },
                    )
                }
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
