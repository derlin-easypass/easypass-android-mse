package ch.derlin.easypass

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import ch.derlin.easypass.data.Account
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.helper.MiscUtils.colorizePassword
import ch.derlin.easypass.helper.MiscUtils.copyToClipBoard
import ch.derlin.easypass.helper.MiscUtils.rootView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.account_detail.*
import kotlinx.android.synthetic.main.activity_account_detail.*

/**
 * A fragment representing a single Account detail screen.
 * This fragment is either contained in a [AccountListActivity]
 * in two-pane mode (on tablets) or a [AccountDetailActivity]
 * on handsets.
 */
/**
 * Mandatory empty constructor for the fragment manager to instantiate the
 * fragment (e.g. upon screen orientation changes).
 */
class AccountDetailFragment : Fragment() {

    /**
     * The dummy content this fragment is presenting.
     */
    private var mItem: Account? = null
    private var isPasswordShowed = false

    private lateinit var password: String
    private lateinit var hiddenPassword: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.account_detail, container, false)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Load the dummy content specified by the fragment
        // arguments. In a real-world scenario, use a Loader
        // to load content from a content provider.
        mItem = arguments?.getParcelable(AccountDetailActivity.BUNDLE_ACCOUNT_KEY)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Show the dummy content as text in a TextView.
        mItem?.run {
            details_name.text = name
            details_email.text = email
            details_pseudo.text = pseudo
            details_notes.text = notes
            details_created_date.text = creationDate
            details_modified_date.text = modificationDate

            // handle password
            this@AccountDetailFragment.password = password
            hiddenPassword = if (password.isEmpty()) "" else HIDDEN_PASSWORD

            isPasswordShowed = true // (toggle will toggle the state back to false)
            togglePassword()

            // register listener on the show password checkbox
            details_show_password.setOnClickListener { togglePassword() }
            details_copy_password.setOnClickListener {
                val activity = requireActivity()
                activity.copyToClipBoard(password)
                Snackbar.make(
                    activity.rootView(),
                    "Copied to clipboard", Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        val theActivity = activity as? AccountDetailActivity

        if (theActivity != null) {
            // set the title
            theActivity.updateTitle(mItem?.name ?: "Details")
            // handle the fab icon + action
            theActivity.fab?.setImageResource(R.drawable.ic_mode_edit)
            theActivity.fab?.setOnClickListener { theActivity.editAccount() }

        } else {
            // show the edit button
            button_container.visibility = View.VISIBLE
            button_edit.setOnClickListener {
                (activity as? AccountListActivity)?.openDetailActivity(mItem!!, AccountDetailActivity.OPERATION_EDIT)
            }
        }
    }

    private fun togglePassword() {
        isPasswordShowed = !isPasswordShowed
        details_password.text = if (isPasswordShowed) mItem!!.password.colorizePassword() else hiddenPassword

        details_show_password.background = AppCompatResources.getDrawable(
            requireContext(),
            if (isPasswordShowed) R.drawable.ic_visibility_on
            else R.drawable.ic_visibility_off
        )
    }

    companion object {
        const val HIDDEN_PASSWORD = "********"
    }
}
