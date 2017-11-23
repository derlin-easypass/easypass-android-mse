package ch.derlin.easypass.easypass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.view.View
import ch.derlin.easypass.easypass.data.Account
import ch.derlin.easypass.easypass.dropbox.DbxService
import android.support.design.widget.BottomSheetDialog
import android.support.v7.widget.helper.ItemTouchHelper
import android.widget.Toast
import ch.derlin.easypass.easypass.dropbox.DbxBroadcastReceiver
import java.util.*


/**
 * An activity representing a list of Accounts. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [AccountDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
class AccountListActivity : AppCompatActivity() {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var mTwoPane: Boolean = false

    lateinit var mAdapter: AccountAdapter

    lateinit var mFab: FloatingActionButton

    private var bottomSheetDialog: BottomSheetDialog? = null

    private var selectedAccount: Account? = null

    private val mBroadcastReceiver = object : DbxBroadcastReceiver() {
        override fun onSessionChanged() {
            mAdapter.replaceAll(DbxService.instance.accounts!!.toMutableList())
            Snackbar.make(mFab, "Session updated", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_list)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        toolbar.title = title

        mFab = findViewById(R.id.fab) as FloatingActionButton
        mFab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        val recyclerView = findViewById(R.id.account_list)!! as RecyclerView
        setupRecyclerView(recyclerView)

        if (findViewById(R.id.account_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true
        }
    }

    override fun onPause() {
        super.onPause()
        mBroadcastReceiver.unregisterSelf(this)
    }

    override fun onResume() {
        super.onResume()
        mBroadcastReceiver.registerSelf(this)
    }

    private fun showBottomSheet(item: Account) {
        selectedAccount = item
        bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_layout, null)
        bottomSheetDialog!!.setContentView(view)
        bottomSheetDialog!!.show()
    }

    // this is bound from the xml layout
    fun bottomSheetClicked(v: View) {
        if (selectedAccount == null) return;
        when (v.id) {
            R.id.copy_pass_btn -> copyToClipboard(selectedAccount!!.password)
            R.id.copy_username_btn -> copyToClipboard(selectedAccount!!.pseudo)
            R.id.view_details_btn -> showDetails(selectedAccount!!)
            else -> Toast.makeText(this, "something clicked", Toast.LENGTH_SHORT).show()
        }

        if (bottomSheetDialog != null) bottomSheetDialog!!.hide()
    }

    private fun copyToClipboard(text: String, toastDescription: String = "") {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip = ClipData.newPlainText("easypass", text)
        if (toastDescription != "") {
            Toast.makeText(this, toastDescription, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDetails(item: Account): Boolean {
        if (mTwoPane) {
            val arguments = Bundle()
            arguments.putParcelable(AccountDetailFragment.ARG_ACCOUNT, item)
            val fragment = AccountDetailFragment()
            fragment.arguments = arguments
            supportFragmentManager.beginTransaction()
                    .replace(R.id.account_detail_container, fragment)
                    .commit()
        } else {
            val context = this
            val intent = Intent(context, AccountDetailActivity::class.java)
            intent.putExtra(AccountDetailFragment.ARG_ACCOUNT, item)

            context.startActivity(intent)
        }
        return true
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        mAdapter = AccountAdapter(DbxService.instance.accounts!!)
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
                mAdapter.removeAt(viewHolder!!.adapterPosition)
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)

    }

}
