package ch.derlin.easypass.easypass.dropbox

import android.content.Context
import ch.derlin.easypass.easypass.App
import ch.derlin.easypass.easypass.data.Accounts
import ch.derlin.easypass.easypass.data.JsonManager
import ch.derlin.easypass.easypass.data.SessionSerialisationType
import ch.derlin.easypass.easypass.dropbox.DbxService.Companion.CACHED_FILE
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.GetMetadataErrorException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.task
import timber.log.Timber
import java.io.FileInputStream

/**
 * Created by Lin on 24.11.17.
 */

object DbxManager {

    var accounts: Accounts? = null
    var metadata: FileMetadata? = null

    val localFileExists: Boolean
        get() = prefs.revision != null

    val client: DbxClientV2 by lazy {
        val config = DbxRequestConfig.newBuilder("Easypass/2.0").build()
        DbxClientV2(config, Preferences(App.appContext).dbxAccessToken)
    }

    val prefs: Preferences by lazy {
        Preferences(App.appContext)
    }

    fun fetchRemoteFileInfo(): Promise<Boolean, Exception> {

        val deferred = deferred<Boolean, Exception>()
        task {
            // TODO
            if (!NetworkStatus.isInternetAvailable(App.appContext)) {
                deferred.reject(Exception("Network not available"))
            } else {
                metadata = null // ensure to clear any past state
                async(CommonPool) {
                    try {
                        metadata = client.files().getMetadata(DbxService.DEFAULT_FILE_PATH) as FileMetadata
                    } catch (e: GetMetadataErrorException) {
                        // session does not exist
                        // TODO
                    } finally {
                        deferred.resolve(metadata != null)
                    }
                }

            }
        }
        return deferred.promise
    }

    fun openSession(password: String): Promise<Boolean, Exception> {

        val deferred = deferred<Boolean, Exception>()
        task {
            val version = prefs.revision

            if (localFileExists) {
                loadCachedFile(password)
                if (metadata != null && metadata!!.rev != version)
                    loadSession(password, deferred)
                else deferred.resolve(true)


            } else {
                if (metadata == null) {
                    // new account
                    accounts = Accounts(password, DbxService.DEFAULT_FILE_PATH)
                    deferred.resolve(true)
                } else {
                    loadSession(password, deferred)
                }
            }
        } fail {
            deferred.reject(it)
        }

        return deferred.promise
    }


    fun saveAccounts(): Promise<Boolean, Exception> {
        assert(this.accounts != null)

        val deferred = deferred<Boolean, Exception>()
        task {
            // serialize accounts to private file
            val fos = App.appContext.openFileOutput(DbxService.CACHED_FILE, Context.MODE_PRIVATE)
            fos.use { out ->
                JsonManager.serialize(accounts!!.data, out, accounts!!.password)
            }

            // upload file to dropbox
            App.appContext.openFileInput(DbxService.CACHED_FILE).use { `in` ->
                this.metadata = client.files()
                        .uploadBuilder(accounts!!.path)
                        .uploadAndFinish(`in`)
            }
            prefs.revision = metadata!!.rev
        } fail {
            val ex = it
            Timber.d(it)
            deferred.reject(ex)
        }
        return deferred.promise
    }

    // ----------------------------

    private fun loadSession(password: String, deferred: nl.komponents.kovenant.Deferred<Boolean, Exception>) {

        async(CommonPool) {
            try {
                metadata = client.files()
                        .download(metadata!!.pathDisplay)
                        .download(App.appContext.openFileOutput(CACHED_FILE, Context.MODE_PRIVATE))

                val isUpdate = accounts != null
                deserialize(App.appContext.openFileInput(CACHED_FILE), metadata?.pathDisplay, password)
                prefs.revision = metadata!!.rev

                if (isUpdate) {
                    //notifyEvent(EVT_SESSION_CHANGED)
                    Timber.d("session changed: %s", metadata!!.rev)
                } else {
                    //notifyEvent(EVT_SESSION_OPENED)
                    Timber.d("remote session loaded: rev: %s", metadata!!.rev)
                }
                deferred.resolve(true)

            } catch (e: Exception) {
                Timber.d(e)
                deferred.reject(e)
            }
        }
    }

    private fun loadCachedFile(password: String) {
        deserialize(App.appContext.openFileInput(DbxService.CACHED_FILE), DbxService.DEFAULT_FILE_PATH, password)
        Timber.d("loaded cached file: rev=%s", prefs.revision)
    }

    private fun deserialize(fin: FileInputStream, pathName: String?, password: String) {
        val accountList = JsonManager.deserialize(fin, password,
                object : TypeToken<SessionSerialisationType>() {
                }.type) as SessionSerialisationType

        accounts = Accounts(password, pathName ?: "??", accountList)
    }
}