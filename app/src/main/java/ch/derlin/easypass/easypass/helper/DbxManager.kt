package ch.derlin.easypass.easypass.helper

import android.content.Context
import ch.derlin.easypass.easypass.App
import ch.derlin.easypass.easypass.data.Accounts
import ch.derlin.easypass.easypass.data.JsonManager
import ch.derlin.easypass.easypass.data.SessionSerialisationType
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.util.IOUtil
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.GetMetadataErrorException
import com.dropbox.core.v2.files.WriteMode
import com.google.gson.reflect.TypeToken
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.task
import timber.log.Timber
import java.io.FileInputStream

/**
 * Created by Lin on 24.11.17.
 */

object DbxManager {

    const val remoteFilePath = "/easypass.data_ser"
    const val localFileName = "easypass_cached.data_ser"


    var accounts: Accounts? = null

    var metaFetched = false
        private set

    var metadata: FileMetadata? = null

    val isNewSession: Boolean
        get() = metaFetched && metadata == null

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

                try {
                    metadata = client.files().getMetadata(remoteFilePath) as FileMetadata
                    metaFetched = true
                } catch (e: GetMetadataErrorException) {
                    // session does not exist
                    // TODO ?
                } finally {
                    deferred.resolve(metadata != null)
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
                    accounts = Accounts(password, remoteFilePath)
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
            val tempFile = "lala"
            // serialize accounts to private file
            App.appContext.openFileOutput(tempFile, Context.MODE_PRIVATE).use { out ->
                JsonManager.serialize(accounts!!, out, accounts!!.password)
            }

            // upload file to dropbox
            App.appContext.openFileInput(tempFile).use { `in` ->
                this.metadata = client.files()
                        .uploadBuilder(accounts!!.path)
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(`in`)
            }

            // make changes locally permanent
            // TODO
            IOUtil.copyStreamToStream(App.appContext.openFileInput(tempFile),
                    App.appContext.openFileOutput(localFileName, Context.MODE_PRIVATE))

            prefs.revision = metadata!!.rev
            deferred.resolve(true)
        } fail {
            val ex = it
            Timber.d(it)
            deferred.reject(ex)
        }
        return deferred.promise
    }

    // ----------------------------

    private fun loadSession(password: String, deferred: nl.komponents.kovenant.Deferred<Boolean, Exception>) {

        try {

            metadata = client.files()
                    .download(metadata!!.pathDisplay)
                    .download(App.appContext.openFileOutput(localFileName, Context.MODE_PRIVATE))

            val isUpdate = accounts != null
            deserialize(App.appContext.openFileInput(localFileName), metadata?.pathDisplay, password)
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
            // TODO: undo local change
            Timber.d(e)
            deferred.reject(e)
        }
    }

    private fun loadCachedFile(password: String) {
        deserialize(App.appContext.openFileInput(localFileName), remoteFilePath, password)
        Timber.d("loaded cached file: rev=%s", prefs.revision)
    }

    private fun deserialize(fin: FileInputStream, pathName: String?, password: String) {
        val accountList = JsonManager.deserialize(fin, password,
                object : TypeToken<SessionSerialisationType>() {
                }.type) as SessionSerialisationType

        accounts = Accounts(password, pathName ?: "??", accountList)
    }
}