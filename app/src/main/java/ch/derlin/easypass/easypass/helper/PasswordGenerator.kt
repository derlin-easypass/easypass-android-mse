package ch.derlin.easypass.easypass.helper

import android.util.Range
import java.util.*

/**
 * Created by Lin on 13.12.17.
 */

object PasswordGenerator {

    val normalChars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val specialChars = normalChars + ".-_,;<>/+*ç%&/()=?'[]{}@#¬"
    val random = Random(System.currentTimeMillis())

    fun generate(size: Int, useSpecialChars: Boolean): String {
        val chars = if (useSpecialChars) specialChars else normalChars
        return IntRange(0, size)
                .map { chars[random.nextInt(chars.length)] }
                .joinToString("")
    }
}