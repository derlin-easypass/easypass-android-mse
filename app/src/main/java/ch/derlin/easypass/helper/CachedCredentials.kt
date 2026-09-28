package ch.derlin.easypass.helper

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import ch.derlin.easypass.easypass.R
import timber.log.Timber
import java.nio.charset.Charset
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * This object contains all the methods needed to cache a password safely into the
 * preferences.
 *
 * The password is encrypted using the AES algorithm. The AES key is stored in the
 * Android keystore and secured by the Android keyguard. The user will need to use
 * his fingerprints or his pattern to unlock the key.
 *
 * date 26.11.17
 * @author Lucy Linder
 */

object CachedCredentials {

    /** The name of the keystore used. */
    private const val KEYSTORE_NAME = "AndroidKeyStore"

    /** The name of the AES key used. */
    private const val KEY_NAME = "key"

    /** how long the key can be used after authentication. > 0 to be able to use it ! */
    private const val AUTHENTICATION_VALIDITY_SECONDS: Int = 30

    // -----------------------------------------

    /** The encryption to use for storing credentials */
    private val transformation: String
        get() = (KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7)

    /** The charset to encode the credentials */
    private val charset: Charset = Charsets.UTF_8

    /** returns true if a password is currently stored */
    val isPasswordCached: Boolean
        get() = Preferences.cachedPassword != null

    // -----------------------------------------

    /**
     * Store a password securely using the default authentication mechanism of the phone.
     * It can be pattern, password or fingerprint.
     *
     * @throws UserNotAuthenticatedException if the keyguard hasn't been unlocked for a while
     * In this case, call [authenticate] and then call this method again on success.
     */
    @Throws(UserNotAuthenticatedException::class, RuntimeException::class)
    fun savePassword(password: String) {

        try {
            var secretKey = getKey()
            if (secretKey == null) secretKey = createKey() // create key only once
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedPassword = cipher.doFinal(password.toByteArray(charset))

            // save both password and IV
            Preferences.cachedPassword = "%s,%s".format(
                Base64.encodeToString(cipher.iv, Base64.DEFAULT),
                Base64.encodeToString(encryptedPassword, Base64.DEFAULT)
            )

        } catch (e: Exception) {
            Timber.d(e)
            when (e) {
                // --> showAuthenticationScreen(SAVE_CREDENTIALS_REQUEST_CODE)
                is UserNotAuthenticatedException -> throw e
                is KeyPermanentlyInvalidatedException -> {
                    // the screen lock has changed
                    deleteKey()
                    savePassword(password) // try again, it should create the key this time
                }

                else -> throw RuntimeException(e)
            }
        }
    }

    /**
     * Read a cached password stored securely.
     *
     * @throws UserNotAuthenticatedException if the keyguard hasn't been unlocked for a while
     * In this case, call [authenticate] and then call this method again on success.
     *
     * @throws Exception if there is no cached password. Use [isPasswordCached] beforehand to
     * avoid this error.
     * @throws UserNotAuthenticatedException if the keyguard hasn't been unlocked for a while
     * In this case, call [authenticate] and then call this method again on success.
     * @throws KeyPermanentlyInvalidatedException if the lockscreen security has changed (either a
     * new lock screen -> ask for password again) or no security (-> can't store password anymore)
     * @throws RuntimeException for any other exception
     */
    @Throws(
        UserNotAuthenticatedException::class,
        KeyPermanentlyInvalidatedException::class,
        RuntimeException::class
    )
    fun getPassword(): String {
        try {
            val base64Content = Preferences.cachedPassword ?: throw Exception("No password cached.")

            val (base64iv, base64password) = base64Content.split(",")
            val encryptionIv = Base64.decode(base64iv, Base64.DEFAULT)
            val encryptedContent = Base64.decode(base64password, Base64.DEFAULT)

            // decrypt the content
            val secretKey = getKey()
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(encryptionIv))
            val contentBytes = cipher.doFinal(encryptedContent)

            return String(contentBytes, charset)

        } catch (e: Exception) {
            when (e) {
                // --> showAuthenticationScreen(LOGIN_WITH_CREDENTIALS_REQUEST_CODE)
                is UserNotAuthenticatedException -> throw e
                // --> authentication disabled or changed, i.e. fingerprint added, pattern changed...
                is KeyPermanentlyInvalidatedException -> deleteKey()
                is InvalidKeyException -> deleteKey()
                // --> default
                else -> throw RuntimeException(e)
            }
            Timber.d(e)
            throw e
        }
    }

    /**
     * Delete the cached credentials.
     */
    fun clearPassword() {
        Preferences.cachedPassword = null
    }

    /**
     * Show an authentication screen (fingerprint or screen lock). A success unlocks the key
     * for [AUTHENTICATION_VALIDITY_SECONDS].
     *
     * @param ctx the activity context
     * @param onResult called on the main thread with true if the authentication succeeded
     * @return false if the device has no screen lock (nothing is shown)
     */
    fun authenticate(ctx: Context, onResult: (Boolean) -> Unit): Boolean {
        val keyguardManager = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguardManager.isDeviceSecure) return false

        BiometricPrompt.Builder(ctx)
            .setTitle(ctx.getString(R.string.app_name))
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
            .authenticate(
                CancellationSignal(),
                ctx.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) =
                        onResult(true)

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) =
                        onResult(false)
                })
        return true
    }

    // -----------------------------------------

    // get and initialise the keystore
    private fun getKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(KEYSTORE_NAME)
        keyStore.load(null)
        return keyStore
    }

    // load an existing key from the keystore
    private fun getKey(): SecretKey? = getKeyStore().getKey(KEY_NAME, null) as SecretKey?

    // delete the key. Should be called in case of [KeyPermanentlyInvalidatedException]
    private fun deleteKey() {
        Preferences.cachedPassword = null
        getKeyStore().deleteEntry(KEY_NAME)
        Preferences.keystoreInitialised = false
        Timber.d("key deleted.")
    }

    // create a new key. Should be called only once, hence the [Preferences.keysoreInitialised] flag.
    private fun createKey(): SecretKey {
        try {
            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(
                        AUTHENTICATION_VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build()
            )
            Preferences.keystoreInitialised = true
            Timber.d("key created.")
            return keyGenerator.generateKey()
        } catch (e: Exception) {
            throw RuntimeException("Failed to create a symmetric key", e)
        }

    }

}