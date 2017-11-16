package ch.derlin.easypass.easypass.dropbox

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import ch.derlin.easypass.easypass.data.Account
import ch.derlin.easypass.easypass.data.Accounts
import ch.derlin.easypass.easypass.data.JsonManager
import ch.derlin.easypass.easypass.data.SessionSerialisationType
import com.dropbox.core.v2.files.GetMetadataErrorException
import com.dropbox.core.v2.files.Metadata
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.HashMap
import com.dropbox.core.v2.files.FileMetadata


/**
 *
 * See https://developer.android.com/guide/components/bound-services.html#Lifecycle
 * for this service lifecycle.
 * @author Lucy Linder
 */

class DbxService : BaseDbxService() {

    private val myBinder = BBinder()
    private lateinit var broadcastManager: LocalBroadcastManager

    var accounts: Accounts? = null
    var metadata: Metadata? = null

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
        const val EVT_UPLOAD_OK = "dbx_evt.upload_ok"

        const val DEFAULT_FILE_PATH = "/easypass.data_ser"
    }

    // --------------------------------------

    fun getSessionMetadata() {
        metadata = null // ensure to clear any past state
        async(CommonPool) {
            try {
                metadata = client!!.files().getMetadata(DEFAULT_FILE_PATH)
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
            // serialize accounts to tempFile
            val file = File.createTempFile("seralization", "data_ser")
            file.outputStream().use { out ->
                JsonManager.serialize(accounts!!.data, out, accounts!!.password)
            }

            // upload file to dropbox
            FileInputStream(file).use { `in` ->
                this.metadata = client!!.files().uploadBuilder(accounts!!.path)
                        .uploadAndFinish(`in`)
            }
        } catch (t: Throwable) {
            Log.d("sgf", "merde " + t)
        }
    }

    fun openSession(password: String) {
        if (metadata == null) {
            accounts = Accounts(password, DEFAULT_FILE_PATH)
            notifyEvent(EVT_SESSION_OPENED)
        } else {
            openSession(metadata!!, password)
        }
    }

    fun openSession(sessionMeta: Metadata, password: String) {

        async(CommonPool) {
            try {

                val file = File.createTempFile(sessionMeta.name, "data_ser")
                client!!.files()
                        .download(sessionMeta.pathDisplay)
                        .download(FileOutputStream(file))

                val accountList = JsonManager.deserialize(FileInputStream(file), password,
                        object : TypeToken<SessionSerialisationType>() {
                        }.type) as SessionSerialisationType

                accounts = Accounts(password, sessionMeta.pathDisplay, accountList)
                accounts!!.forEach { a -> a.uid = Account.generateUid() }
                notifyEvent(EVT_SESSION_OPENED)

            } catch (e: Exception) {
                notifyError(e.message ?: "error opening session.")
            }
        }
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