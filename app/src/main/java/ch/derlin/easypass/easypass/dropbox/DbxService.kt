package ch.derlin.easypass.easypass.dropbox

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.support.v4.content.LocalBroadcastManager
import ch.derlin.easypass.easypass.data.Accounts
import ch.derlin.easypass.easypass.data.JsonManager
import ch.derlin.easypass.easypass.data.SessionSerialisationType
import com.dropbox.core.v2.files.Metadata
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.HashMap


/**
 *
 * @author Lucy Linder
 */

class DbxService : BaseDbxService() {

    private val myBinder = BBinder()
    private lateinit var broadcastManager: LocalBroadcastManager

    var accounts: Accounts? = null

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

        const val EVT_SESSION_OPENED = "dbx_evt.session_opened"
        const val EXTRA_SESSION_NAME = "key.session_name"

        const val EVT_UPLOAD_OK = "dbx_evt.upload_ok"
    }

    // --------------------------------------

    fun openSession(sessionMeta: Metadata, password: String) {
        async(CommonPool) {
            try {

                val file = File.createTempFile(sessionMeta.name, "data_ser")
                client!!.files()
                        .download(sessionMeta.pathDisplay)
                        .download(FileOutputStream(file))

                val accountList = JsonManager().deserialize(FileInputStream(file), password,
                        object : TypeToken<SessionSerialisationType>() {
                        }.type) as SessionSerialisationType

                accounts = Accounts(password, sessionMeta.pathDisplay, accountList)
                notifySessionOpened(sessionMeta.name)

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

    protected fun notifySessionOpened(sessionName: String) {
        val i = getIntentFor(EVT_SESSION_OPENED)
        i.putExtra(EXTRA_SESSION_NAME, sessionName)
        broadcastManager.sendBroadcast(i)
    }

    protected fun getIntentFor(evtType: String): Intent {
        val i = Intent(INTENT_FILTER)
        i.putExtra(EXTRA_EVT_KEY, evtType)
        return i
    }
}