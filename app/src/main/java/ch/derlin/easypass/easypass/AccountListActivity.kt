package ch.derlin.easypass.easypass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.support.design.widget.FloatingActionButton
import android.view.View
import ch.derlin.easypass.easypass.data.Account
import android.support.design.widget.BottomSheetDialog
import android.support.v7.widget.helper.ItemTouchHelper
import android.widget.Toast
import ch.derlin.easypass.easypass.dropbox.NetworkChangeListener
import ch.derlin.easypass.easypass.dropbox.DbxManager
import kotlinx.android.synthetic.main.account_list.*
import kotlinx.android.synthetic.main.activity_account_list.*


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

    private var bottomSheetDialog: BottomSheetDialog? = null

    private var selectedAccount: Account? = null

    private val mNetworkChangeListener = object : NetworkChangeListener() {
        override fun onNetworkChange() {
            // TODO
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_list)

        fab.setOnClickListener { view ->
            newAccount()
            // Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
            //         .setAction("Action", null).show()
        }

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
        mNetworkChangeListener.registerSelf(this)
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
            Toast.makeText(this, toastDescription, Toast.LENGTH_SHORT).show()
        }
    }

    private fun newAccount(): Boolean {
        val context = this
        val intent = Intent(context, AccountNewActivity::class.java)
        context.startActivity(intent)
        return true
    }

    private fun showDetails(item: Account): Boolean {
        if (mTwoPane) {
            val arguments = Bundle()
            arguments.putParcelable(AccountDetailFragment.ARG_ACCOUNT, item)
            val fragment = AccountDetailFragment()
            fragment.arguments = arguments
            supportFragmentManager.beginTransaction()
                    .replace(R.id.accountDetailContainer, fragment)
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
                mAdapter.removeAt(viewHolder!!.adapterPosition)
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)

    }

}
