package ch.derlin.easypass.data

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption compatible with the OpenSSL CLI:
 *
 *     openssl enc -aes-256-cbc -pbkdf2 -iter 210000 -md sha512 -salt -base64
 *
 * Format: base64("Salted__" + 8-byte salt + ciphertext), wrapped at 64 columns.
 * Key (32 bytes) and IV (16 bytes) = PBKDF2-HMAC-SHA512(UTF-8 password, salt, iterations, 48 bytes).
 *
 * date: 28.09.2026
 * @author Lucy Linder
 */
object OpenSSLCrypto {

    /** Not stored in the file: the app, easycmd and the OpenSSL command must use the same value. */
    const val PBKDF2_ITERATIONS = 210_000

    private const val KDF = "PBKDF2WithHmacSHA512"
    private const val CIPHER = "AES/CBC/PKCS5Padding"
    private const val KEY_LEN = 32
    private const val IV_LEN = 16
    private const val SALT_LEN = 8
    private const val LINE_LEN = 64
    private val MAGIC = "Salted__".toByteArray(Charsets.US_ASCII)

    private val random = SecureRandom()

    /**
     * Encrypt [plaintext] with a random salt.
     * @return the base64 text, wrapped at 64 columns and ending with a newline
     */
    fun encrypt(password: String, plaintext: ByteArray): ByteArray {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val encrypted = crypt(Cipher.ENCRYPT_MODE, password, salt, plaintext, 0, plaintext.size)
        val base64 = Base64.getMimeEncoder(LINE_LEN, "\n".toByteArray())
            .encode(MAGIC + salt + encrypted)
        return base64 + '\n'.code.toByte()
    }

    /**
     * Decrypt base64 [data] as written by [encrypt] or the OpenSSL CLI. Whitespace is ignored.
     * @throws GeneralSecurityException if the data is not in the expected format,
     * or the password is wrong (in most cases, see [JsonManager.deserialize])
     */
    fun decrypt(password: String, data: ByteArray): ByteArray {
        val raw = try {
            Base64.getDecoder().decode(data.filterNot { it.toInt().toChar().isWhitespace() }.toByteArray())
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException("invalid base64", e)
        }
        val headerLen = MAGIC.size + SALT_LEN
        if (raw.size < headerLen || !raw.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw GeneralSecurityException("missing Salted__ header")
        }
        val salt = raw.copyOfRange(MAGIC.size, headerLen)
        return crypt(Cipher.DECRYPT_MODE, password, salt, raw, headerLen, raw.size - headerLen)
    }

    private fun crypt(
        mode: Int, password: String, salt: ByteArray,
        input: ByteArray, offset: Int, length: Int
    ): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, (KEY_LEN + IV_LEN) * 8)
        val keyIv = try {
            SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
        try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                mode,
                SecretKeySpec(keyIv, 0, KEY_LEN, "AES"),
                IvParameterSpec(keyIv, KEY_LEN, IV_LEN)
            )
            return cipher.doFinal(input, offset, length)
        } finally {
            keyIv.fill(0)
        }
    }
}
