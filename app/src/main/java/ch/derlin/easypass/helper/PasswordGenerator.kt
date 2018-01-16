package ch.derlin.easypass.helper

import java.util.*

/**
 * This object lets you generate new passwords with a single method.
 *
 * date 13.12.17
 * @author Lucy Linder
 */

object PasswordGenerator {

    /** The list of alpha numeric characters always used for generation */
    val normalChars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    /** The default list of special characters optionally used for generation */
    val allSpecialChars = ".-_,;<>/+*ç%&/()=?'[]{}@#¬"
    /** The random generator */
    val random = Random(System.currentTimeMillis())

    /**
     * @param size the length of the password to generate
     * @param useSpecialChars whether or not to include special chars in the password
     * @param if [useSpecialChars] is set, specify an alternate list of special chars
     *      (default to [allSpecialChars])
     *
     * @return a randomly generated password
     */
    fun generate(size: Int, useSpecialChars: Boolean, specialChars: String = allSpecialChars): String {
        var chars = normalChars;
        if (useSpecialChars) chars += specialChars

        return IntRange(0, size)
                .map { chars[random.nextInt(chars.length)] }
                .joinToString("")
    }
}