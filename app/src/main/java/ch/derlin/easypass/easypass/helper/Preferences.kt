package ch.derlin.easypass.easypass.helper

import android.content.Context
import ch.derlin.easypass.easypass.App

class Preferences(context: Context = App.appContext) {

    private val PREFERENCES_FILENAME = "ch.derlin.easypass.preferences"

    val sharedPrefs = context.getSharedPreferences(PREFERENCES_FILENAME, Context.MODE_PRIVATE)

    var dbxAccessToken: String?
        get() = sharedPrefs.getString("dbx_access-token", null)
        set(value) = sharedPrefs.edit().putString("dbx_access-token", value).apply()

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