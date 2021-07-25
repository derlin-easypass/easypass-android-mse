package ch.derlin.easypass


import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import ch.derlin.easypass.data.Account
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.helper.DbxManager
import ch.derlin.easypass.helper.MiscUtils.hideKeyboard
import ch.derlin.easypass.helper.MiscUtils.rootView
import ch.derlin.easypass.helper.PasswordGenerator
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.account_edit.*
import kotlinx.android.synthetic.main.activity_account_detail.*
import kotlinx.android.synthetic.main.dialog_generate_password.*
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi


/**
 * A fragment representing a single Account detail screen.
 * This fragment is either contained in a [AccountListActivity]
 * in two-pane mode (on tablets) or a [AccountEditActivity]
 * on handsets.
 */
/**
 * Mandatory empty constructor for the fragment manager to instantiate the
 * fragment (e.g. upon screen orientation changes).
 */

class AccountEditFragment : Fragment() {

    /**
     * The dummy content this fragment is presenting.
     */
    private var mItem: Account? = null
    private var originalAccountIndex = -1

    private var working: Boolean
        get() = progressBar.visibility == View.VISIBLE
        set(value) = progressBar.setVisibility(if (value) View.VISIBLE else View.INVISIBLE)

    override fun onAttach(context: Context) {
        super.onAttach(context)

        mItem = arguments?.getParcelable(AccountDetailActivity.BUNDLE_ACCOUNT_KEY)
        mItem?.name?.let { name ->
            originalAccountIndex = DbxManager.accounts.indexOfFirst { acc -> acc.name == name }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.account_edit, container, false)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        working = false
        val theActivity = activity as? AccountDetailActivity

        theActivity?.updateTitle(if (mItem != null) "Editing ${mItem?.name}" else "New account")

        // Show the dummy content as text in a TextView.
        if (mItem != null) {
            details_name.setText(mItem!!.name)
            details_email.setText(mItem!!.email)
            details_pseudo.setText(mItem!!.pseudo)
            details_password.setText(mItem!!.password)
            details_notes.setText(mItem!!.notes)
        }

        button_edit_save.isEnabled = mItem?.name?.isNotBlank() ?: false

        details_name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                val newName = p0?.toString() ?: ""
                (activity as? AccountDetailActivity)?.fab?.isEnabled = newName.isNotBlank()
                button_edit_save.isEnabled = newName.isNotBlank()
            }
        })

        // see https://stackoverflow.com/a/39770984/2667536
        details_notes.setHorizontallyScrolling(false)
        details_notes.maxLines = 5
        details_notes.setOnEditorActionListener { textView, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                requireActivity().hideKeyboard()
                true
            } else {
                false
            }
        }

        // open generate password dialog
        button_generate_password.setOnClickListener { generatePassword() }

        // save and cancel buttons
        button_edit_save.setOnClickListener { saveAccount() }
        button_edit_cancel.setOnClickListener { requireActivity().onBackPressed() }

        theActivity?.fab?.setImageResource(R.drawable.ic_save)
        theActivity?.fab?.setOnClickListener { _ ->
            saveAccount()
        }

//        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
//                .toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    private fun generatePassword() {

        val dialogView = requireActivity().layoutInflater.inflate(R.layout.dialog_generate_password, null)

        // generate action
        generate_password_button.setOnClickListener {
            PasswordGenerator.generate(number_picker.value, generate_password_special_chars.isChecked).let {
                generate_password_result.setText(it)
            }
        }

        // default value
        PasswordGenerator.generate(number_picker.value, generate_password_special_chars.isChecked).let {
            generate_password_result.setText(it)
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.AppTheme_AlertDialog)
                .setView(dialogView)
                .setTitle("Generate a password")
                .setPositiveButton("Use") { _, _ ->
                    details_password.setText(generate_password_result.text.toString())
                }
                .setNegativeButton("Cancel") { _, _ -> }
                .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY)

    }


    private fun saveAccount() {

        val newAccount = getAccount()

        // check that something has indeed changed
        if (mItem != null && !newAccount.isDifferentFrom(mItem!!)) {
            Toast.makeText(activity, "nothing to save", Toast.LENGTH_SHORT).show()
            return
        }

        // ensure the name is valid
        if (!newAccount.isValid) {
            Toast.makeText(activity, "The account name is mandatory", Toast.LENGTH_SHORT).show()
        }

        // ensure there are no duplicate names in the account list
        if (newAccount.name != mItem?.name) {
            // is new name already in the list ?
            val idx = DbxManager.accounts.indexOfFirst { acc ->
                acc.name.equals(newAccount.name, ignoreCase = true)
            }
            // ensure it is not just a change of letter casing
            if (idx >= 0 && idx != originalAccountIndex) {
                Toast.makeText(activity, "an account with this name already exists", Toast.LENGTH_LONG).show()
                return
            }
        }

        // ok, now it become critical
        if (working) return
        working = true

        // update accounts
        if (originalAccountIndex > -1) {
            newAccount.creationDate = mItem!!.creationDate
            DbxManager.accounts[originalAccountIndex] = newAccount
        } else {
            DbxManager.accounts.add(newAccount)
        }

        // try save
        DbxManager.saveAccounts().successUi {
            // saved ok, end the edit activity
            Toast.makeText(activity, "Saved!", Toast.LENGTH_SHORT).show()
            (activity as? AccountDetailActivity)?.setUpdatedAccount(newAccount)
            (activity as? AccountListActivity)?.notifyAccountUpdate(newAccount)

        } failUi {
            // failed ... oups
            working = false
            // undo !
            if (originalAccountIndex >= -1) {
                DbxManager.accounts[originalAccountIndex] = mItem!!
            } else {
                DbxManager.accounts.remove(newAccount)
            }
            // show error
            Snackbar.make(requireActivity().rootView(),
                    "Error $it", Snackbar.LENGTH_LONG)
                    .show()
        }
    }

    private fun getAccount(): Account =
            (mItem ?: Account()).copy(
                    name = details_name.text.toString(),
                    pseudo = details_pseudo.text.toString(),
                    email = details_email.text.toString(),
                    password = details_password.text.toString(),
                    notes = details_notes.text.toString(),
                    modificationDate = Account.now)

}
