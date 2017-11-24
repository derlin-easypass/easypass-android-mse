package ch.derlin.easypass.easypass.dropbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.support.v4.content.LocalBroadcastManager


open class NetworkChangeListener : BroadcastReceiver() {

    private var isRegistered = false

    companion object {
        private var INTENT_FILTER: IntentFilter

        init {
            INTENT_FILTER = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        }
    }

    // ----------------------------------------------------


    override fun onReceive(context: Context, intent: Intent) {
        onNetworkChange()
    }


    /**
     * Register this receiver to the local broadcast manager to start receiving events.
     *
     * @param context the context
     */
    fun registerSelf(context: Context) {
        if (isRegistered) return
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


    /**
     * callback to implement
     */
    open fun onNetworkChange() {

    }


}
