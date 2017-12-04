package ch.derlin.easypass.easypass

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import ch.derlin.easypass.easypass.data.Account
import ch.derlin.easypass.easypass.helper.DbxManager
import kotlinx.android.synthetic.main.account_edit.*
import kotlinx.android.synthetic.main.activity_account_detail.*
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import java.util.*

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
    private lateinit var showPassCheckbox: CheckBox
    private var originalAccountIndex = -1

    private var progressbarVisible: Boolean
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

        progressbarVisible = false

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

        // TODO
        button_edit_save.setOnClickListener { activity.finish() }
        button_edit_cancel.setOnClickListener { activity.finish() }

        (activity as AccountDetailActivity).fab.setImageResource(R.drawable.ic_save_24dp)
        (activity as AccountDetailActivity).fab.setOnClickListener { _ ->
            saveAccount()
        }
    }


    private fun saveAccount() {
        val newAccount = getAccount()

        // check that something has indeed changed
        if (!newAccount.isDifferentFrom(mItem!!)) {
            Toast.makeText(activity, "nothing to save", Toast.LENGTH_SHORT).show()
            return
        }

        // ensure there are no duplicate names in the account list
        if (newAccount.name != mItem!!.name &&
                DbxManager.accounts!!.find { acc -> acc.name == newAccount.name } != null) {
            Toast.makeText(activity, "an account with this name already exists", Toast.LENGTH_LONG).show()
            return
        }

        // set modification date to now
        newAccount.modificationDate = Date().toString()

        // update accounts
        if (originalAccountIndex > -1) {
            newAccount.creationDate = mItem!!.creationDate
            DbxManager.accounts!![originalAccountIndex] = newAccount
        } else {
            DbxManager.accounts!!.add(newAccount)
        }

        // try save
        progressbarVisible = true
        DbxManager.saveAccounts().successUi {
            // saved ok, end the edit activity
            Toast.makeText(activity, "Saved!", Toast.LENGTH_SHORT).show()
            (activity as AccountDetailActivity).selectedAccount = newAccount
            activity.onBackPressed()

        } failUi {
            // failed ... oups
            progressbarVisible = false
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
            Account(
                    details_name.text.toString(),
                    details_pseudo.text.toString(),
                    details_email.text.toString(),
                    details_password.text.toString(),
                    details_notes.text.toString())


    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putBoolean(BUNDLE_CHECKBOX_STATE, showPassCheckbox.isChecked)
    }


    companion object {
        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        val ARG_ACCOUNT = "parcelable_account"
        val BUNDLE_CHECKBOX_STATE = "showpass_checked"
        val HIDDEN_PASSWORD = "********"
    }
}
