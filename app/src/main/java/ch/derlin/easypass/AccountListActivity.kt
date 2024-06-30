package ch.derlin.easypass

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import ch.derlin.easypass.AccountDetailActivity.Companion.OPERATION_EDIT
import ch.derlin.easypass.AccountDetailActivity.Companion.OPERATION_SHOW
import ch.derlin.easypass.data.Account
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.easypass.databinding.ActivityAccountListBinding
import ch.derlin.easypass.helper.DbxManager
import ch.derlin.easypass.helper.MiscUtils.attrColor
import ch.derlin.easypass.helper.MiscUtils.colorizePassword
import ch.derlin.easypass.helper.MiscUtils.copyToClipBoard
import ch.derlin.easypass.helper.MiscUtils.hideKeyboard
import ch.derlin.easypass.helper.MiscUtils.restartApp
import ch.derlin.easypass.helper.MiscUtils.rootView
import ch.derlin.easypass.helper.MiscUtils.toSpannable
import ch.derlin.easypass.helper.NetworkChangeListener
import ch.derlin.easypass.helper.NetworkStatus
import ch.derlin.easypass.helper.Preferences
import ch.derlin.easypass.helper.SecureActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
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

    private lateinit var binding: ActivityAccountListBinding

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var mTwoPane: Boolean = false
    private var mTwoPaneCurrentFragment: Fragment? = null
    private var mTwoPaneSearch: String? = null
    private lateinit var mTwoPaneBackToDetail: OnBackPressedCallback

    lateinit var mAdapter: AccountAdapter
    private lateinit var searchView: SearchView
    private var mOfflineIndicator: MenuItem? = null
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var selectedAccount: Account? = null

    private val mNetworkChangeListener = object : NetworkChangeListener() {
        override fun onNetworkChange(connectionAvailable: Boolean) {
            updateConnectivityViews(connectionAvailable)
            if (connectionAvailable) {
                syncWithRemote()
            } else {
                Snackbar.make(binding.fab, "Network unavailable", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private var working: Boolean
        set(value) {
            binding.progressBar.visibility = if (value) View.VISIBLE else View.INVISIBLE
        }
        get() = binding.progressBar.visibility == View.VISIBLE


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        if (!DbxManager.isInitialized) {
            Timber.e("Dropbox accounts is not initialized in list activity !!! (accounts null)")
            return
        }

        binding.fab.setOnClickListener {
            if (NetworkStatus.isInternetAvailable()) {
                openDetailActivity(null, AccountDetailActivity.OPERATION_NEW)
            } else {
                Toast.makeText(this, "no internet connection available.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.syncButton.setOnClickListener { backToLoadingScreen() }

        setupRecyclerView(findViewById(R.id.recyclerView))
        if (findViewById<View>(R.id.accountDetailContainer) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true
        }

        if (mTwoPane) registerOnBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == DETAIL_ACTIVITY_REQUEST_CODE) {
            if (data?.getBooleanExtra(AccountDetailActivity.RETURN_MODIFIED, false) == true) {
                // update the list in case of modification
                val account = data.getParcelableExtra(
                    AccountDetailActivity.BUNDLE_ACCOUNT_KEY,
                    Account::class.java
                )
                notifyAccountUpdate(requireNotNull(account))
            }
        } else if (requestCode == SETTINGS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK
                && data?.getBooleanExtra(SettingsActivity.BUNDLE_RESTART_KEY, false) == true
            ) {
                // if asking for restart, kill current activity
                // TODO: find a better way
                restartApp()
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
        binding.syncButton.visibility =
            if (!connected || DbxManager.isInSync) View.GONE else View.VISIBLE
        updateConnectivityViews(connected)
        mNetworkChangeListener.registerSelf(this)
    }

    private fun registerOnBackPressed() {
        check(mTwoPane) {
            "Custom onBackPressed is only necessary in mTwoPane mode"
        }
        mTwoPaneBackToDetail = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                mTwoPaneBackToDetail.isEnabled = false
                if (selectedAccount != null) {
                    openDetailActivity(selectedAccount!!, OPERATION_SHOW)
                } else {
                    supportFragmentManager.beginTransaction()
                        .remove(mTwoPaneCurrentFragment as AccountEditFragment).commit()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, mTwoPaneBackToDetail)
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_list_accounts, menu)
        mOfflineIndicator = menu!!.findItem(R.id.action_offline)
        mOfflineIndicator!!.isVisible = !(NetworkStatus.isConnected)
        searchView = (menu.findItem(R.id.action_search).actionView as SearchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                hideKeyboard()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                mAdapter.filter(newText)
                mTwoPaneSearch = newText.takeUnless { it.isNullOrBlank() }
                return true
            }
        })
        val sort = Preferences.sortOrder
        menu.findItem(sort).isChecked = true

        if (mTwoPane && !mTwoPaneSearch.isNullOrEmpty()) {
            // The menu for some reason is recreated on fragment transactions.
            // Ensure the search is shown if currently filtering.
            searchView.setQuery(mTwoPaneSearch, true)
            searchView.isIconified = false
            searchView.clearFocus()
        }
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.groupId == R.id.group_menu_sort) {
            Preferences.sortOrder = item.itemId
            mAdapter.comparator = getSortOrder(item.itemId)
            item.isChecked = true
            return true
        } else {
            when (item.itemId) {
                R.id.action_settings -> startActivityForResult(
                    Intent(
                        this,
                        SettingsActivity::class.java
                    ), SETTINGS_REQUEST_CODE
                )

                R.id.action_sync -> syncWithRemote()
                else -> return super.onOptionsItemSelected(item)
            }
            return true
        }

    }

    private fun getSortOrder(itemId: Int): Comparator<Account> = when (itemId) {
        R.id.submenu_sort_title_asc -> Account.nameComparatorAsc
        R.id.submenu_sort_title_desc -> Account.nameComparatorDesc
        R.id.submenu_sort_year_asc -> Account.modifiedComparatorAsc
        R.id.submenu_sort_year_desc -> Account.modifiedComparatorDesc
        else -> Account.nameComparatorAsc
    }


    private fun showBottomSheet(item: Account) {
        selectedAccount = item
        if (searchView.hasFocus()) searchView.clearFocus()

        bottomSheetDialog = BottomSheetDialog(this).also { sheet ->
            val view = layoutInflater.inflate(R.layout.bottom_sheet_layout, null)

            view.findViewById<TextView>(R.id.bottomSheetTitle).text = item.name
            view.findViewById<Button>(R.id.copy_username_btn).let {
                it.text =
                    getString(R.string.fmt_copy_xx).format("username", item.pseudo).toSpannable()
                it.isEnabled = item.pseudo.isNotBlank()
            }
            view.findViewById<Button>(R.id.copy_email_btn).let {
                it.text = getString(R.string.fmt_copy_xx).format("email", item.email).toSpannable()
                it.isEnabled = item.email.isNotBlank()
            }
            view.findViewById<Button>(R.id.view_password_btn).isEnabled = item.password.isNotBlank()
            view.findViewById<Button>(R.id.view_edit_btn).isEnabled =
                NetworkStatus.isInternetAvailable(this)

            sheet.setContentView(view)
            sheet.show()
        }
    }

    // this is bound from the xml layout
    fun bottomSheetClicked(v: View) {
        if (selectedAccount == null) return

        when (v.id) {
            R.id.copy_pass_btn -> copyText(selectedAccount?.password, "password copied!")
            R.id.copy_username_btn -> copyText(
                selectedAccount?.pseudo,
                "'${selectedAccount!!.pseudo}' copied!"
            )

            R.id.copy_email_btn -> copyText(
                selectedAccount?.email,
                "'${selectedAccount!!.email}' copied!"
            )

            R.id.view_details_btn -> {
                bottomSheetDialog!!.dismiss()
                openDetailActivity(selectedAccount!!, OPERATION_SHOW)
            }

            R.id.view_password_btn -> {
                bottomSheetDialog!!.dismiss()
                showPassword(selectedAccount!!)
            }

            R.id.view_edit_btn -> {
                bottomSheetDialog!!.dismiss()
                openDetailActivity(selectedAccount!!, OPERATION_EDIT)
            }

            else -> Toast.makeText(this, "something clicked", Toast.LENGTH_SHORT).show()
        }

    }

    private fun showPassword(account: Account) {
        val view = layoutInflater.inflate(R.layout.show_password, null)
        view.findViewById<TextView>(R.id.show_password_textview).text =
            account.password.colorizePassword()
        view.findViewById<ImageButton>(R.id.show_password_copy_btn).setOnClickListener {
            copyText(selectedAccount?.password, "password copied!")
        }
        AlertDialog.Builder(this)
            .setView(view)
            .show()
    }

    private fun copyText(text: String?, toastDescription: String = "") {

        if (copyToClipBoard(text) && toastDescription.isNotBlank()) {
            if (bottomSheetDialog?.isShowing == true)
                Snackbar.make(
                    bottomSheetDialog!!.rootView(),
                    toastDescription,
                    Snackbar.LENGTH_SHORT
                ).show()
            else
                Toast.makeText(this, toastDescription, Toast.LENGTH_SHORT).show()
        }
    }


    fun openDetailActivity(item: Account?, operation: String): Boolean {
        if (mTwoPane) {
            hideKeyboard()
            val arguments = Bundle()
            selectedAccount = item
            mTwoPaneBackToDetail.isEnabled = (operation != OPERATION_SHOW)
            arguments.putParcelable(AccountDetailActivity.BUNDLE_ACCOUNT_KEY, item)
            (if (operation == OPERATION_SHOW) AccountDetailFragment() else AccountEditFragment()).let { fragment ->
                fragment.arguments = arguments
                mTwoPaneCurrentFragment = fragment
                supportFragmentManager.beginTransaction()
                    .replace(R.id.accountDetailContainer, fragment)
                    .commit()
            }
        } else {
            val intent = Intent(this, AccountDetailActivity::class.java).let {
                it.putExtra(AccountDetailActivity.BUNDLE_OPERATION_KEY, operation)
                it.putExtra(AccountDetailActivity.BUNDLE_ACCOUNT_KEY, item)
            }
            startActivityForResult(intent, DETAIL_ACTIVITY_REQUEST_CODE)
        }
        return true
    }

    fun notifyAccountUpdate(item: Account) {
        mAdapter.resetAndNotify()
        selectedAccount = item
        val idx = mAdapter.positionOf(item)
        if (idx >= 0) findViewById<RecyclerView>(R.id.recyclerView).scrollToPosition(idx)

        if (mTwoPane) {
            openDetailActivity(item, OPERATION_SHOW)
        }
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        mAdapter = AccountAdapter(
            DbxManager.accounts,
            defaultComparator = getSortOrder(Preferences.sortOrder),
            textviewCounter = findViewById(R.id.countText)
        )

        //mAdapter = AccountAdapter(IntRange(0, 3).map { i -> Account("name " + i, "pseudo " + i, "", "") }.toMutableList())
        recyclerView.adapter = mAdapter
        //recyclerView.layoutManager.isItemPrefetchEnabled = false

        mAdapter.onClick = { account ->
            if (mTwoPane) {
                openDetailActivity(account, OPERATION_SHOW)
            } else {
                showBottomSheet(account)
            }
        }

        mAdapter.onLongClick = { account ->
            openDetailActivity(account, OPERATION_SHOW)
        }

        mAdapter.onFavoriteClick = { _, account ->
            if (NetworkStatus.isConnected) {
                working = true
                account.toggleFavorite()
                mAdapter.resetAndNotify()
                recyclerView.scrollToPosition(mAdapter.positionOf(account))
                DbxManager.saveAccounts()
                    .alwaysUi { working = false }
                    .failUi {
                        account.toggleFavorite()
                        mAdapter.resetAndNotify()
                        Toast.makeText(this, "error: $it", Toast.LENGTH_LONG).show()
                    }
            } else {
                Timber.d("trying to update favorite when no network available.")
                Toast.makeText(this, "action not available in offline mode", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        fun createSwipeHandler(): SwipeToDeleteCallback =
            object : SwipeToDeleteCallback(this, attrColor(R.attr.colorAccent)) {

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val item = mAdapter.removeAt(viewHolder.bindingAdapterPosition)
                    working = true

                    DbxManager.saveAccounts()
                        .alwaysUi { working = false }
                        .successUi {
                            if (mTwoPane && selectedAccount == item)
                                supportFragmentManager.beginTransaction()
                                    .remove(mTwoPaneCurrentFragment!!)
                                    .commit()

                            Timber.d("removed account: %s", item)
                            Snackbar.make(binding.fab, "Account deleted", Snackbar.LENGTH_LONG)
                                .setAction("undo") {
                                    working = true
                                    mAdapter.add(item)
                                    DbxManager.saveAccounts()
                                        // TODO: what if it fails
                                        .alwaysUi { working = false }
                                        .failUi { showToast("Failed to undo changes !") }
                                }
                                .show()
                        }
                        .failUi {
                            // undo swipe !
                            mAdapter.add(item)
                            showToast("Failed to save changes")

                        }
                }
            }

        val itemTouchHelper = ItemTouchHelper(createSwipeHandler())
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
            binding.syncButton.visibility = if (DbxManager.isInSync) View.GONE else View.VISIBLE
        } failUi {
            Snackbar.make(binding.fab, "Sync error: " + it.message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun updateConnectivityViews(connectionAvailable: Boolean) {
        mOfflineIndicator?.isVisible = !connectionAvailable
    }

    companion object {
        const val DETAIL_ACTIVITY_REQUEST_CODE = 1984
        const val SETTINGS_REQUEST_CODE = 1985
    }
}
