package ch.derlin.easypass.easypass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.BottomSheetDialog
import android.support.design.widget.Snackbar
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
import ch.derlin.easypass.easypass.helper.MiscUtils.dismissKeyboard
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

    lateinit var mAdapter: AccountAdapter
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

    private var progressbarVisible: Boolean
        set(value) = progressBar.setVisibility(if (value) View.VISIBLE else View.INVISIBLE)
        get() = progressBar.visibility == View.VISIBLE


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_list)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            newAccount()
            // Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
            //         .setAction("Action", null).show()
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

    override fun onPause() {
        super.onPause()
        mNetworkChangeListener.unregisterSelf(this)
    }

    override fun onResume() {
        super.onResume()
        val connected = NetworkStatus.isInternetAvailable(this)
        syncButton.visibility = if (!connected || DbxManager.isInSync) View.GONE else View.VISIBLE
        updateConnectivityViews(connected)
        mNetworkChangeListener.registerSelf(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_list_accounts, menu)
        mOfflineIndicator = menu!!.findItem(R.id.action_offline)
        mOfflineIndicator!!.isVisible = !(NetworkStatus.isConnected ?: false)
        (menu.findItem(R.id.action_search).actionView as SearchView)
                .setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        // TODO
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        mAdapter.filter(newText)
                        return true
                    }
                })
        val sort = Preferences(this).sortOrder
        menu.findItem(sort).isChecked = true
        setSortOrder(sort)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.groupId == R.id.group_menu_sort) {
            Preferences(this).sortOrder = item.itemId
            setSortOrder(item.itemId)
            item?.isChecked = true
        }
        return super.onOptionsItemSelected(item);
    }

    private fun setSortOrder(itemId: Int) {
        when (itemId) {
            R.id.submenu_sort_title_asc -> mAdapter.comparator = Account.nameComparatorAsc
            R.id.submenu_sort_title_desc -> mAdapter.comparator = Account.nameComparatorDesc
            R.id.submenu_sort_year_asc -> mAdapter.comparator = Account.modifiedComparatorAsc
            R.id.submenu_sort_year_desc -> mAdapter.comparator = Account.modifiedComparatorDesc
        }
    }

    private fun showBottomSheet(item: Account) {
        selectedAccount = item
        bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_layout, null)

        view.findViewById<TextView>(R.id.bottomSheetTitle).text = item.name

        var tv = view.findViewById<Button>(R.id.copy_username_btn)
        tv.text = MiscUtils.toSpannable(getString(R.string.fmt_copy_xx).format("USERNAME", item.pseudo))
        tv.isEnabled = item.pseudo.isNotBlank()

        tv = view.findViewById<Button>(R.id.copy_email_btn)
        tv.text = MiscUtils.toSpannable(getString(R.string.fmt_copy_xx).format("EMAIL", item.email))
        tv.isEnabled = item.email.isNotBlank()

        dismissKeyboard()
        bottomSheetDialog!!.setContentView(view)
        bottomSheetDialog!!.show()
    }

    // this is bound from the xml layout
    fun bottomSheetClicked(v: View) {
        if (selectedAccount == null) return;
        when (v.id) {
            R.id.copy_pass_btn -> copyToClipboard(selectedAccount!!.password, "password copied!")
            R.id.copy_username_btn -> copyToClipboard(selectedAccount!!.pseudo, "'${selectedAccount!!.pseudo}' copied!")
            R.id.copy_email_btn -> copyToClipboard(selectedAccount!!.email, "'${selectedAccount!!.email}' copied!")
            R.id.view_details_btn -> {
                bottomSheetDialog!!.dismiss()
                showDetails(selectedAccount!!)
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

    private fun newAccount(): Boolean {
        if (mTwoPane) {
            val arguments = Bundle()
            val fragment = AccountEditFragment()
            fragment.arguments = arguments
            supportFragmentManager.beginTransaction()
                    .replace(R.id.accountDetailContainer, fragment)
                    .commit()
        } else {
            val context = this
            val intent = Intent(context, AccountDetailActivity::class.java)
            intent.putExtra("operation","new");
            context.startActivity(intent)
        }
        return true
    }

    private fun editAccount(item: Account): Boolean {
        if (mTwoPane) {
            val arguments = Bundle()
            arguments.putParcelable("account", item)
            val fragment = AccountEditFragment()
            fragment.arguments = arguments
            supportFragmentManager.beginTransaction()
                    .replace(R.id.accountDetailContainer, fragment)
                    .commit()
        } else {
            val context = this
            val intent = Intent(context, AccountDetailActivity::class.java)
            intent.putExtra("operation","edit");
            intent.putExtra("account", item)
            context.startActivity(intent)
        }
        return true
    }

    private fun showDetails(item: Account): Boolean {
        if (mTwoPane) {
            val arguments = Bundle()
            arguments.putParcelable("account", item)
            val fragment = AccountDetailFragment()
            fragment.arguments = arguments
            supportFragmentManager.beginTransaction()
                    .replace(R.id.accountDetailContainer, fragment)
                    .commit()
        } else {
            val context = this
            val intent = Intent(context, AccountDetailActivity::class.java)
            intent.putExtra("operation","show");
            intent.putExtra("account", item)
            context.startActivity(intent)
        }
        return true
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        mAdapter = AccountAdapter(DbxManager.accounts!!)
        //mAdapter = AccountAdapter(IntRange(0, 3).map { i -> Account("name " + i, "pseudo " + i, "", "") }.toMutableList())
        recyclerView.adapter = mAdapter
        //recyclerView.layoutManager.isItemPrefetchEnabled = false

        mAdapter.onCLick = View.OnClickListener { v ->
            val position = recyclerView.getChildAdapterPosition(v)
            showBottomSheet(mAdapter.itemAtPosition(position))
        }

        mAdapter.onLongClick = View.OnLongClickListener { v ->
            val position = recyclerView.getChildAdapterPosition(v)
            showDetails(mAdapter.itemAtPosition(position))
        }

        val swipeHandler = object : SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                val item = mAdapter.removeAt(viewHolder!!.adapterPosition)
                progressbarVisible = true

                DbxManager.saveAccounts()
                        .alwaysUi { progressbarVisible = false }
                        .successUi {
                            Timber.d("removed account: %s", item)
                            Snackbar.make(fab, "Account deleted", Snackbar.LENGTH_LONG)
                                    .setAction("undo", { _ ->
                                        mAdapter.add(item)
                                        progressbarVisible = true
                                        DbxManager.saveAccounts()
                                                // TODO: what if it fails
                                                .alwaysUi { progressBar.visibility = View.INVISIBLE }
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
        progressbarVisible = true
        DbxManager.fetchRemoteFileInfo().successUi {
            val inSync = it
            if (!inSync) {
                syncButton.visibility = View.VISIBLE
//                Snackbar.make(fab, "Remote session changed", Snackbar.LENGTH_INDEFINITE)
//                        .setAction("Reload",
//                                { _ -> restartApp() })
//                        .show()
            }
        } alwaysUi {
            progressbarVisible = false
        }
    }

    private fun updateConnectivityViews(connectionAvailable: Boolean) {
        mOfflineIndicator?.isVisible = !connectionAvailable
        fab.isEnabled = connectionAvailable
    }
}
