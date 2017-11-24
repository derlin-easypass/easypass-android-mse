package ch.derlin.easypass.easypass

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import ch.derlin.easypass.easypass.data.Account
import kotlinx.android.synthetic.main.activity_account_detail.*

/**
 * An activity representing a single Account detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a [AccountListActivity].
 */
class AccountDetailActivity : AppCompatActivity() {

    private var selectedAccount: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_detail)

        selectedAccount = intent.getParcelableExtra(AccountDetailFragment.ARG_ACCOUNT)
        fab.setOnClickListener { view -> editAccount()
            // Snackbar.make(view, "Replace with your own detail action", Snackbar.LENGTH_LONG)
            //        .setAction("Action", null).show()
        }

        // Show the Up button in the action bar.
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

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
            arguments.putParcelable(AccountDetailFragment.ARG_ACCOUNT,
                    intent.getParcelableExtra(AccountDetailFragment.ARG_ACCOUNT))
            val fragment = AccountDetailFragment()
            fragment.arguments = arguments
            supportFragmentManager.beginTransaction()
                    .add(R.id.accountDetailContainer, fragment)
                    .commit()
        }
    }

    private fun editAccount(): Boolean {
        val context = this
        val intent = Intent(context, AccountEditActivity::class.java)
        intent.putExtra(AccountDetailFragment.ARG_ACCOUNT, selectedAccount)
        context.startActivity(intent)
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
