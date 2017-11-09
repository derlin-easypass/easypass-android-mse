package ch.derlin.easypass.easypass.dropbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.content.LocalBroadcastManager
import ch.derlin.easypass.easypass.dropbox.DbxService.Companion.EVT_ERROR
import ch.derlin.easypass.easypass.dropbox.DbxService.Companion.EVT_METADATA_FETCHED
import ch.derlin.easypass.easypass.dropbox.DbxService.Companion.EVT_SESSION_OPENED
import ch.derlin.easypass.easypass.dropbox.DbxService.Companion.EVT_UPLOAD_OK
import ch.derlin.easypass.easypass.dropbox.DbxService.Companion.EXTRA_EVT_KEY
import ch.derlin.easypass.easypass.dropbox.DbxService.Companion.EXTRA_MSG_KEY

/**
 * Simplify the communication with the [DbxService] broadcast manager.
 * How to use:
 * - instantiate a new DbxBroadcastReceiver in a private variable
 * and override the method which you will use.
 * - call {@ref registerReceiver} in the onResume method and
 * {@ref unregisterReceiver} in the onPause method.
 * <br></br>----------------------------------------------------<br></br>
 * Derlin - MyBooks Android, May, 2016
 *
 * @author Lucy Linder
 */
open class DbxBroadcastReceiver : BroadcastReceiver() {

    private var isRegistered = false

    companion object {
        private val INTENT_FILTER = IntentFilter(DbxService.INTENT_FILTER)
    }

    // ----------------------------------------------------


    override fun onReceive(context: Context, intent: Intent) {
        val evtType = intent.getStringExtra(EXTRA_EVT_KEY)

        when (evtType) {
            EVT_ERROR -> onError(intent.getStringExtra(EXTRA_MSG_KEY))
            EVT_SESSION_OPENED -> onSessionOpened()
            EVT_METADATA_FETCHED -> onMetaFetched()
            EVT_UPLOAD_OK -> onUploadOk()
        }
    }


    /**
     * Register this receiver to the local broadcast manager to start receiving events.
     *
     * @param context the context
     */
    fun registerSelf(context: Context) {
        if(isRegistered) return
        LocalBroadcastManager.getInstance(context).registerReceiver(this, INTENT_FILTER)
        isRegistered = true
    }


    /**
     * Unregister this receiver from the local broadcast manager to stop receiving events.
     *
     * @param context the context
     */
    fun unregisterSelf(context: Context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
        isRegistered = false
    }

    // ----------------------------------------------------


    open fun onError(msg: String) {
        // pass
    }


    open fun onUploadOk() {
        // pass
    }

    open fun onMetaFetched() {
        // pass
    }

    open fun onSessionOpened() {
        // pass
    }


}
