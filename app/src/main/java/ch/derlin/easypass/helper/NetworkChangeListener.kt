package ch.derlin.easypass.helper

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import ch.derlin.easypass.App

/**
 * Reusable listener for network change.
 *
 * To use it:
 *  1. ensure you have the [android.permission.ACCESS_NETWORK_STATE] set in the manifest
 *  2. create a new instance of this listener
 *  3. register the listener by calling [registerSelf] in the [android.app.Activity.onResume]
 *      and [unregisterSelf] in the [android.app.Activity.onPause]
 *  4. override [onNetworkChange] to respond to the events
 *
 *  date: 24.11.2017
 *  @author Lucy Linder
 */
open class NetworkChangeListener {

    // to avoid registering twice
    private var isRegistered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = checkStatus()
        override fun onLost(network: Network) = checkStatus()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            checkStatus()
    }

    // ----------------------------------------------------

    private fun checkStatus() {
        val oldStatus = NetworkStatus.isConnected
        val status = NetworkStatus.isInternetAvailable(App.appContext)
        if (oldStatus != status) {
            onNetworkChange(status)
        }
    }


    /**
     * Register this listener to start receiving events.
     *
     * @param context the context
     */
    fun registerSelf(context: Context) {
        if (isRegistered) return
        // the main looper lets onNetworkChange update views
        connectivityManager(context)
            .registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        isRegistered = true
    }


    /**
     * Unregister this listener to stop receiving events.
     *
     * @param context the context
     */
    fun unregisterSelf(context: Context) {
        if (!isRegistered) return
        connectivityManager(context).unregisterNetworkCallback(callback)
        isRegistered = false
    }


    private fun connectivityManager(context: Context) =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * callback to implement
     */
    open fun onNetworkChange(connectionAvailable: Boolean) {

    }


}
