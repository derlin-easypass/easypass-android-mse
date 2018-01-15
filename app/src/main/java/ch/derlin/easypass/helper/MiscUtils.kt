package ch.derlin.easypass.helper

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.app.Fragment
import android.text.Html
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import ch.derlin.easypass.IntroActivity


/**
 * This object contains various utilities and extensions reused throughout the app.
 *
 * date 25.11.17
 * @author Lucy Linder
 */

object MiscUtils {

    /** Convert an HTML string into a [Spanned] that can be used in a [TextView] */
    fun toSpannable(str: String, vararg args: String): Spanned? {
        val content = if (args.size > 0) str.format(args) else str
        if (Build.VERSION.SDK_INT >= 24) {
            return Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY)
        } else {
            return Html.fromHtml(content)
        }
    }

    /** Hide the soft keyboard */
    fun Activity.hideKeyboard() {
        /*
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (inputMethodManager.isAcceptingText)
            inputMethodManager.hideSoftInputFromWindow(this.currentFocus.windowToken, /*flags:*/ 0)
        */
        val v = window.currentFocus
        if (v != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    /** Restart the whole application */
    fun Activity.restartApp() {
        // see https://stackoverflow.com/questions/17795189/how-to-programmatically-force-a-full-app-restart-e-g-kill-then-start
        val startIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 123456, startIntent,
                PendingIntent.FLAG_CANCEL_CURRENT)

        (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .set(AlarmManager.RTC, System.currentTimeMillis() + 10, pendingIntent)

        finish()
        //System.exit(0)
        //android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** Launch the introduction slides activity using the [IntroActivity.INTENT_INTRO] request code */
    fun Activity.showIntro() {
        val intent = Intent(this, IntroActivity::class.java)
        // intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
        startActivityForResult(intent, IntroActivity.INTENT_INTRO)
    }

    /** Get the activity root view, for example to create a snackbar */
    fun Activity.rootView(): View = findViewById(android.R.id.content)

    /** Resolve an attribute color, for example [android.R.attrcolorAccent] */
    fun Activity.attrColor(resourceId: Int): Int {
        // see https://stackoverflow.com/a/27611244/2667536
        val typedValue = TypedValue()
        val a = obtainStyledAttributes(typedValue.data, intArrayOf(resourceId))
        val color = a.getColor(0, 0)
        a.recycle()
        return color
    }
}