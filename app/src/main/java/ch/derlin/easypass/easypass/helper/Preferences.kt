package ch.derlin.easypass.easypass.helper

import android.content.Context
import ch.derlin.easypass.easypass.App
import ch.derlin.easypass.easypass.R

class Preferences(context: Context = App.appContext) {

    private val PREFERENCES_FILENAME = "ch.derlin.easypass.preferences"

    val sharedPrefs = context.getSharedPreferences(PREFERENCES_FILENAME, Context.MODE_PRIVATE)

    var dbxAccessToken: String?
        get() = sharedPrefs.getString("dbx_access-token", null)
        set(value) { sharedPrefs.edit().putString("dbx_access-token", value).commit() }

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

    var sortOrder: Int
        get() = App.appContext.resources.getIdentifier(
                sharedPrefs.getString("sortOrder", "submenu_sort_title_asc"),
                "id", App.appContext.packageName)
        set(value) = sharedPrefs.edit().putString("sortOrder", App.appContext.resources.getResourceName(value)).apply()

    var specialChars: String
        get() = sharedPrefs.getString("generatorSpecialChars", PasswordGenerator.allSpecialChars)
        set(value) = sharedPrefs.edit().putString("generatorSpecialChars", value).apply()

    var introDone: Boolean
        get() = sharedPrefs.getBoolean("init_done", false)
        set(value) = sharedPrefs.edit().putBoolean("init_done", value).apply()
}