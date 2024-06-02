package ch.derlin.easypass.helper

import android.content.Context
import android.content.SharedPreferences
import ch.derlin.easypass.App

/**
 * Simplify the manipulation of the app's [SharedPreferences].
 * @author Lucy Linder
 */
object Preferences {
    private val sharedPrefs: SharedPreferences =
        App.appContext.getSharedPreferences("ch.derlin.easypass.preferences", Context.MODE_PRIVATE)

    /** Default session path in Dropbox */
    const val DEFAULT_REMOTE_FILE_PATH = "easypass.data_ser" // Default session path in Dropbox

    /** The OAuth token for accessing Dropbox, if any. */
    var dbxAccessToken: String?
        get() = sharedPrefs.getString("dbx_access-token", null)
        set(value) {
            sharedPrefs.edit().putString("dbx_access-token", value).commit()
        }

    /** The local cached file revision. */
    var revision: String?
        get() = sharedPrefs.getString("revision", null)
        set(value) = sharedPrefs.edit().putString("revision", value).apply()

    /** Is the Android Keystore initialised, i.e. is the AES key already created ? */
    var keystoreInitialised: Boolean
        get() = sharedPrefs.getBoolean("keystore_initialised", false)
        set(value) = sharedPrefs.edit().putBoolean("keystore_initialised", value).apply()

    /** The encrypted password + IV (base64), if present. */
    var cachedPassword: String?
        get() = sharedPrefs.getString("asdf", null)
        set(value) =
            if (value == null) sharedPrefs.edit().remove("asdf").apply()
            else sharedPrefs.edit().putString("asdf", value).apply()

    /** Path of the session in Dropbox */
    var remoteFilePath: String
        get() = "/" + sharedPrefs.getString("remote_filepath", DEFAULT_REMOTE_FILE_PATH)
        set(value) =
            if (value == "") sharedPrefs.edit().remove("remote_filepath").apply()
            else sharedPrefs.edit().putString("remote_filepath", value).apply()

    /** Same as [remoteFilePath], but without the starting slash. */
    val remoteFilePathDisplay: String
        get() = remoteFilePath.replaceFirst("/", "")

    /**
     * The sort order to use when displaying the list of accounts.
     * This is an int corresponding to the resource ID of a menu item (see menu_main.xml)
     */
    var sortOrder: Int
        get() = App.appContext.resources.getIdentifier(
            sharedPrefs.getString("sortOrder", "submenu_sort_title_asc"),
            "id", App.appContext.packageName
        )
        set(value) = sharedPrefs.edit()
            .putString("sortOrder", App.appContext.resources.getResourceName(value)).apply()

    /** The list of special characters to use for the generation of password. */
    var specialChars: String
        get() = sharedPrefs.getString("generatorSpecialChars", null)
            ?: PasswordGenerator.ALL_SPECIAL_CHARS
        set(value) = sharedPrefs.edit().putString("generatorSpecialChars", value).apply()

    /** Are the intro slides already displayed once ? */
    var introDone: Boolean
        get() = sharedPrefs.getBoolean("init_done", false)
        set(value) = sharedPrefs.edit().putBoolean("init_done", value).apply()

    /** Keep track of the version to show changelog dialog on update */
    var versionCode: Int
        get() = sharedPrefs.getInt("version_code", 0)
        set(value) = sharedPrefs.edit().putInt("version_code", value).apply()

}