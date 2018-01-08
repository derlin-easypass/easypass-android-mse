package ch.derlin.easypass

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.data.JsonManager
import ch.derlin.easypass.helper.CachedCredentials
import ch.derlin.easypass.helper.DbxManager
import ch.derlin.easypass.helper.Preferences
import kotlinx.android.synthetic.main.fragment_enter_password.*
import kotlinx.android.synthetic.main.fragment_load_session_meta.*
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import ch.derlin.easypass.helper.SelectFileDialog.createSelectFileDialog

class LoadSessionActivity : AppCompatActivity() {

    private var mCurrentFragment: Fragment? = null

    // ----------------------------------------- Service callbacks

    // each fragment will do one of those things:
    // - fetching metadata
    // - opening a session
    // Thus, this is the way of "communicating" with the fragments and know the
    // overall state of the activity
    fun onMetaFetched() {
        switchFragments(PasswordFragment())
    }

    /** Triggered by the [PasswordFragment] */
    fun onSessionOpened() {
        // service up and running, start the actual app
        val intent = Intent(this@LoadSessionActivity, AccountListActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
        startActivity(intent)
        finish()
    }

    // ----------------------------------------- Activity stuff

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_load_session)
        initWorkflow()
    }

    private fun initWorkflow() {
        switchFragments(ProgressFragment())
    }


    private fun switchFragments(f: Fragment) {
        // Execute a transaction, replacing any existing fragment
        // with this one inside the frame.
        f.setRetainInstance(true)
        mCurrentFragment = f
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.load_session_fragment_layout, f)
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        ft.commitAllowingStateLoss()
    }

    // ----------------------------------------- Metadata fetching Fragment

    class ProgressFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            super.onCreateView(inflater, container, savedInstanceState)
            //(activity as AppCompatActivity).supportActionBar?.hide()
            return inflater!!.inflate(R.layout.fragment_load_session_meta, container, false)
        }

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            loadLocalButton.setOnClickListener { _ -> next() }
            retryButton.setOnClickListener { _ -> fetchMeta() }

            fetchMeta()
        }

        fun fetchMeta() {
            errorLayout.visibility = View.GONE
            loadingLayout.visibility = View.VISIBLE
            DbxManager.fetchRemoteFileInfo().successUi {
                next()
            } failUi {
                val ex = it
                showError(ex)
            }
        }

        private fun showError(e: Exception) {
            errorText.text = e.message
            loadLocalButton.visibility = if (DbxManager.localFileExists) View.VISIBLE else View.GONE

            loadingLayout.visibility = View.GONE
            errorLayout.visibility = View.VISIBLE
        }

        fun next() {
            (activity as LoadSessionActivity).onMetaFetched()
        }
    }

    // ----------------------------------------- Credentials Fragment

    // inspired from https://github.com/Zlate87/android-fingerprint-example
    class PasswordFragment : Fragment() {

        private lateinit var mPrefs: Preferences
        private var mPassword: String? = null

        private var working: Boolean
            get() = progressBar.visibility == View.VISIBLE
            set(value) = if (value) {
                // show progressbar and disable the button
                progressBar.visibility = View.VISIBLE
                loginButton.isEnabled = false
            } else {
                progressBar.visibility = View.INVISIBLE
                loginButton.isEnabled = true
            }


        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            super.onCreateView(inflater, container, savedInstanceState)
            //(activity as AppCompatActivity).supportActionBar?.show()
            return inflater!!.inflate(R.layout.fragment_enter_password, container, false)
        }

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            // setup prefs
            mPrefs = Preferences(activity)

            // setup auth
            // cf https://developer.android.com/training/articles/keystore.html
            val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!keyguardManager.isKeyguardSecure) {
                // no way to save the password if the device doesn't have a pin
                rememberMeCheckbox.isEnabled = false
                rememberMeCheckbox.setText("Caching disabled.\nYour device is not secure.")
            }

            // show text in case it is the first time
            if (DbxManager.isNewSession) {
                rememberMeCheckbox.visibility = View.GONE // don't cache pass the first time
                rememberMeCheckbox.setText("")
                newSessionText.visibility = View.VISIBLE
                newSessionText.text = Html.fromHtml(getString(R.string.header_new_session))
            }

            // register btn callback
            loginButton.setOnClickListener({ _ ->
                mPassword = passwordField.text.toString()
                if (rememberMeCheckbox.isChecked) {
                    mPrefs.cachedPassword = null
                    savePasswordAndDecrypt()
                } else {
                    decryptSession()
                }
            })

            // toggle button to avoid empty passwords
            passwordField.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                    loginButton.isEnabled = passwordField.text.length >= MIN_PASSWORD_LENGTH
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            })

            // load login data from shared preferences
            // only the password is encrypted, IV used for the encryption is loaded from shared preferences
            if (CachedCredentials.isPasswordCached) {
                getPasswordsFromFingerprint()
            }

            // show session name
            sessionName.setText("session: ${mPrefs.remoteFilePathDisplay}")
            changeSessionBtn.setOnClickListener { _ ->
                activity.createSelectFileDialog({
                    (activity as LoadSessionActivity).initWorkflow()
                }).show()
            }
        }


        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            when (requestCode) {

                SAVE_CREDENTIALS_REQUEST_CODE -> {
                    // save the password only if the authentication was successful
                    if (resultCode == Activity.RESULT_OK) savePasswordAndDecrypt()
                    else decryptSession()
                }

                LOGIN_WITH_CREDENTIALS_REQUEST_CODE -> {
                    if (resultCode == Activity.RESULT_OK) {
                        getPasswordsFromFingerprint()
                    } else {
                        Toast.makeText(activity, "Confirming credentials failed", Toast.LENGTH_SHORT).show()
                        working = false
                    }
                }
            }
        }

        private fun decryptSession() {
            working = true
            DbxManager.openSession(mPassword!!).successUi {
                (activity as LoadSessionActivity).onSessionOpened()
            } failUi {
                val ex = it
                working = false
                if (ex is JsonManager.WrongCredentialsException) {
                    // remove wrong credentials
                    CachedCredentials.clearPassword()
                    Toast.makeText(activity, "Wrong credentials", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(activity, "An error occurred: " + ex.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        fun savePasswordAndDecrypt() {
            try {
                working = true
                CachedCredentials.savePassword(mPassword!!)
                decryptSession()
            } catch (e: UserNotAuthenticatedException) {
                showAuthenticationScreen(SAVE_CREDENTIALS_REQUEST_CODE)
            }
        }

        fun getPasswordsFromFingerprint() {
            try {
                working = true
                mPassword = CachedCredentials.getPassword()
                passwordField.setText(mPassword)
                decryptSession()
            } catch (e: UserNotAuthenticatedException) {
                showAuthenticationScreen(LOGIN_WITH_CREDENTIALS_REQUEST_CODE)
            } catch (e: KeyPermanentlyInvalidatedException) {
                working = false
                Toast.makeText(activity,
                        "Lock screen changed. Key invalidated.", Toast.LENGTH_LONG).show()
            }
        }

        private fun showAuthenticationScreen(requestCode: Int) {
            val intent = CachedCredentials.getAuthenticationIntent(activity, requestCode)
            if (intent != null) {
                startActivityForResult(intent, requestCode)
            } else {
                // keyguard ont secure !
                val prefs = Preferences()
                prefs.cachedPassword = null
                prefs.keysoreInitialised = false
                working = false
            }
        }


        // -----------------------------------------

        companion object {
            val SAVE_CREDENTIALS_REQUEST_CODE = 1
            val LOGIN_WITH_CREDENTIALS_REQUEST_CODE = 2
            val MIN_PASSWORD_LENGTH = 3
        }
    }

}
