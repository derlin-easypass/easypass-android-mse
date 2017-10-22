package ch.derlin.easypass.easypass.dropbox

import android.content.Intent
import android.os.Binder
import android.os.IBinder


/**
 *
 * @author Lucy Linder
 */
class DbxService : BaseDbxService() {

    private val myBinder = BBinder()

    /**
     * BBinder for this service *
     */
    inner class BBinder : Binder() {
        /**
         * @return a reference to the bound service
         */
        val service: DbxService
            get() = this@DbxService
    }//end class


    override fun onBind(arg0: Intent): IBinder? {
        return myBinder
    }

    //-------------------------------------------------------------


}