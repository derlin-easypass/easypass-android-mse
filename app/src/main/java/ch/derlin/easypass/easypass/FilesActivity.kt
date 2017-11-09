package ch.derlin.easypass.easypass

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import ch.derlin.easypass.easypass.dropbox.DbxBroadcastReceiver
import ch.derlin.easypass.easypass.dropbox.DbxService
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.ListFolderResult
import com.dropbox.core.v2.files.Metadata
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.jetbrains.anko.coroutines.experimental.Ref
import org.jetbrains.anko.coroutines.experimental.asReference
import org.jetbrains.anko.coroutines.experimental.bg
import java.text.SimpleDateFormat

class FilesActivity : AppCompatActivity() {

    private lateinit var listview: ListView
    private lateinit var listAdapter: ListAdapter

    private val mContext: Context = this
    private lateinit var fab: FloatingActionButton

    private val broadcastReceiver = object : DbxBroadcastReceiver() {
        override fun onError(msg: String) {
            Snackbar.make(fab, msg, Snackbar.LENGTH_SHORT).show()
        }

        override fun onSessionOpened() {
            val intent = Intent(mContext, AccountListActivity::class.java)
            startActivity(intent)
        }
    }
    // --------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        fab = findViewById(R.id.fab) as FloatingActionButton
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        loadFiles()

    }

    override fun onResume() {
        broadcastReceiver.registerSelf(this)
        super.onResume()
    }


    override fun onPause() {
        broadcastReceiver.unregisterSelf(this)
        super.onPause()
    }

    // --------------------------------------

    fun loadFiles() {
        val ref: Ref<FilesActivity> = this.asReference()
        async(UI) {
            val deferred: Deferred<ListFolderResult> = bg {
                DbxService.instance!!.client!!.files().listFolder("")
            }
            ref().setupList(deferred.await())
        }
    }

    private fun setupList(results: ListFolderResult) {
        listview = findViewById(R.id.list_files) as ListView
        listAdapter = FilesAdapter(mContext, results.entries)
        listview.adapter = listAdapter
        listview.setOnItemClickListener { adapterView, view, position, id ->
            onItemSelected(listAdapter.getItem(position) as Metadata)
        }
    }

    private fun onItemSelected(metadata: Metadata) {
        DbxService.instance!!.openSession(metadata, "essai")
    }

    class FilesAdapter(context: Context, files: List<Metadata>, val resourceId: Int = android.R.layout.simple_list_item_2) :
            ArrayAdapter<Metadata>(context, resourceId, files) {

        val formatter = SimpleDateFormat("dd/MM/yyyy hh:mm")

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = getItem(position)
            var cView = convertView
            if (cView == null)
                cView = LayoutInflater.from(context).inflate(resourceId, parent, false)

            val modified = (item as FileMetadata).clientModified
            cView!!.findViewById<TextView>(android.R.id.text1).setText(item.name.replace(".data_ser", ""))
            cView!!.findViewById<TextView>(android.R.id.text2).setText(formatter.format(modified))

            return cView
        }
    }


}
