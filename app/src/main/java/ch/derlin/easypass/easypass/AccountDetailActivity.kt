package ch.derlin.easypass.easypass

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import ch.derlin.easypass.easypass.R.styleable.FloatingActionButton
import ch.derlin.easypass.easypass.data.Account
import ch.derlin.easypass.easypass.helper.SecureActivity
import kotlinx.android.synthetic.main.activity_account_detail.*

/**
 * An activity representing a single Account detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a [AccountListActivity].
 */
class AccountDetailActivity : SecureActivity() {

    private var selectedAccount: Account? = null
    private var selectedOperation: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_detail)
        setSupportActionBar(toolbar)

        fab.setOnClickListener {
            view -> editAccount()
            fab.hide()
            // Snackbar.make(view, "Replace with your own detail action", Snackbar.LENGTH_LONG)
            //        .setAction("Action", null).show()
        }

        // Show the Up button in the action bar.
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val extras = intent.extras
        if (extras != null) {
            selectedOperation = extras.getString("operation")

            if(selectedOperation == "edit" || selectedOperation == "show") {
                selectedAccount = intent.getParcelableExtra("account")
            }
        }

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            val arguments = Bundle()

            if(selectedOperation == "new" || selectedOperation == "edit") {
                fab.hide()
                arguments.putParcelable(AccountEditFragment.ARG_ACCOUNT, selectedAccount)
                val fragment = AccountEditFragment()
                fragment.arguments = arguments
                supportFragmentManager.beginTransaction()
                        .add(R.id.accountDetailContainer, fragment)
                        .commit()
            }
            else if(selectedOperation == "show") {
                fab.show()
                arguments.putParcelable(AccountDetailFragment.ARG_ACCOUNT, selectedAccount)
                val fragment = AccountDetailFragment()
                fragment.arguments = arguments
                supportFragmentManager.beginTransaction()
                        .add(R.id.accountDetailContainer, fragment)
                        .commit()
            }
        }
    }

    private fun editAccount(): Boolean {

        val arguments = Bundle()
        arguments.putParcelable(AccountEditFragment.ARG_ACCOUNT, selectedAccount)
        val fragment = AccountEditFragment()
        fragment.arguments = arguments
        supportFragmentManager.beginTransaction()
                .replace(R.id.accountDetailContainer, fragment)
                .commit()

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            navigateUpTo(Intent(this, AccountListActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
