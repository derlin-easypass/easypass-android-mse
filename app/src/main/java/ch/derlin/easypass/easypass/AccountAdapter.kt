package ch.derlin.easypass.easypass

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import ch.derlin.easypass.easypass.data.Account

/**
 * Created by Lin on 16.11.17.
 *
 * Gosh, I tried so many things in order to have a sortable, filterable AND animated
 * recycler list view...
 *
 * - SortedList with SortedList.Callback: nice, but no way to change the sorting order
 *    (we can reverse the whole list, but favorites will be at the end, see
 *    https://www.codesd.com/item/change-sort-order-with-sortedlist-and-recyclerview.html)
 * - using the notifyItemChanged, notifyItemDeleted, etc vs notifyDataChange and inserting
 *    items at the right index. But it becomes complicated when we toggle favorite...
 * - RecyclerAdapter https://github.com/gotev/recycler-adapter
 *    nice, but since we only control the items of the list (vs the adapter), it is
 *    complicated to manage the callbacks (i.e. calling the activity method on click)
 *
 * After many trial and errors, I discovered that animations can be applied automatically using
 * the regular pattern (sort, filter and notifyDataChange) if the items in the list have a unique id:
 * -> add a uid to the account + override getItemId + call setHasStableIds(true) in the adapter
 * constructor.
 *
 * I used android:animateLayoutChanges="true" in the xml, but it doesn't seem to be mandatory in
 * the end...
 */

class AccountAdapter(var accounts: MutableList<Account>) :
        RecyclerView.Adapter<AccountAdapter.ViewHolder>() {

    var comparator: Comparator<Account> = Account.nameComparator
        set(value) {
            field = value; sort()
        }

    var onCLick: View.OnClickListener? = null
    var onLongClick: View.OnLongClickListener? = null

    private var comparatorWrapper = Comparator<Account> { a1: Account, a2: Account ->
        if (a1.isFavorite == a2.isFavorite) {
            comparator.compare(a1, a2)
        } else {
            if (a1.isFavorite) -1 else 1
        }
    }

    var filtered = accounts.map { i -> i }.toMutableList()

    init {
        setHasStableIds(true)
        filtered.sortWith(comparatorWrapper)
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        onBindViewHolder(holder, position, null)
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        // create a new view
        val v: View = LayoutInflater.from(parent!!.context)
                .inflate(R.layout.account_list_content, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int, payloads: MutableList<Any>?) {
        val item = filtered[position]
        holder!!.titleView.text = item.name
        holder.subtitleView.text = item.modificationDate
        holder.favoriteIcon.setBackgroundResource(
                if (item.isFavorite) R.drawable.ic_pinned_on else R.drawable.ic_pinned_off)

        holder.favoriteIcon.setOnClickListener({ v ->
            item.isFavorite = !item.isFavorite
            sort()
        })

        holder.view.setOnClickListener(onCLick)
        holder.view.setOnLongClickListener(onLongClick)
    }

    override fun getItemCount(): Int = filtered.size

    override fun getItemId(position: Int): Long = filtered[position].uid

    fun replaceAll(accounts: MutableList<Account>){
        this.accounts = accounts
        this.filter(null)
        sort()
    }

    fun itemAtPosition(position: Int): Account = filtered[position]

    fun removeAt(position: Int) {
        accounts.removeAt(position)
        filtered.removeAt(position)
        notifyItemRemoved(position)
    }

    fun filter(search: String?) {
        filtered = if (search == null || search.isBlank()) accounts.toMutableList()
        else accounts.filter { i -> i.name.toLowerCase().contains(search) }.toMutableList()
        notifyDataSetChanged()
    }


    fun add(item: Account) {
        accounts.add(item)
        filtered.add(item)
        notifyDataSetChanged()
    }

    private fun sort() {
        filtered.sortWith(comparatorWrapper)
        notifyDataSetChanged()
    }

    // -----------------------------------------

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.account_title)
        val subtitleView: TextView = view.findViewById(R.id.account_subtitle)
        var favoriteIcon: ImageButton = view.findViewById(R.id.account_favorite)
    }


}