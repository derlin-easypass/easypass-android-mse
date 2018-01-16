package ch.derlin.easypass.helper

import android.content.Context
import android.net.ConnectivityManager
import ch.derlin.easypass.App


/**
 * This object let's you easily query the network state from anywhere in the app.
 *
 * @author Lucy Linder
 */
object NetworkStatus {

    /** The connectivity status on the last query */
    var isConnected: Boolean? = null

    /** Ask the system for connectivity status. The result is cached in [isConnected] as well. */
    fun isInternetAvailable(context: Context = App.appContext): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        isConnected = cm.activeNetworkInfo != null && cm.activeNetworkInfo.isConnected
        return isConnected!!
    }
}


