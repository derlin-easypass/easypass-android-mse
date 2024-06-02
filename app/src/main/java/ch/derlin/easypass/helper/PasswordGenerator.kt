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
    private const val NORMAL_CHARS =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /** The default list of special characters optionally used for generation */
    const val ALL_SPECIAL_CHARS = ".-_,;<>/+*ç%&/()=?'[]{}@#¬"

    /** The random generator */
    private val random = Random(System.currentTimeMillis())

    /**
     * @param size the length of the password to generate
     * @param useSpecialChars whether or not to include special chars in the password
     * @param if [useSpecialChars] is set, specify an alternate list of special chars
     *      (default to [allSpecialChars])
     *
     * @return a randomly generated password
     */
    fun generate(
        size: Int,
        useSpecialChars: Boolean,
        specialChars: String = ALL_SPECIAL_CHARS
    ): String {
        var chars = NORMAL_CHARS
        if (useSpecialChars) chars += specialChars

        return IntRange(0, size)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }
}