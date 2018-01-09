package ch.derlin.easypass.helper

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import timber.log.Timber
import java.nio.charset.Charset
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * Created by Lin on 26.11.17.
 */

object CachedCredentials {

    val keystoreName = "AndroidKeyStore"
    val keyName = "key"

    /** how long the auth will be valid. > 0 to be able to use it ! */
    val authenticationValiditySeconds: Int = 30

    // -----------------------------------------

    /** The encryption to use for storing credentials */
    val transformation: String
        get() = (KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7)

    /** The charset to encode the credentials */
    val charset: Charset
        get() = Charsets.UTF_8

    /** returns true if a password is currently stored */
    val isPasswordCached: Boolean
        get() = prefs.cachedPassword != null

    val prefs: Preferences
        get() = Preferences()

    // -----------------------------------------

    /**
     * Store a password securely using the default authentication mechanism of the phone.
     * It can be pattern, password or fingerprint.
     *
     * @throws UserNotAuthenticatedException if the keyguard hasn't been unlocked for a while
     * In this case, you need to start a new activity using [KeyguardManager.createConfirmDeviceCredentialIntent].
     * (see [getAuthenticationIntent]) and then call this method again in the [Activity.onActivityResult]
     * (if the result is a success).
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
            prefs.cachedPassword = "%s,%s".format(
                    Base64.encodeToString(cipher.iv, Base64.DEFAULT),
                    Base64.encodeToString(encryptedPassword, Base64.DEFAULT))

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
     * In this case, you need to start a new activity using [KeyguardManager.createConfirmDeviceCredentialIntent].
     * (see [getAuthenticationIntent]) and then call this method again in the [Activity.onActivityResult]
     * (if the result is a success).
     *
     * @throws Exception if there is no cached password. Use [isPasswordCached] beforehand to
     * avoid this error.
     * @throws UserNotAuthenticatedException if the keyguard hasn't been unlocked for a while
     * In this case, you need to start a new activity using [KeyguardManager.createConfirmDeviceCredentialIntent].
     * (see [getAuthenticationIntent]) and then call this method again in the [Activity.onActivityResult]
     * (if the result is a success).
     * @throws KeyPermanentlyInvalidatedException if the lockscreen security has changed (either a
     * new lock screen -> ask for password again) or no security (-> can't store password anymore)
     * @throws RuntimeException for any other exception
     */
    @Throws(UserNotAuthenticatedException::class, KeyPermanentlyInvalidatedException::class, RuntimeException::class)
    fun getPassword(): String {
        try {
            val base64Content = Preferences().cachedPassword
            if (base64Content == null) throw Exception("No password cached.")

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
        prefs.cachedPassword = null
    }

    /**
     * Get the intent to use in order to show an authentication screen.
     * Once the intent created, use [Activity.startActivityForResult].
     *
     * @param ctx the activity context
     * @param requestCode the code used to identify the request in the [Activity.onActivityResult]
     * @param title the title in the authentication screen
     * @param description the description in the authentication screen
     */
    fun getAuthenticationIntent(ctx: Context, requestCode: Int, title: String? = null, description: String? = null): Intent? {
        val keyguardManager = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.createConfirmDeviceCredentialIntent(null, null)
    }

    // -----------------------------------------

    // get and initialise the keystore
    private fun getKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(keystoreName)
        keyStore.load(null)
        return keyStore
    }

    // load an existing key from the keystore
    private fun getKey(): SecretKey? = getKeyStore().getKey(keyName, null) as SecretKey?

    // delete the key. Should be called in case of [KeyPermanentlyInvalidatedException]
    private fun deleteKey() {
        prefs.cachedPassword = null
        getKeyStore().deleteEntry(keyName)
        prefs.keysoreInitialised = false
        Timber.d("key deleted.")
    }

    // create a new key. Should be called only once, hence the [Preferences.keysoreInitialised] flag.
    private fun createKey(): SecretKey {
        try {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keystoreName)
            keyGenerator.init(KeyGenParameterSpec.Builder(keyName,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(authenticationValiditySeconds)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build())
            prefs.keysoreInitialised = true
            Timber.d("key created.")
            return keyGenerator.generateKey()
        } catch (e: Exception) {
            throw RuntimeException("Failed to create a symmetric key", e)
        }

    }

}