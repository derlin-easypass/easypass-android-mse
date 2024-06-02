package ch.derlin.easypass


import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import ch.derlin.easypass.data.Account
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.easypass.databinding.AccountEditBinding
import ch.derlin.easypass.helper.DbxManager
import ch.derlin.easypass.helper.MiscUtils.colorizePassword
import ch.derlin.easypass.helper.MiscUtils.rootView
import ch.derlin.easypass.helper.PasswordGenerator
import ch.derlin.easypass.helper.Preferences
import com.google.android.material.snackbar.Snackbar
import com.shawnlin.numberpicker.NumberPicker
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

    private var _binding: AccountEditBinding? = null
    private val binding get() = _binding!!

    /**
     * The dummy content this fragment is presenting.
     */
    private var mItem: Account? = null
    private var originalAccountIndex = -1

    private var working: Boolean
        get() = binding.progressBar.visibility == View.VISIBLE
        set(value) {
            binding.progressBar.visibility = if (value) View.VISIBLE else View.INVISIBLE
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // do not recreate the menu when switching fragments,
        // so the search state is kept in two panes mode
        setHasOptionsMenu(false)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        mItem =
            arguments?.getParcelable(AccountDetailActivity.BUNDLE_ACCOUNT_KEY, Account::class.java)
        mItem?.name?.let { name ->
            originalAccountIndex = DbxManager.accounts.indexOfFirst { acc -> acc.name == name }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AccountEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        working = false
        val theActivity = activity as? AccountDetailActivity

        theActivity?.updateTitle(if (mItem != null) "Editing ${mItem?.name}" else "New account")

        // Show the dummy content as text in a TextView.
        if (mItem != null) {
            binding.detailsName.setText(mItem!!.name)
            binding.detailsEmail.setText(mItem!!.email)
            binding.detailsPseudo.setText(mItem!!.pseudo)
            binding.detailsPassword.setText(mItem!!.password)
            binding.detailsNotes.setText(mItem!!.notes)
        }

        binding.buttonEditSave.isEnabled = mItem?.name?.isNotBlank() ?: false

        binding.detailsName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                val newName = p0?.toString() ?: ""
                (activity as? AccountDetailActivity)?.fab?.isEnabled = newName.isNotBlank()
                binding.buttonEditSave.isEnabled = newName.isNotBlank()
            }
        })

        // open generate password dialog
        binding.buttonGeneratePassword.setOnClickListener { generatePassword() }

        // save and cancel buttons
        binding.buttonEditSave.setOnClickListener { saveAccount() }
        binding.buttonEditCancel.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        theActivity?.fab?.setImageResource(R.drawable.ic_save)
        theActivity?.fab?.setOnClickListener { _ ->
            saveAccount()
        }

//        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
//                .toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    private fun generatePassword() {

        val dialogView =
            requireActivity().layoutInflater.inflate(R.layout.dialog_generate_password, null)

        val resultText = dialogView.findViewById<EditText>(R.id.generate_password_result)
        val sizePicker = dialogView.findViewById<NumberPicker>(R.id.number_picker)
        val specialCharsToggle =
            dialogView.findViewById<CheckBox>(R.id.generate_password_special_chars)

        // generate action
        dialogView.findViewById<Button>(R.id.generate_password_button).run {
            setOnClickListener {
                PasswordGenerator.generate(
                    sizePicker.value,
                    specialCharsToggle.isChecked,
                    Preferences.specialChars
                ).let {
                    resultText.setText(it.colorizePassword())
                }
            }
            performClick() // default value
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.AppTheme_AlertDialog)
            .setView(dialogView)
            .setTitle("Generate a password")
            .setPositiveButton("Use") { _, _ ->
                binding.detailsPassword.setText(resultText.text.toString())
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
                Toast.makeText(
                    activity,
                    "an account with this name already exists",
                    Toast.LENGTH_LONG
                ).show()
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
            Snackbar.make(
                requireActivity().rootView(),
                "Error $it", Snackbar.LENGTH_LONG
            )
                .show()
        }
    }

    private fun getAccount(): Account =
        (mItem ?: Account()).copy(
            name = binding.detailsName.text.toString(),
            pseudo = binding.detailsPseudo.text.toString(),
            email = binding.detailsEmail.text.toString(),
            password = binding.detailsPassword.text.toString(),
            notes = binding.detailsNotes.text.toString(),
            modificationDate = Account.now
        )

}
