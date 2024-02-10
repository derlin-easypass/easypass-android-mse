package ch.derlin.easypass

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import ch.derlin.easypass.data.Account
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.helper.NetworkStatus
import ch.derlin.easypass.helper.SecureActivity
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
    private var shouldGoBackToEditView = false
    private var accountModified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_detail)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { editAccount() }

        // Show the Up button in the action bar.
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val extras = intent.extras
        if (extras != null) {
            selectedOperation = extras.getString(BUNDLE_OPERATION_KEY)

            if (selectedOperation == OPERATION_EDIT || selectedOperation == OPERATION_SHOW) {
                selectedAccount = intent.getParcelableExtra(BUNDLE_ACCOUNT_KEY, Account::class.java)
            }
        }

        app_bar.setExpanded(selectedOperation == OPERATION_SHOW, false)
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction only the first time
            switchFragment(
                if (selectedOperation == OPERATION_SHOW)
                    AccountDetailFragment() else AccountEditFragment()
            )
        }

        registerOnBackPressed()
    }

    fun updateTitle(title: String) {
        toolbarLayout.title = title
    }

    fun editAccount(): Boolean {
        return if (NetworkStatus.isInternetAvailable()) {
            switchFragment(AccountEditFragment())
            shouldGoBackToEditView = true
            app_bar.setExpanded(false, true)
            true
        } else {
            Toast.makeText(this, "no internet connection available.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun setUpdatedAccount(account: Account) {
        selectedAccount = account
        accountModified = true
        //this.onBackPressed()
        backToDetailsView()
    }

    private fun switchFragment(f: Fragment) {
        var arguments: Bundle? = null

        if (selectedAccount != null) {
            arguments = Bundle()
            arguments.putParcelable(BUNDLE_ACCOUNT_KEY, selectedAccount)
        }

        f.arguments = arguments
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.accountDetailContainer, f)
        transaction.commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            //navigateUpTo(Intent(this, AccountListActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun backToDetailsView() {
        switchFragment(AccountDetailFragment())
        app_bar.setExpanded(true, true)
        shouldGoBackToEditView = false
    }


    private fun registerOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (shouldGoBackToEditView) {
                    backToDetailsView()
                } else {
                    val returnIntent = Intent()
                    returnIntent.putExtra("modified", accountModified)
                    returnIntent.putExtra(BUNDLE_ACCOUNT_KEY, selectedAccount)
                    setResult(Activity.RESULT_OK, returnIntent)
                    finish()
                }
            }
        })
    }


    companion object {
        const val BUNDLE_ACCOUNT_KEY = "account"
        const val BUNDLE_OPERATION_KEY = "operation"
        const val OPERATION_SHOW = "show"
        const val OPERATION_EDIT = "edit"
        const val OPERATION_NEW = "new"
        const val RETURN_MODIFIED = "modified"
    }

}
