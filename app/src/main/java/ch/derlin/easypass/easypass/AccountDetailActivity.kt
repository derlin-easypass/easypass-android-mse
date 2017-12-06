package ch.derlin.easypass.easypass

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.MenuItem
import android.widget.Toast
import ch.derlin.easypass.easypass.data.Account
import ch.derlin.easypass.easypass.helper.NetworkStatus
import ch.derlin.easypass.easypass.helper.SecureActivity
import kotlinx.android.synthetic.main.activity_account_detail.*
import android.content.Intent


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
    private var accountModified = false

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
            selectedOperation = extras.getString(BUNDLE_OPERATION_KEY)

            if (selectedOperation == OPERATION_EDIT || selectedOperation == OPERATION_SHOW) {
                selectedAccount = intent.getParcelableExtra(BUNDLE_ACCOUNT_KEY)
            }
        }

        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction only the first time
            switchFragment(if (selectedOperation == OPERATION_SHOW)
                AccountDetailFragment() else AccountEditFragment())
        }
    }


    fun editAccount(): Boolean {
        if (NetworkStatus.isInternetAvailable()) {
            switchFragment(AccountEditFragment())
            shouldGoBackToEditView = true
            return true
        } else {
            Toast.makeText(this, "no internet connection available.", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    fun setUpdatedAccount(account: Account) {
        selectedAccount = account
        accountModified = true
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
            val returnIntent = Intent()
            returnIntent.putExtra("modified", accountModified)
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }
    }

    companion object {
        val BUNDLE_ACCOUNT_KEY = "account"
        val BUNDLE_OPERATION_KEY = "operation"
        val OPERATION_SHOW = "show"
        val OPERATION_EDIT = "edit"
        val OPERATION_NEW = "new"
        val RETURN_MODIFIED = "modified"
    }

}
