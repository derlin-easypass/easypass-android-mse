package ch.derlin.easypass

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import android.text.Editable
import android.text.Html
import android.text.Html.FROM_HTML_MODE_LEGACY
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import ch.derlin.easypass.data.JsonManager
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.easypass.databinding.ActivityLoadSessionBinding
import ch.derlin.easypass.easypass.databinding.FragmentEnterPasswordBinding
import ch.derlin.easypass.easypass.databinding.FragmentLoadSessionMetaBinding
import ch.derlin.easypass.helper.CachedCredentials
import ch.derlin.easypass.helper.DbxManager
import ch.derlin.easypass.helper.Preferences
import ch.derlin.easypass.helper.SelectFileDialog.createSelectFileDialog
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi

class LoadSessionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoadSessionBinding
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
        binding = ActivityLoadSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        initWorkflow()
    }

    private fun initWorkflow() {
        switchFragments(ProgressFragment())
    }


    private fun switchFragments(f: Fragment) {
        // Execute a transaction, replacing any existing fragment
        // with this one inside the frame.
        f.retainInstance = true
        mCurrentFragment = f
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.load_session_fragment_layout, f)
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        ft.commitAllowingStateLoss()
    }

    // ----------------------------------------- Metadata fetching Fragment

    class ProgressFragment : Fragment() {
        private var _binding: FragmentLoadSessionMetaBinding? = null
        private val binding get() = _binding!!

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            _binding = FragmentLoadSessionMetaBinding.inflate(inflater, container, false)
            return binding.root
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            binding.loadLocalButton.setOnClickListener { next() }
            binding.retryButton.setOnClickListener { fetchMeta() }

            fetchMeta()
        }

        private fun fetchMeta() {
            binding.errorLayout.visibility = View.GONE
            binding.loadingLayout.visibility = View.VISIBLE
            DbxManager.fetchRemoteFileInfo().successUi {
                next()
            } failUi {
                val ex = it
                showError(ex)
            }
        }

        private fun showError(e: Exception) {
            binding.errorText.text = e.message
            binding.loadLocalButton.visibility =
                if (DbxManager.localFileExists) View.VISIBLE else View.GONE

            binding.loadingLayout.visibility = View.GONE
            binding.errorLayout.visibility = View.VISIBLE
        }

        fun next() {
            (activity as LoadSessionActivity).onMetaFetched()
        }
    }

    // ----------------------------------------- Credentials Fragment

    // inspired from https://github.com/Zlate87/android-fingerprint-example
    class PasswordFragment : Fragment() {
        private var _binding: FragmentEnterPasswordBinding? = null
        private val binding get() = _binding!!
        private var mPassword: String? = null

        private var working: Boolean
            get() = binding.progressBar.visibility == View.VISIBLE
            set(value) = if (value) {
                // show progressbar and disable the button
                binding.progressBar.visibility = View.VISIBLE
                binding.loginButton.isEnabled = false
            } else {
                binding.progressBar.visibility = View.INVISIBLE
                binding.loginButton.isEnabled = true
            }


        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            _binding = FragmentEnterPasswordBinding.inflate(inflater, container, false)
            return binding.root
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            // setup auth
            // cf https://developer.android.com/training/articles/keystore.html
            val keyguardManager =
                requireActivity().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!keyguardManager.isKeyguardSecure) {
                // no way to save the password if the device doesn't have a pin
                binding.rememberMeCheckbox.isEnabled = false
                binding.rememberMeCheckbox.text = "Caching disabled.\nYour device is not secure."
            }

            // show text in case it is the first time
            if (DbxManager.isNewSession) {
                binding.rememberMeCheckbox.visibility = View.GONE // don't cache pass the first time
                binding.rememberMeCheckbox.text = ""
                binding.newSessionText.visibility = View.VISIBLE
                binding.newSessionText.text =
                    Html.fromHtml(getString(R.string.header_new_session), FROM_HTML_MODE_LEGACY)
            }

            // register btn callback
            binding.loginButton.setOnClickListener {
                mPassword = binding.passwordField.text.toString()
                if (binding.rememberMeCheckbox.isChecked) {
                    Preferences.cachedPassword = null
                    savePasswordAndDecrypt()
                } else {
                    decryptSession()
                }
            }

            // toggle button to avoid empty passwords
            binding.passwordField.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                    binding.loginButton.isEnabled =
                        (binding.passwordField.text ?: "").length >= MIN_PASSWORD_LENGTH
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
            binding.sessionName.text = "session: ${Preferences.remoteFilePathDisplay}"
            binding.changeSessionBtn.setOnClickListener {
                requireActivity().createSelectFileDialog {
                    (activity as LoadSessionActivity).initWorkflow()
                }.show()
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
                        Toast.makeText(
                            activity,
                            "Confirming credentials failed",
                            Toast.LENGTH_SHORT
                        ).show()
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
                    Toast.makeText(activity, "An error occurred: " + ex.message, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }

        private fun savePasswordAndDecrypt() {
            try {
                working = true
                CachedCredentials.savePassword(mPassword!!)
                decryptSession()
            } catch (e: UserNotAuthenticatedException) {
                showAuthenticationScreen(SAVE_CREDENTIALS_REQUEST_CODE)
            }
        }

        private fun getPasswordsFromFingerprint() {
            try {
                working = true
                mPassword = CachedCredentials.getPassword()
                binding.passwordField.setText(mPassword)
                decryptSession()
            } catch (e: UserNotAuthenticatedException) {
                showAuthenticationScreen(LOGIN_WITH_CREDENTIALS_REQUEST_CODE)
            } catch (e: KeyPermanentlyInvalidatedException) {
                working = false
                Toast.makeText(
                    activity,
                    "Lock screen changed. Key invalidated.", Toast.LENGTH_LONG
                ).show()
            }
        }

        private fun showAuthenticationScreen(requestCode: Int) {
            val intent = CachedCredentials.getAuthenticationIntent(requireContext(), requestCode)
            if (intent != null) {
                startActivityForResult(intent, requestCode)
            } else {
                // keyguard ont secure !
                Preferences.cachedPassword = null
                Preferences.keystoreInitialised = false
                working = false
            }
        }


        // -----------------------------------------

        companion object {
            const val SAVE_CREDENTIALS_REQUEST_CODE = 1
            const val LOGIN_WITH_CREDENTIALS_REQUEST_CODE = 2
            const val MIN_PASSWORD_LENGTH = 3
        }
    }

}
