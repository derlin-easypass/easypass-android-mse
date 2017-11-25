package ch.derlin.easypass.easypass.helper

import android.os.Build
import android.text.Html
import android.text.Spanned

/**
 * Created by Lin on 25.11.17.
 */

object MiscUtils {

    fun toSpannable(str: String, vararg args: String): Spanned? {
        val content = if (args.size > 0) str.format(args) else str
        if (Build.VERSION.SDK_INT >= 24) {
            return Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY)
        } else {
            return Html.fromHtml(content)
        }
    }
}