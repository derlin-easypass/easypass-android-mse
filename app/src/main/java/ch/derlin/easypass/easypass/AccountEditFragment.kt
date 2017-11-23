package ch.derlin.easypass.easypass

import android.support.design.widget.CollapsingToolbarLayout
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import ch.derlin.easypass.easypass.data.Account

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        if (arguments.containsKey(ARG_ACCOUNT)) {
//            // Load the dummy content specified by the fragment
//            // arguments. In a real-world scenario, use a Loader
//            // to load content from a content provider.
//            mItem = arguments.getParcelable(ARG_ACCOUNT)
//
//            val activity = this.activity
//            val appBarLayout = activity.findViewById<View>(R.id.toolbar_layout) as CollapsingToolbarLayout
//            if (appBarLayout != null) {
//                appBarLayout.title = mItem!!.name
//            }
//        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val rootView = inflater!!.inflate(R.layout.account_edit, container, false)

        // Show the dummy content as text in a TextView.
        if (mItem != null) {

            rootView.findViewById<TextView>(R.id.details_name).text = mItem!!.name
            rootView.findViewById<TextView>(R.id.details_email).text = mItem!!.email
            rootView.findViewById<TextView>(R.id.details_pseudo).text = mItem!!.pseudo
            rootView.findViewById<TextView>(R.id.details_notes).text = mItem!!.notes

            // handle password
            val password = mItem!!.password
            val hiddenPassword = if (password.isEmpty()) "" else HIDDEN_PASSWORD
            val passwordField = rootView.findViewById<TextView>(R.id.details_password)

            // register listener on the show password checkbox
            showPassCheckbox = rootView.findViewById<CheckBox>(R.id.details_show_password)
            showPassCheckbox.setOnCheckedChangeListener {
                compoundButton, checked -> passwordField.text = if(checked) password else hiddenPassword
            }

            // check if the checkbox state was previously saved
            if(savedInstanceState != null && savedInstanceState.getBoolean(BUNDLE_CHECKBOX_STATE)){
                showPassCheckbox.isChecked = true
                passwordField.text = password
            }else{
                passwordField.text = hiddenPassword
            }
        }

        return rootView
    }

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
