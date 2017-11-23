package ch.derlin.easypass.easypass.dropbox

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import ch.derlin.easypass.easypass.data.Account
import ch.derlin.easypass.easypass.data.Accounts
import ch.derlin.easypass.easypass.data.JsonManager
import ch.derlin.easypass.easypass.data.SessionSerialisationType
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.GetMetadataErrorException
import com.dropbox.core.v2.files.Metadata
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import java.io.FileInputStream


/**
 *
 * See https://developer.android.com/guide/components/bound-services.html#Lifecycle
 * for this service lifecycle.
 * @author Lucy Linder
 */

class DbxService : BaseDbxService() {

    private val myBinder = BBinder()
    private lateinit var broadcastManager: LocalBroadcastManager
    private lateinit var sharedPrefs: SharedPreferences

    var accounts: Accounts? = null
    var metadata: FileMetadata? = null
    var version: String? = null

    // --------------------------------------

    /**
     * BBinder for this service *
     */
    inner class BBinder : Binder() {
        /**
         * @return a reference to the bound service
         */
        val service: DbxService
            get() = this@DbxService
    }//end class


    override fun onBind(arg0: Intent): IBinder? {
        return myBinder
    }

    // --------------------------------------

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ret = super.onStartCommand(intent, flags, startId)
        broadcastManager = LocalBroadcastManager.getInstance(this)
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        instance = this
        return ret
    }
    //----------------------------------------

    companion object {
        lateinit var instance: DbxService

        const val INTENT_FILTER = "dbx_easypass_broadcast"
        const val EXTRA_EVT_KEY = "key.event_type"

        const val EVT_ERROR = "dbx_evt.error"
        const val EXTRA_MSG_KEY = "key.msg"

        const val EVT_METADATA_FETCHED = "dbx_evt.metadata_fetched"
        const val EVT_SESSION_OPENED = "dbx_evt.session_opened"
        const val EVT_SESSION_CHANGED = "dbx_evt.session_changed"
        const val EVT_UPLOAD_OK = "dbx_evt.upload_ok"

        const val DEFAULT_FILE_PATH = "/easypass.data_ser"

        const val CACHED_FILE = "easypass_cached.data_ser"
    }

    // --------------------------------------

    fun getSessionMetadata() {
        metadata = null // ensure to clear any past state
        async(CommonPool) {
            try {
                metadata = client!!.files().getMetadata(DEFAULT_FILE_PATH) as FileMetadata
            } catch (e: GetMetadataErrorException) {
                // session does not exist
                // TODO
            } finally {
                notifyEvent(EVT_METADATA_FETCHED)
            }
        }
    }

    fun saveAccounts() {
        assert(this.accounts != null)

        try {
            // serialize accounts to private file
            val fos = openFileOutput(CACHED_FILE, Context.MODE_PRIVATE)
            fos.use { out ->
                JsonManager.serialize(accounts!!.data, out, accounts!!.password)
            }

            // upload file to dropbox
            openFileInput(CACHED_FILE).use { `in` ->
                this.metadata = client!!.files()
                        .uploadBuilder(accounts!!.path)
                        .uploadAndFinish(`in`)
            }
            sharedPrefs.edit().putString("revision", metadata!!.rev).apply()
        } catch (t: Throwable) {
            Log.d("sgf", "oups " + t)
        }
    }

    fun openSession(password: String) {

        if (metadata == null) {
            accounts = Accounts(password, DEFAULT_FILE_PATH)
            sharedPrefs.edit().putString("revision", null).apply()
            notifyEvent(EVT_SESSION_OPENED)
            return
        }

        val version = sharedPrefs.getString("revision", null)
        if (version != null) {
            loadCachedFile(password)
            notifyEvent(EVT_SESSION_OPENED)
            if (metadata != null && metadata!!.rev != version)
                openSession(metadata!!, password)

        } else {
            openSession(metadata!!, password)
            Log.d("easypass", "metadata is not null, but version is ...")

        }
    }

    fun loadCachedFile(password: String) {
        deserialize(openFileInput(CACHED_FILE), DEFAULT_FILE_PATH, password)
    }

    fun openSession(sessionMeta: Metadata, password: String) {

        async(CommonPool) {
            try {

                metadata = client!!.files()
                        .download(sessionMeta.pathDisplay)
                        .download(openFileOutput(CACHED_FILE, Context.MODE_PRIVATE))

                val isUpdate = accounts != null
                deserialize(openFileInput(CACHED_FILE), metadata?.pathDisplay, password)
                sharedPrefs.edit().putString("revision", metadata!!.rev).apply()
                if (isUpdate) notifyEvent(EVT_SESSION_CHANGED)
                else notifyEvent(EVT_SESSION_OPENED)


            } catch (e: Exception) {
                Log.e("easypass", e.toString())
                notifyError(e.message ?: "error opening session.")
            }
        }
    }

    private fun deserialize(fin: FileInputStream, pathName: String?, password: String) {
        val accountList = JsonManager.deserialize(fin, password,
                object : TypeToken<SessionSerialisationType>() {
                }.type) as SessionSerialisationType

        accounts = Accounts(password, pathName ?: "??", accountList)
        accounts!!.forEach { a -> a.uid = Account.generateUid() }
    }

// --------------------------------------

    protected fun notifyError(error: String) {
        val i = getIntentFor(EVT_ERROR)
        i.putExtra(EXTRA_MSG_KEY, error)
        broadcastManager.sendBroadcast(i)
    }

    protected fun notifyEvent(eventName: String) {
        val i = getIntentFor(eventName)
        broadcastManager.sendBroadcast(i)
    }

    protected fun getIntentFor(evtType: String): Intent {
        val i = Intent(INTENT_FILTER)
        i.putExtra(EXTRA_EVT_KEY, evtType)
        return i
    }
}