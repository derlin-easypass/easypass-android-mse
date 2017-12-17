package ch.derlin.easypass.easypass


import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import ch.derlin.easypass.easypass.data.Account
import ch.derlin.easypass.easypass.helper.DbxManager
import ch.derlin.easypass.easypass.helper.MiscUtils.hideKeyboard
import ch.derlin.easypass.easypass.helper.PasswordGenerator
import com.shawnlin.numberpicker.NumberPicker
import kotlinx.android.synthetic.main.account_edit.*
import kotlinx.android.synthetic.main.activity_account_detail.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (arguments?.containsKey(ARG_ACCOUNT) ?: false) {
            mItem = arguments.getParcelable(ARG_ACCOUNT)
            originalAccountIndex =
                    DbxManager.accounts!!.indexOfFirst { acc -> acc.name == mItem!!.name }
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
            inflater!!.inflate(R.layout.account_edit, container, false)


    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        working = false

        val theActivity = activity as AccountDetailActivity
        theActivity.title = if (mItem != null) "Editing ${mItem?.name}" else "New account"

        // Show the dummy content as text in a TextView.
        if (mItem != null) {
            details_name.setText(mItem!!.name)
            details_email.setText(mItem!!.email)
            details_pseudo.setText(mItem!!.pseudo)
            details_password.setText(mItem!!.password)
            details_notes.setText(mItem!!.notes)
        }

        details_name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                val newName = p0?.toString() ?: ""
                (activity as AccountDetailActivity).fab.isEnabled = !newName.isBlank()
            }
        })

        // see https://stackoverflow.com/a/39770984/2667536
        details_notes.setHorizontallyScrolling(false)
        details_notes.maxLines = 5
        details_notes.setOnEditorActionListener { textView, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        // open generate password dialog
        button_generate_password.setOnClickListener { generatePassword() }

        // save and cancel buttons
        button_edit_save.setOnClickListener { saveAccount() }
        button_edit_cancel.setOnClickListener { activity.onBackPressed() }

        theActivity.fab.setImageResource(R.drawable.ic_save)
        theActivity.fab.setOnClickListener { _ ->
            saveAccount()
        }

//        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
//                .toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    private fun generatePassword() {

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_generate_password, null)

        val resultText = dialogView.findViewById<EditText>(R.id.generate_password_result)
        val sizePicker = dialogView.findViewById<NumberPicker>(R.id.number_picker)
        val specialChars = dialogView.findViewById<CheckBox>(R.id.generate_password_special_chars)

        // generate action
        dialogView.findViewById<Button>(R.id.generate_password_button).setOnClickListener {
            resultText.setText(PasswordGenerator.generate(sizePicker.value, specialChars.isChecked))
        }

        // default value
        resultText.setText(PasswordGenerator.generate(sizePicker.value, specialChars.isChecked))

        val dialog = AlertDialog.Builder(activity, R.style.AppTheme_AlertDialog)
                .setView(dialogView)
                .setTitle("Generate a password")
                .setPositiveButton("Use", { _, _ ->
                    details_password.setText(resultText.text.toString())
                })
                .setNegativeButton("Cancel", { dialog, _ -> })
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
        if (newAccount.name != mItem?.name &&
                DbxManager.accounts!!.indexOfFirst { acc ->
                    acc.name.equals(newAccount.name, ignoreCase = true)
                } > -1) {
            Toast.makeText(activity, "an account with this name already exists", Toast.LENGTH_LONG).show()
            return
        }

        // ok, now it become critical
        if (working) return
        working = true

        // update accounts
        if (originalAccountIndex > -1) {
            newAccount.creationDate = mItem!!.creationDate
            DbxManager.accounts!![originalAccountIndex] = newAccount
        } else {
            DbxManager.accounts!!.add(newAccount)
        }

        // try save
        DbxManager.saveAccounts().successUi {
            // saved ok, end the edit activity
            Toast.makeText(activity, "Saved!", Toast.LENGTH_SHORT).show()
            (activity as AccountDetailActivity).setUpdatedAccount(newAccount)
            activity.onBackPressed()

        } failUi {
            // failed ... oups
            working = false
            // undo !
            if (originalAccountIndex >= -1) {
                DbxManager.accounts!![originalAccountIndex] = mItem!!
            } else {
                DbxManager.accounts!!.remove(newAccount)
            }
            // show error
            Snackbar.make(getActivity().findViewById(android.R.id.content),
                    "Error " + it, Snackbar.LENGTH_LONG)
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


    companion object {
        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        val ARG_ACCOUNT = "parcelable_account"
    }
}
