package ch.derlin.easypass.helper

import java.util.*

/**
 * Created by Lin on 13.12.17.
 */

object PasswordGenerator {

    val normalChars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val allSpecialChars = ".-_,;<>/+*ç%&/()=?'[]{}@#¬"
    val random = Random(System.currentTimeMillis())

    fun generate(size: Int, useSpecialChars: Boolean, specialChars: String = allSpecialChars): String {
        var chars = normalChars;
        if (useSpecialChars) chars += specialChars

        return IntRange(0, size)
                .map { chars[random.nextInt(chars.length)] }
                .joinToString("")
    }
}