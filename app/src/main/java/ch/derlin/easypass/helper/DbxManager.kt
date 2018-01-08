package ch.derlin.easypass.helper

import android.content.Context
import ch.derlin.easypass.App
import ch.derlin.easypass.data.Accounts
import ch.derlin.easypass.data.JsonManager
import ch.derlin.easypass.data.SessionSerialisationType
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.InvalidAccessTokenException
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
import java.io.File


/**
 * Created by Lin on 24.11.17.
 */

object DbxManager {

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
        val token = Preferences(App.appContext).dbxAccessToken
        Timber.d("Dropbox token ?? client created")
        val config = DbxRequestConfig.newBuilder("Easypass/2.0").build()
        DbxClientV2(config, token)
    }

    val prefs: Preferences by lazy {
        Preferences(App.appContext)
    }

    var isInSync = false
        private set

    fun fetchRemoteFileInfo(): Promise<Boolean, Exception> {

        val deferred = deferred<Boolean, Exception>()
        task {
            // TODO
            if (!NetworkStatus.isInternetAvailable(App.appContext)) {
                deferred.reject(Exception("Network not available"))
            } else {
                metadata = null // ensure to clear any past state

                try {
                    metadata = client.files().getMetadata(prefs.remoteFilePath) as FileMetadata
                    metaFetched = true
                    isInSync = metadata?.rev.equals(prefs.revision)
                    deferred.resolve(isInSync)
                } catch (e: GetMetadataErrorException) {
                    // session does not exist
                    prefs.cachedPassword = null // ensure it is clean
                    isInSync = prefs.revision == null
                    prefs.revision = null
                    metaFetched = true // flag for isNewSession
                    deferred.resolve(isInSync)
                }
            }
        } fail {
            val ex = it
            if (ex is InvalidAccessTokenException) prefs.dbxAccessToken = null
            deferred.reject(ex)
        }

        return deferred.promise
    }

    fun removeLocalFile(): Boolean {
        val localFile = File(App.appContext.filesDir.getAbsolutePath(), localFileName)
        val ok = localFile.delete()
        Timber.d("""removed local file ? $ok""")
        prefs.revision = null
        return ok
    }


    fun openSession(password: String): Promise<Boolean, Exception> {

        val deferred = deferred<Boolean, Exception>()
        task {

            if (isNewSession) {
                // new account
                accounts = Accounts(password, prefs.remoteFilePath)
                deferred.resolve(true)

            } else if (isInSync && localFileExists) {
                loadCachedFile(password)
                deferred.resolve(true)

            } else {
                if (metaFetched) {
                    // no cached file, but ok, we have the connection
                    // (at least we should since we have fetched the metadata)
                    loadSession(password, deferred)
                } else {
                    deferred.reject(Exception("Missing metadata (no network?) and offline mode not available (no cached file)"))
                }

            }
        } fail {
            deferred.reject(it)
        }

        return deferred.promise
    }

    fun saveAccounts(): Promise<Boolean, Exception> {
        assert(accounts != null)

        val deferred = deferred<Boolean, Exception>()
        task {
            Timber.d("begin save accounts %s", Thread.currentThread())
            val tempFile = "lala"
            // serialize accounts to private file
            App.appContext.openFileOutput(tempFile, Context.MODE_PRIVATE).use { out ->
                JsonManager.serialize(accounts!!, out, accounts!!.password)
            }

            // upload file to dropbox
            App.appContext.openFileInput(tempFile).use { `in` ->
                metadata = client.files()
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
            Timber.d("end save accounts %s", Thread.currentThread())
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

            isInSync = true
            deferred.resolve(true)

        } catch (e: Exception) {
            // TODO: undo local change
            Timber.d(e)
            deferred.reject(e)
        }
    }

    fun listSessionFiles(): Promise<Array<String>, Exception> {
        val deferred = deferred<Array<String>, Exception>()
        task {
            val files = client.files().listFolder("").entries.map { f -> f.name }.toTypedArray()
            files.sort()
            deferred.resolve(files)
        } fail {
            deferred.reject(it)
        }
        return deferred.promise
    }

    private fun loadCachedFile(password: String) {
        deserialize(App.appContext.openFileInput(localFileName), prefs.remoteFilePath, password)
        Timber.d("loaded cached file: rev=%s", prefs.revision)
    }

    private fun deserialize(fin: FileInputStream, pathName: String?, password: String) {
        val accountList = JsonManager.deserialize(fin, password,
                object : TypeToken<SessionSerialisationType>() {
                }.type) as SessionSerialisationType

        accounts = Accounts(password, pathName ?: "??", accountList)
    }
}