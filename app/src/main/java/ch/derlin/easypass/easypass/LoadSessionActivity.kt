package ch.derlin.easypass.easypass

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.support.design.widget.TextInputEditText
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import ch.derlin.easypass.easypass.dropbox.DbxBroadcastReceiver
import ch.derlin.easypass.easypass.dropbox.DbxService
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class LoadSessionActivity : AppCompatActivity() {

    private var mCurrentFragment: Fragment? = null


    // -----------------------------------------

    private val mBroadcastReceiver = object : DbxBroadcastReceiver() {
        override fun onMetaFetched() {
            switchFragments(PasswordFragment())
        }

        override fun onSessionOpened() {
            // service up and running, start the actual app
            val intent = Intent(this@LoadSessionActivity, AccountListActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
            startActivity(intent)
            finish()
        }

        override fun onError(msg: String) {
            Toast.makeText(this@LoadSessionActivity, "error: " + msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // mandatory to do this here as well, since onActivityResult is called before
        // onResume (and the DbxService might be called inside onActivityResult, see
        // the PasswordFragment)
        mBroadcastReceiver.registerSelf(this)
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        mBroadcastReceiver.registerSelf(this)
        super.onResume()
    }


    override fun onPause() {
        mBroadcastReceiver.unregisterSelf(this)
        super.onPause()
    }

    // -----------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_load_session)
        DbxService.instance.getSessionMetadata()
        switchFragments(ProgressFragment())
    }

    // -----------------------------------------

    private fun switchFragments(f: Fragment) {
        // Execute a transaction, replacing any existing fragment
        // with this one inside the frame.
        mCurrentFragment = f
        f.setRetainInstance(true)
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.load_session_fragment_layout, f)
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        ft.commitAllowingStateLoss()
    }

    // -----------------------------------------

    class ProgressFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            super.onCreateView(inflater, container, savedInstanceState)
            return inflater!!.inflate(R.layout.fragment_load_session_meta, container, false)
        }
    }

    // -----------------------------------------

    class PasswordFragment : Fragment() {

        private lateinit var mKeyguardManager: KeyguardManager
        private lateinit var mSharedPreferences: SharedPreferences
        private lateinit var mPasswordField: TextInputEditText
        private var mPassword: String? = null

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            super.onCreateView(inflater, container, savedInstanceState)
            val v = inflater!!.inflate(R.layout.fragment_enter_password, container, false)

            // setup auth
            mSharedPreferences = activity.getSharedPreferences(STORAGE_FILE_NAME, Activity.MODE_PRIVATE)
            mKeyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

            val checkbox = v.findViewById<CheckBox>(R.id.remember_me_checkbox)
            mPasswordField = v.findViewById<TextInputEditText>(R.id.password_field)

            // setup callbacks
            v.findViewById<Button>(R.id.login_button).setOnClickListener({ v ->
                mPassword = mPasswordField.text.toString()
                if (checkbox.isChecked) {
                    savePassword()
                } else {
                    decryptSession()
                }
            })

            // load login data from shared preferences (
            // only the mPassword is encrypted, IV used for the encryption is loaded from shared preferences
            if (mSharedPreferences.contains(PREFS_PASSWORD_KEY)) {
                getPasswordsFromFingerprint()
            }

            return v
        }

        fun decryptSession() {
            DbxService.instance.openSession(mPassword!!)
        }


        fun savePassword() {
            try {
                // encrypt the mPassword
                //val secretKey = createKey()
                var secretKey = getKey()
                if (secretKey == null) secretKey = createKey()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val encryptionIv = cipher.iv
                val passwordBytes = mPassword!!.toByteArray(CHARSET)
                val encryptedPasswordBytes = cipher.doFinal(passwordBytes)
                val encryptedPassword = Base64.encodeToString(encryptedPasswordBytes, Base64.DEFAULT)

                mSharedPreferences
                        .edit()
                        .putString(PREFS_PASSWORD_KEY, Base64.encodeToString(encryptionIv, Base64.DEFAULT) + "," + encryptedPassword)
                        .apply()

                decryptSession()

            } catch (e: UserNotAuthenticatedException) {
                showAuthenticationScreen(SAVE_CREDENTIALS_REQUEST_CODE)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        fun getPasswordsFromFingerprint() {
            try {
                val base64Content = mSharedPreferences.getString(PREFS_PASSWORD_KEY, null)
                if (base64Content == null) {
                    Toast.makeText(activity, "You must first store credentials.", Toast.LENGTH_SHORT).show()
                    return
                }

                val (base64iv, base64password) = base64Content.split(",")
                val encryptionIv = Base64.decode(base64iv, Base64.DEFAULT)
                val encryptedContent = Base64.decode(base64password, Base64.DEFAULT)

                // decrypt the content
                val secretKey = getKey()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(encryptionIv))
                val contentBytes = cipher.doFinal(encryptedContent)

                mPassword = String(contentBytes, CHARSET)
                mPasswordField.setText(mPassword)
                decryptSession()

            } catch (e: UserNotAuthenticatedException) {
                showAuthenticationScreen(LOGIN_WITH_CREDENTIALS_REQUEST_CODE)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        private fun getKey(): SecretKey? {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            return keyStore.getKey(KEY_NAME, null) as SecretKey?
        }

        private fun showAuthenticationScreen(requestCode: Int) {
            val intent = mKeyguardManager.createConfirmDeviceCredentialIntent(null, null)
            if (intent != null) {
                startActivityForResult(intent, requestCode)
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
            if (resultCode == Activity.RESULT_OK) {
                if (requestCode == SAVE_CREDENTIALS_REQUEST_CODE) {
                    savePassword()
                } else if (requestCode == LOGIN_WITH_CREDENTIALS_REQUEST_CODE) {
                    getPasswordsFromFingerprint()
                }
            } else {
                Toast.makeText(activity, "Confirming credentials failed", Toast.LENGTH_SHORT).show()
            }
        }

        private fun createKey(): SecretKey {
            try {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
                keyGenerator.init(KeyGenParameterSpec.Builder(KEY_NAME,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationValidityDurationSeconds(AUTHENTICATION_DURATION_SECONDS)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .build())
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                throw RuntimeException("Failed to create a symmetric key", e)
            }

        }
        // -----------------------------------------


        companion object {

            val SAVE_CREDENTIALS_REQUEST_CODE = 1
            val LOGIN_WITH_CREDENTIALS_REQUEST_CODE = 2
            val AUTHENTICATION_DURATION_SECONDS = 30

            val CHARSET = Charsets.UTF_8

            val ANDROID_KEY_STORE = "AndroidKeyStore"
            val KEY_NAME = "key"
            val STORAGE_FILE_NAME = "credentials"
            val TRANSFORMATION = (KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7)

            val PREFS_PASSWORD_KEY = "default_pass"

        }
    }

}
