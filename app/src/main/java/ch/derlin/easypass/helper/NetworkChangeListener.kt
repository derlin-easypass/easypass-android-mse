package ch.derlin.easypass.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import ch.derlin.easypass.App

/**
 * Reusable listener for network change.
 *
 * To use it:
 *  1. ensure you have the [android.permission.ACCESS_NETWORK_STATE] set in the manifest
 *  2. create a new instance of this receiver
 *  3. register the receiver by calling [registerSelf] in the [android.app.Activity.onResume]
 *      and [unregisterSelf] in the [android.app.Activity.onPause]
 *  4. override [onNetworkChange] to respond to the events
 *
 *  date: 24.11.2017
 *  @author Lucy Linder
 */
open class NetworkChangeListener : BroadcastReceiver() {

    // to avoid registering twice
    private var isRegistered = false

    companion object {
        private var INTENT_FILTER: IntentFilter =
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
    }

    // ----------------------------------------------------

    override fun onReceive(context: Context, intent: Intent) {
        val oldStatus = NetworkStatus.isConnected
        val status = NetworkStatus.isInternetAvailable(App.appContext)
        if (oldStatus != status) {
            onNetworkChange(status)
        }
    }


    /**
     * Register this receiver to the broadcast manager to start receiving events.
     *
     * @param context the context
     */
    fun registerSelf(context: Context) {
        if (isRegistered) return
        context.registerReceiver(this, INTENT_FILTER)
        isRegistered = true
    }


    /**
     * Unregister this receiver from the broadcast manager to stop receiving events.
     *
     * @param context the context
     */
    fun unregisterSelf(context: Context) {
        context.unregisterReceiver(this)
        isRegistered = false
    }


    /**
     * callback to implement
     */
    open fun onNetworkChange(connectionAvailable: Boolean) {

    }


}
