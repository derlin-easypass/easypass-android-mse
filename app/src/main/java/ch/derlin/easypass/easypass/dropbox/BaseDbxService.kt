package ch.derlin.easypass.easypass.dropbox

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.DbxClientV2

import ch.derlin.easypass.easypass.R


/**
 * A basic dropbox service using the V2 API.
 * How to use:
 * - change the APP_KEY and APP_SECRET
 * - in your main activity, initialise the service by calling
 * {@ref startAuth}. If it returns true, then the linking has
 * already been done. If not, the method will launch the OAuth
 * flow. In this case, you need to call {@ref finishAuth} in the
 * activity onResume method.
 * <br></br>----------------------------------------------------<br></br>
 * Derlin - MyBooks Android, May, 2016
 *
 * @author Lucy Linder
 */
open class BaseDbxService : Service() {

    protected lateinit var dbxClientV2: DbxClientV2

    private val myBinder = BBinder()

    val client: DbxClientV2?
        get() = dbxClientV2

    private val accessToken: String?
        get() {
            val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
            return prefs.getString(getString(R.string.prefs_dbx_access_token), null)
        }

    /**
     * BBinder for this service *
     */
    inner class BBinder : Binder() {
        /**
         * @return a reference to the bound service
         */
        val service: BaseDbxService
            get() = this@BaseDbxService
    }//end class


    override fun onBind(arg0: Intent): IBinder? {
        return myBinder
    }


    // ----------------------------------------------------


    /**
     * start the authentication process. If the user is already
     * logged in, the method returns immediately. If not,
     * a dropbox activity is launched and the caller will need
     * to call {@ref finishAuth} in its onResume.
     *
     * @return true (immediate) if already authentified, won't
     * return but will launch the OAuth process otherwise.
     */
    fun startAuth(): Boolean {
        val accessToken = accessToken
        if (accessToken == null) {
            Auth.startOAuth2Authentication(this, getString(R.string.dbx_app_key))
            return false
        } else {
            initializeClient(accessToken)
            return true
        }
    }


    /**
     * finish the authentication process. Must be called in the
     * onResume method of the caller.
     */
    fun finishAuth() {

        val accessToken = Auth.getOAuth2Token() //generate Access Token
        if (accessToken != null) {
            //Store accessToken in SharedPreferences
            storeAccessToken(accessToken)
            initializeClient(accessToken)
        } else {
            Log.i("DbAuthLog", "Error authenticating")
        }
    }


    // ----------------------------------------------------


    private fun initializeClient(accessToken: String) {
        val config = DbxRequestConfig.newBuilder(getString(R.string.dbx_request_config_name)).build()
        dbxClientV2 = DbxClientV2(config, accessToken)
    }


    private fun storeAccessToken(accessToken: String) {
        val prefs = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        prefs.edit().putString(getString(R.string.prefs_dbx_access_token), accessToken).apply()
    }
}
