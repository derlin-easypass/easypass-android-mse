package ch.derlin.easypass.helper

import android.app.Activity
import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.ColorInt
import ch.derlin.easypass.IntroActivity


/**
 * This object contains various utilities and extensions reused throughout the app.
 *
 * date 25.11.17
 * @author Lucy Linder
 */

object MiscUtils {

    fun Activity.copyToClipBoard(text: String?): Boolean {
        if (text == null) return false
        ClipData.newPlainText("easypass", text).let { clipData ->
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                clipData
            )
        }
        return true
    }

    /** Convert an HTML string into a [Spanned] that can be used in a [TextView] */
    fun String.toSpannable(): Spanned? {
        return if (Build.VERSION.SDK_INT >= 24) {
            Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(this)
        }
    }

    /** Colorize digits and special chars in a password */
    fun String.colorizePassword(
        @ColorInt digitsColor: Int = Color.parseColor("#EA4865"),
        @ColorInt symbolsColor: Int = Color.parseColor("#03A9F4")
    ): Spannable =
        SpannableStringBuilder(this).also { ssb ->
            this.withIndex().forEach {
                if (it.value.isDigit() || !it.value.isLetter()) {
                    val color = if (it.value.isDigit()) digitsColor else symbolsColor
                    ssb.setSpan(
                        ForegroundColorSpan(color),
                        it.index,
                        it.index + 1,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                }
            }
        }

    fun String._colorizePassword(): Spanned? = // TODO: don't hardcode colors
        this.splitByCharacterClass().joinToString("") {
            val c = it.first()
            when {
                c.isLetter() -> it
                c.isDigit() -> "<font color=\"#EA4865\">$it</font>"
                else -> "<font color=\"#03A9F4\">$it</font>"
            }
        }.toSpannable()


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
        val pendingIntent = PendingIntent.getActivity(
            this, 123456, startIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
    fun Activity.rootView(): View = this.findViewById(android.R.id.content)
    fun Dialog.rootView(): View = this.findViewById(android.R.id.content)

    /** Resolve an attribute color, for example [android.R.attrcolorAccent] */
    fun Activity.attrColor(resourceId: Int): Int {
        // see https://stackoverflow.com/a/27611244/2667536
        val typedValue = TypedValue()
        val a = obtainStyledAttributes(typedValue.data, intArrayOf(resourceId))
        val color = a.getColor(0, 0)
        a.recycle()
        return color
    }

    /** Split a string by character class: groups are consecutive letters, digits, or other  */
    fun String.splitByCharacterClass(): List<String> {
        fun Char.typ() = if (!isLetterOrDigit()) 2 else if (isDigit()) 1 else 0
        return this.toList().splitConsecutiveBy { c, c2 ->
            c.typ() == c2.typ()
        }.map { it.joinToString("") }
    }

    /**
     * Group consecutive items based on a predicate.
     * The predicate receives two items, and should return whether or not they belong to the same group.
     */
    fun <T> Iterable<T>.splitConsecutiveBy(predicate: (T, T) -> Boolean): List<List<T>> {
        var leftover = this.toList()
        val groups = mutableListOf<List<T>>()

        while (leftover.isNotEmpty()) {
            val first = leftover.first()
            val newGroup = leftover.takeWhile { predicate(first, it) }
            groups += newGroup
            leftover = leftover.drop(newGroup.size)
        }
        return groups
    }
}