package ch.derlin.easypass.easypass

import android.app.Activity
import android.app.KeyguardManager
import android.content.*
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.support.design.widget.TextInputEditText
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.text.Editable
import android.text.TextWatcher
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

    interface LoadSessionFragment {
        fun onFail(msg: String)
    }

    private var mIsAuthenticating = false
    private var mCurrentFragment: LoadSessionFragment? = null
    // ----------------------------------------- Service callbacks

    // each fragment will do one of those things:
    // - fetching metadata
    // - opening a session
    // Thus, this is the way of "communicating" with the fragments and know the
    // overall state of the activity
    private val mBroadcastReceiver = object : DbxBroadcastReceiver() {
        /** Triggered by the [ProgressFragment] */
        override fun onMetaFetched() {
            switchFragments(PasswordFragment())
        }

        /** Triggered by the [PasswordFragment] */
        override fun onSessionOpened() {
            // service up and running, start the actual app
            val intent = Intent(this@LoadSessionActivity, AccountListActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
            startActivity(intent)
            finish()
        }

        override fun onError(msg: String) {
            if(mCurrentFragment != null){
                mCurrentFragment!!.onFail(msg)
            }else {
                Toast.makeText(this@LoadSessionActivity, "error: " + msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ----------------------------------------- Manage the Dropbox service

    // this allows us to detect when the service is up.
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mIsAuthenticating = true
            if (DbxService.instance!!.startAuth()) {
                // returns true only if already authenticated --> start fetching metadata !
                mIsAuthenticating = false
                switchFragments(ProgressFragment())
            } else {
                // else, a dbx activity will be launched --> see the on resume
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // this is not used at all...
        }
    }

    override fun onStart() {
        super.onStart()
        // start the dropbox service
        startService(Intent(applicationContext, DbxService::class.java))
        bindService(Intent(applicationContext, DbxService::class.java), //
                mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        // destroy the bind
        unbindService(mServiceConnection)
        super.onDestroy()
    }

    override fun onPause() {
        // stop receiving local broadcasts
        mBroadcastReceiver.unregisterSelf(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        // receive local broadcasts
        mBroadcastReceiver.registerSelf(this)

        if (mIsAuthenticating) {
            // it was the first time the app was launched on this device
            // and the dropbox authentication succeeded
            DbxService.instance.finishAuth()
            mIsAuthenticating = false
            // next !
            switchFragments(ProgressFragment())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // mandatory to do this here as well, since onActivityResult is called before
        // onResume (and the DbxService might be called inside onActivityResult, see
        // the PasswordFragment)
        mBroadcastReceiver.registerSelf(this)
    }

    // ----------------------------------------- Activity stuff

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_load_session)
    }


    private fun switchFragments(f: LoadSessionFragment) {
        // Execute a transaction, replacing any existing fragment
        // with this one inside the frame.
        (f as Fragment).setRetainInstance(true)
        mCurrentFragment = f
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.load_session_fragment_layout, f)
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        ft.commitAllowingStateLoss()
    }

    // ----------------------------------------- Metadata fetching Fragment

    class ProgressFragment : Fragment(), LoadSessionFragment {
        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            super.onCreateView(inflater, container, savedInstanceState)
            DbxService.instance.getSessionMetadata()
            return inflater!!.inflate(R.layout.fragment_load_session_meta, container, false)
        }

        override fun onFail(msg: String) {
            // TODO
        }
    }

    // ----------------------------------------- Credentials Fragment

    class PasswordFragment : Fragment(), LoadSessionFragment {

        private lateinit var mKeyguardManager: KeyguardManager
        private lateinit var mSharedPreferences: SharedPreferences
        private lateinit var mPasswordField: TextInputEditText
        private lateinit var mLoginButton: Button
        private var mPassword: String? = null

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            super.onCreateView(inflater, container, savedInstanceState)
            val v = inflater!!.inflate(R.layout.fragment_enter_password, container, false)

            // setup auth
            mSharedPreferences = activity.getSharedPreferences(STORAGE_FILE_NAME, Activity.MODE_PRIVATE)
            mKeyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

            // fetch views
            val checkbox = v.findViewById<CheckBox>(R.id.remember_me_checkbox)
            if(!mKeyguardManager.isKeyguardSecure){
                // no way to save the password if the device doesn't have a pin
                checkbox.isEnabled = false
            }
            mLoginButton = v.findViewById<Button>(R.id.login_button)
            mPasswordField = v.findViewById<TextInputEditText>(R.id.password_field)

            // register btn callback
            mLoginButton.setOnClickListener({ v ->
                mPassword = mPasswordField.text.toString()
                if (checkbox.isChecked) {
                    mSharedPreferences.edit().clear().apply()
                    savePasswordAndDecrypt()
                } else {
                   decryptSession()
                }
            })


            // toggle button to avoid empty passwords
            mPasswordField.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                    mLoginButton.isEnabled = mPasswordField.text.length >= MIN_PASSWORD_LENGTH
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            })

            // load login data from shared preferences
            // only the password is encrypted, IV used for the encryption is loaded from shared preferences
            if (mSharedPreferences.contains(PREFS_PASSWORD_KEY)) {
                getPasswordsFromFingerprint()
            }

            return v
        }

        override fun onFail(msg: String) {
            // TODO
            // remove wrong credentials
            mSharedPreferences.edit().remove(PREFS_PASSWORD_KEY).apply()
            mLoginButton.isEnabled = false
            Toast.makeText(activity, "Wrong credentials", Toast.LENGTH_SHORT).show()
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (resultCode == Activity.RESULT_OK) {
                if (requestCode == SAVE_CREDENTIALS_REQUEST_CODE) {
                    savePasswordAndDecrypt()
                } else if (requestCode == LOGIN_WITH_CREDENTIALS_REQUEST_CODE) {
                    getPasswordsFromFingerprint()
                }
            } else {
                Toast.makeText(activity, "Confirming credentials failed", Toast.LENGTH_SHORT).show()
            }
        }

        private fun decryptSession() {
                DbxService.instance.openSession(mPassword!!)
        }

        fun savePasswordAndDecrypt() {
            try {
                var secretKey = getKey()
                if (secretKey == null) secretKey = createKey() // create key only once
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val encryptedPassword = cipher.doFinal(mPassword!!.toByteArray(CHARSET))

                // save both password and IV for decrypt later
                val prefValue = "%s,%s".format(
                        Base64.encodeToString(cipher.iv, Base64.DEFAULT),
                        Base64.encodeToString(encryptedPassword, Base64.DEFAULT))

                // finally, save everything
                mSharedPreferences
                        .edit()
                        .putString(PREFS_PASSWORD_KEY, prefValue)
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
                DbxService.instance.openSession(mPassword!!)

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
                mSharedPreferences.edit().putBoolean(PREFS_KEYSTORE_INITIALISED, true).apply()
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                throw RuntimeException("Failed to create a symmetric key", e)
            }

        }
        // -----------------------------------------


        companion object {

            val SAVE_CREDENTIALS_REQUEST_CODE = 1
            val LOGIN_WITH_CREDENTIALS_REQUEST_CODE = 2
            val AUTHENTICATION_DURATION_SECONDS = 5 * 60

            val CHARSET = Charsets.UTF_8

            val ANDROID_KEY_STORE = "AndroidKeyStore"
            val KEY_NAME = "key"
            val STORAGE_FILE_NAME = "credentials"
            val TRANSFORMATION = (KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7)

            val PREFS_PASSWORD_KEY = "default_pass"
            val PREFS_KEYSTORE_INITIALISED = "keystore_initialised"

            val MIN_PASSWORD_LENGTH = 3
        }
    }

}