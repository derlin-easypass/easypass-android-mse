package ch.derlin.easypass.easypass

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
    private lateinit var showPassCheckbox: CheckBox
    private var isPasswordShowed = false

    private lateinit var password: String
    private lateinit var hiddenPassword: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (arguments.containsKey(ARG_ACCOUNT)) {
            // Load the dummy content specified by the fragment
            // arguments. In a real-world scenario, use a Loader
            // to load content from a content provider.
            mItem = arguments.getParcelable(ARG_ACCOUNT)

        }

    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
            inflater!!.inflate(R.layout.account_detail, container, false)


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

        // handle the fab icon + action
        (activity as AccountDetailActivity).fab.setImageResource(R.drawable.ic_mode_edit)
        (activity as AccountDetailActivity).fab.setOnClickListener { _ ->
            (activity as AccountDetailActivity).editAccount()
        }

        // set the title
        (activity as AccountDetailActivity).title = mItem?.name ?: "Details"

    }

    private fun togglePassword() {
        isPasswordShowed = !isPasswordShowed
        details_password.text = if (isPasswordShowed) mItem!!.password else hiddenPassword
        details_show_password.background = activity.getDrawable(
                if (isPasswordShowed) R.drawable.ic_visibility_on
                else R.drawable.ic_visibility_off)
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
