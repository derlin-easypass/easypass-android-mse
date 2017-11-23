package ch.derlin.easypass.easypass.dropbox

import android.content.Context
import android.net.ConnectivityManager
import android.preference.PreferenceManager


object NetworkStatus {

    fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo != null && cm.activeNetworkInfo.isConnected
    }
}


class Preferences(context: Context) {

    private val PREFERENCES_FILENAME = "ch.derlin.easypass.preferences"

    val sharedPrefs = context.getSharedPreferences(PREFERENCES_FILENAME, Context.MODE_PRIVATE)

    var revision: String?
        get() = sharedPrefs.getString("revision", null)
        set(value) = sharedPrefs.edit().putString("revision", value).apply()

    var keysoreInitialised: Boolean
        get() = sharedPrefs.getBoolean("keystore_initialised", false)
        set(value) = sharedPrefs.edit().putBoolean("keystore_initialised", value).apply()

    var cachedPassword: String?
        get() = sharedPrefs.getString("asdf", null)
        set(value) =
            if (value == null) sharedPrefs.edit().remove("asdf").apply()
            else sharedPrefs.edit().putString("asdf", value).apply()

}