package ch.derlin.easypass.easypass

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ch.derlin.easypass.easypass.data.Account
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

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
            inflater!!.inflate(R.layout.account_detail, container, false)

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (arguments.containsKey(AccountDetailActivity.BUNDLE_ACCOUNT_KEY)) {
            // Load the dummy content specified by the fragment
            // arguments. In a real-world scenario, use a Loader
            // to load content from a content provider.
            mItem = arguments.getParcelable(AccountDetailActivity.BUNDLE_ACCOUNT_KEY)

        }
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Show the dummy content as text in a TextView.
        if (mItem != null) {

            details_name.text = mItem!!.name
            details_email.text = mItem!!.email
            details_pseudo.text = mItem!!.pseudo
            details_notes.text = mItem!!.notes
            details_created_date.text = mItem!!.creationDate
            details_modified_date.text = mItem!!.modificationDate

            // handle password
            password = mItem!!.password
            hiddenPassword = if (password.isEmpty()) "" else HIDDEN_PASSWORD

            isPasswordShowed = true // (toggle will toggle the state back to false)
            togglePassword()

            // register listener on the show password checkbox
            details_show_password.setOnClickListener { compoundButton -> togglePassword() }
        }

        val theActivity = activity as? AccountDetailActivity

        // handle the fab icon + action
        theActivity?.fab?.setImageResource(R.drawable.ic_mode_edit)
        theActivity?.fab?.setOnClickListener { _ ->
            theActivity.editAccount()
        }
        button_edit.setOnClickListener { _ ->
            if (theActivity != null) {
                theActivity.editAccount()
            } else {
                (activity as? AccountListActivity)?.
                        openDetailActivity(mItem!!, AccountDetailActivity.OPERATION_EDIT)
            }
        }

        // set the title
        theActivity?.title = mItem?.name ?: "Details"
    }

    private fun togglePassword() {
        isPasswordShowed = !isPasswordShowed
        details_password.text = if (isPasswordShowed) mItem!!.password else hiddenPassword
        details_show_password.background = activity.getDrawable(
                if (isPasswordShowed) R.drawable.ic_visibility_on
                else R.drawable.ic_visibility_off)
    }

    companion object {
        val BUNDLE_CHECKBOX_STATE = "showpass_checked"
        val HIDDEN_PASSWORD = "********"
    }
}
