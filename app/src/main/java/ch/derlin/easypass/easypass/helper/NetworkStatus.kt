package ch.derlin.easypass.easypass.helper

import android.content.Context
import android.net.ConnectivityManager


object NetworkStatus {

    fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo != null && cm.activeNetworkInfo.isConnected
    }
}


