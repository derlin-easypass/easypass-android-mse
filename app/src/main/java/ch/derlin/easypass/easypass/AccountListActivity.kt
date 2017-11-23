package ch.derlin.easypass.easypass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ch.derlin.easypass.easypass.data.Account
import ch.derlin.easypass.easypass.data.Accounts
import ch.derlin.easypass.easypass.dropbox.DbxService
import android.support.design.widget.BottomSheetDialog
import android.widget.Toast
import android.content.Context.CLIPBOARD_SERVICE




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

    private var bottomSheetDialog: BottomSheetDialog? = null

    private var selectedAccount: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_list)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        toolbar.title = title

        val fab = findViewById(R.id.fab) as FloatingActionButton
        fab.setOnClickListener { view -> newAccount()
            // Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
            //         .setAction("Action", null).show()
        }

        val recyclerView = findViewById(R.id.account_list)!!
        setupRecyclerView(recyclerView as RecyclerView)

        if (findViewById(R.id.account_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true
        }
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

        if(bottomSheetDialog != null) bottomSheetDialog!!.hide()
    }

    private fun copyToClipboard(text: String, toastDescription: String = ""){
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip = ClipData.newPlainText("easypass", text)
        if(toastDescription != "") {
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
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(DbxService.instance.accounts!!)
    }

    inner class SimpleItemRecyclerViewAdapter(private val mValues: Accounts) :
            RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.account_list_content, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.mItem = mValues[position]
            holder.mIdView.text = mValues[position].name
            holder.mContentView.text = mValues[position].notes

//            holder.mView.setOnClickListener { v ->
//                if (mTwoPane) {
//                    val arguments = Bundle()
//                    arguments.putParcelable(AccountDetailFragment.ARG_ACCOUNT, holder.mItem)
//                    val fragment = AccountDetailFragment()
//                    fragment.arguments = arguments
//                    supportFragmentManager.beginTransaction()
//                            .replace(R.id.account_detail_container, fragment)
//                            .commit()
//                } else {
//                    val context = v.context
//                    val intent = Intent(context, AccountDetailActivity::class.java)
//                    intent.putExtra(AccountDetailFragment.ARG_ACCOUNT, holder.mItem)
//
//                    context.startActivity(intent)
//                }
//            }
            holder.mView.setOnClickListener { v -> showBottomSheet(holder.mItem!!) }
            holder.mView.setOnLongClickListener { v -> showDetails(holder.mItem!!) }
        }

        override fun getItemCount(): Int {
            return mValues.size
        }

        inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
            val mIdView: TextView
            val mContentView: TextView
            var mItem: Account? = null

            init {
                mIdView = mView.findViewById<View>(R.id.id) as TextView
                mContentView = mView.findViewById<View>(R.id.content) as TextView
            }

            override fun toString(): String {
                return super.toString() + " '" + mContentView.text + "'"
            }
        }
    }
}
