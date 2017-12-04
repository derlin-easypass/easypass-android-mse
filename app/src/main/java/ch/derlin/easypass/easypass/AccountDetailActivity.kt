package ch.derlin.easypass.easypass

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.MenuItem
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

    var selectedAccount: Account? = null
    private var selectedOperation: String? = null
    private var shouldGoBackToEditView = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_detail)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view -> editAccount() }

        // Show the Up button in the action bar.
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val extras = intent.extras
        if (extras != null) {
            selectedOperation = extras.getString("operation")

            if (selectedOperation == "edit" || selectedOperation == "show") {
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
            switchFragment(if (selectedOperation == "show")
                AccountDetailFragment() else AccountEditFragment())
        }
    }

    fun editAccount(): Boolean {
        switchFragment(AccountEditFragment())
        shouldGoBackToEditView = true
        return true
    }

    private fun switchFragment(f: Fragment) {
        var arguments: Bundle? = null

        if (selectedAccount != null) {
            arguments = Bundle()
            arguments.putParcelable(AccountEditFragment.ARG_ACCOUNT, selectedAccount)
        }

        f.arguments = arguments
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.accountDetailContainer, f)
        transaction.commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            this.onBackPressed()
            //navigateUpTo(Intent(this, AccountListActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (shouldGoBackToEditView) {
            switchFragment(AccountDetailFragment())
            shouldGoBackToEditView = false
        } else {
            super.onBackPressed()
        }
    }

}
