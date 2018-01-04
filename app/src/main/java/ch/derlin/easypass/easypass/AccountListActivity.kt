package ch.derlin.easypass.easypass

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.BottomSheetDialog
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import ch.derlin.easypass.easypass.data.Account
import ch.derlin.easypass.easypass.helper.*
import ch.derlin.easypass.easypass.helper.MiscUtils.restartApp
import kotlinx.android.synthetic.main.account_list.*
import kotlinx.android.synthetic.main.activity_account_list.*
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import timber.log.Timber


/**
 * An activity representing a list of Accounts. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [AccountDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
class AccountListActivity : SecureActivity() {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var mTwoPane: Boolean = false
    private var mTwoPaneCurrentFragment: Fragment? = null

    lateinit var mAdapter: AccountAdapter
    lateinit var searchView: SearchView
    var mOfflineIndicator: MenuItem? = null
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var selectedAccount: Account? = null

    private val mNetworkChangeListener = object : NetworkChangeListener() {
        override fun onNetworkChange(connectionAvailable: Boolean) {
            updateConnectivityViews(connectionAvailable)
            if (connectionAvailable) {
                syncWithRemote()
            } else {
                Snackbar.make(fab, "Network unavailable", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private var working: Boolean
        set(value) = progressBar.setVisibility(if (value) View.VISIBLE else View.INVISIBLE)
        get() = progressBar.visibility == View.VISIBLE


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_list)
        setSupportActionBar(toolbar)

        if (DbxManager.accounts == null) {
            Timber.e("accounts is null in list activity !!!")
            return
        }

        fab.setOnClickListener { view ->
            openDetailActivity(null, AccountDetailActivity.OPERATION_NEW)
        }

        syncButton.setOnClickListener { _ -> restartApp() }

        setupRecyclerView(recyclerView)

        if (accountDetailContainer != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == DETAIL_ACTIVITY_REQUEST_CODE) {
            if (data?.getBooleanExtra(AccountDetailActivity.RETURN_MODIFIED, false) ?: false) {
                // update the list in case of modification
                selectedAccount = data!!.getParcelableExtra(AccountDetailActivity.BUNDLE_ACCOUNT_KEY)
                notifyAccountUpdate(selectedAccount!!)
            }
        } else if (requestCode == SETTINGS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                data?.let {
                    // if asking for restart, kill current activity
                    // TODO: find a better way
                    if (it.getBooleanExtra(SettingsActivity.BUNDLE_RESTART_KEY, false)) {
                        finish()
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }

    }

    override fun onPause() {
        super.onPause()
        mNetworkChangeListener.unregisterSelf(this)
    }

    override fun onResume() {
        super.onResume()
        val connected = NetworkStatus.isInternetAvailable()
        syncButton.visibility = if (!connected || DbxManager.isInSync) View.GONE else View.VISIBLE
        updateConnectivityViews(connected)
        mNetworkChangeListener.registerSelf(this)
    }

    override fun onBackPressed() {
        if (mTwoPane && mTwoPaneCurrentFragment is AccountEditFragment) {
            if (selectedAccount != null) {
                openDetailActivity(selectedAccount!!, AccountDetailActivity.OPERATION_SHOW)
            } else {
                supportFragmentManager.beginTransaction().remove(mTwoPaneCurrentFragment).commit()
            }
        } else {
            super.onBackPressed()
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_list_accounts, menu)
        mOfflineIndicator = menu!!.findItem(R.id.action_offline)
        mOfflineIndicator!!.isVisible = !(NetworkStatus.isConnected ?: false)
        searchView = (menu.findItem(R.id.action_search).actionView as SearchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true

            override fun onQueryTextChange(newText: String?): Boolean {
                mAdapter.filter(newText)
                return true
            }
        })
        val sort = Preferences(this).sortOrder
        menu.findItem(sort).isChecked = true
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.groupId == R.id.group_menu_sort) {
            Preferences(this).sortOrder = item.itemId
            mAdapter.comparator = getSortOrder(item.itemId)
            item.isChecked = true
            return true
        } else {
            when (item?.itemId) {
                R.id.action_settings -> startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS_REQUEST_CODE)
                R.id.action_sync -> syncWithRemote()
                else -> return super.onOptionsItemSelected(item)
            }
            return true
        }

    }

    private fun getSortOrder(itemId: Int): Comparator<Account> {
        when (itemId) {
            R.id.submenu_sort_title_asc -> return Account.nameComparatorAsc
            R.id.submenu_sort_title_desc -> return Account.nameComparatorDesc
            R.id.submenu_sort_year_asc -> return Account.modifiedComparatorAsc
            R.id.submenu_sort_year_desc -> return Account.modifiedComparatorDesc
            else -> return Account.nameComparatorAsc
        }
    }

    private fun showBottomSheet(item: Account) {

        selectedAccount = item
        //hideKeyboard()
        if (searchView.hasFocus()) searchView.clearFocus()

        if (mTwoPane) {
            openDetailActivity(selectedAccount!!, AccountDetailActivity.OPERATION_SHOW)
            return
        }


        bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_layout, null)

        view.findViewById<TextView>(R.id.bottomSheetTitle).text = item.name

        var tv = view.findViewById<Button>(R.id.copy_username_btn)
        tv.text = MiscUtils.toSpannable(getString(R.string.fmt_copy_xx).format("username", item.pseudo))
        tv.isEnabled = item.pseudo.isNotBlank()

        tv = view.findViewById<Button>(R.id.copy_email_btn)
        tv.text = MiscUtils.toSpannable(getString(R.string.fmt_copy_xx).format("email", item.email))
        tv.isEnabled = item.email.isNotBlank()


        view.findViewById<Button>(R.id.view_edit_btn).isEnabled = NetworkStatus.isInternetAvailable(this)

        bottomSheetDialog!!.setContentView(view)
        bottomSheetDialog!!.show()
    }

    // this is bound from the xml layout
    fun bottomSheetClicked(v: View) {
        if (selectedAccount == null) return

        when (v.id) {
            R.id.copy_pass_btn -> copyToClipboard(selectedAccount!!.password, "password copied!")
            R.id.copy_username_btn -> copyToClipboard(selectedAccount!!.pseudo, "'${selectedAccount!!.pseudo}' copied!")
            R.id.copy_email_btn -> copyToClipboard(selectedAccount!!.email, "'${selectedAccount!!.email}' copied!")
            R.id.view_details_btn -> {
                bottomSheetDialog!!.dismiss()
                openDetailActivity(selectedAccount!!, AccountDetailActivity.OPERATION_SHOW)
            }
            R.id.view_edit_btn -> {
                bottomSheetDialog!!.dismiss()
                openDetailActivity(selectedAccount!!, AccountDetailActivity.OPERATION_EDIT)
            }
            else -> Toast.makeText(this, "something clicked", Toast.LENGTH_SHORT).show()
        }

    }

    private fun copyToClipboard(text: String, toastDescription: String = "") {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip = ClipData.newPlainText("easypass", text)
        if (toastDescription != "") {
            if (bottomSheetDialog != null)
                Snackbar.make(bottomSheetDialog!!.findViewById(android.R.id.content)!!, toastDescription, Snackbar.LENGTH_SHORT).show()
            else
                Toast.makeText(this, toastDescription, Toast.LENGTH_SHORT).show()
        }
    }


    fun openDetailActivity(item: Account?, operation: String): Boolean {
        if (mTwoPane) {
            val arguments = Bundle()
            arguments.putParcelable(AccountDetailActivity.BUNDLE_ACCOUNT_KEY, item)
            mTwoPaneCurrentFragment = if (operation == AccountDetailActivity.OPERATION_SHOW)
                AccountDetailFragment() else AccountEditFragment()
            mTwoPaneCurrentFragment!!.arguments = arguments
            supportFragmentManager.beginTransaction()
                    .replace(R.id.accountDetailContainer, mTwoPaneCurrentFragment)
                    .commit()
        } else {
            val context = this
            val intent = Intent(context, AccountDetailActivity::class.java)
            intent.putExtra(AccountDetailActivity.BUNDLE_OPERATION_KEY, operation)
            intent.putExtra(AccountDetailActivity.BUNDLE_ACCOUNT_KEY, item)
            context.startActivityForResult(intent, DETAIL_ACTIVITY_REQUEST_CODE)
        }
        return true
    }

    // only called in mTwoPane mode
    fun notifyAccountUpdate(item: Account) {
        selectedAccount = item
        mAdapter.replace(selectedAccount!!, item)
        val idx = mAdapter.positionOf(item)
        if (idx >= 0) recyclerView.scrollToPosition(idx)

        if (mTwoPane) openDetailActivity(item, AccountDetailActivity.OPERATION_SHOW)
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        val sort = Preferences(this).sortOrder

        mAdapter = AccountAdapter(DbxManager.accounts!!,
                defaultComparator = getSortOrder(sort),
                textviewCounter = countText)

        //mAdapter = AccountAdapter(IntRange(0, 3).map { i -> Account("name " + i, "pseudo " + i, "", "") }.toMutableList())
        recyclerView.adapter = mAdapter
        //recyclerView.layoutManager.isItemPrefetchEnabled = false

        mAdapter.onClick = { account ->
            showBottomSheet(account)
        }

        mAdapter.onLongClick = { account ->
            openDetailActivity(account, AccountDetailActivity.OPERATION_SHOW)
        }

        mAdapter.onFavoriteClick = { holder, account ->
            if (NetworkStatus.isConnected ?: false) {
                working = true
                account.toggleFavorite()
                mAdapter.resetAndNotify()
                recyclerView.scrollToPosition(mAdapter.positionOf(account))
                DbxManager.saveAccounts()
                        .alwaysUi { working = false }
                        .failUi {
                            account.toggleFavorite()
                            mAdapter.resetAndNotify()
                            Toast.makeText(this, "error: " + it, Toast.LENGTH_LONG).show()
                        }
            } else {
                Timber.d("trying to update favorite when no network available.")
                Toast.makeText(this, "action not available in offline mode", Toast.LENGTH_SHORT).show()
            }
        }

        val swipeHandler = object : SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                val item = mAdapter.removeAt(viewHolder!!.adapterPosition)
                working = true

                DbxManager.saveAccounts()
                        .alwaysUi { working = false }
                        .successUi {
                            if (mTwoPane && selectedAccount == item)
                                supportFragmentManager.beginTransaction()
                                        .remove(mTwoPaneCurrentFragment!!)
                                        .commit()

                            Timber.d("removed account: %s", item)
                            Snackbar.make(fab, "Account deleted", Snackbar.LENGTH_LONG)
                                    .setAction("undo", { _ ->
                                        working = true
                                        mAdapter.add(item)
                                        DbxManager.saveAccounts()
                                                // TODO: what if it fails
                                                .alwaysUi { working = false }
                                                .failUi { showToast("Failed to undo changes !") }
                                    })
                                    .show()
                        }
                        .failUi {
                            // undo swipe !
                            mAdapter.add(item)
                            showToast("Failed to save changes")

                        }
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun showToast(msg: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this@AccountListActivity, msg, duration).show()
    }

    private fun syncWithRemote() {
        working = true
        DbxManager.fetchRemoteFileInfo().alwaysUi {
            working = false
        } successUi {
            val inSync = it
            if (!inSync) {
                syncButton.visibility = View.VISIBLE
//                Snackbar.make(fab, "Remote session changed", Snackbar.LENGTH_INDEFINITE)
//                        .setAction("Reload",
//                                { _ -> restartApp() })
//                        .show()
            }
        } failUi {
            Snackbar.make(fab, "Sync error: " + it.message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun updateConnectivityViews(connectionAvailable: Boolean) {
        mOfflineIndicator?.isVisible = !connectionAvailable
        fab.isEnabled = connectionAvailable
    }

    companion object {
        val DETAIL_ACTIVITY_REQUEST_CODE = 1984
        val SETTINGS_REQUEST_CODE = 1985
    }
}
