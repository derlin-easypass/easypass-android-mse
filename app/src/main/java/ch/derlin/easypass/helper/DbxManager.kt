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
import java.io.File
import java.io.FileInputStream


/**
 * This object is responsible for the communication with Dropbox and for the
 * list of accounts.
 *
 * Note: since it acts like a Singleton, so the only way to reset it is to
 * restart the application completely.
 *
 * date 24.11.17
 * @author Lucy Linder
 */

object DbxManager {

    /** Filename used locally as a cache */
    private const val localFileName = "easypass_cached.data_ser"

    /** The list of accounts */
    private var _accounts: Accounts? = null

    val accounts: Accounts
        get() = requireNotNull(_accounts) { "Dropbox accounts is null !!" }

    /** Whether the accounts are loaded */
    val isInitialized: Boolean
        get() = _accounts != null

    /** Are metadata about the current session from Dropbox fetched ?  */
    private var metaFetched = false

    /** The metadata concerning the current session file fetched from Dropbox */
    private var metadata: FileMetadata? = null

    /**
     * Is the current session a new one ? i.e. Does the file already exist on Dropbox ?
     * Note: this flag is always file when working offline (no metadata fetched)
     */
    val isNewSession: Boolean
        get() = metaFetched && metadata == null

    /** Do we have a cached file locally ? */
    val localFileExists: Boolean
        get() = Preferences.revision != null

    /** The Dropbox client */
    val client: DbxClientV2 by lazy {
        val token = Preferences.dbxAccessToken
        Timber.d("Dropbox token ?? client created")
        val config = DbxRequestConfig.newBuilder("Easypass/2.0").build()
        DbxClientV2(config, token)
    }

    /** Is the local session in sync with Dropbox ? */
    var isInSync = false
        private set

    /**
     * Fetch the Dropbox metadata about the current session.
     * @return A promise resolved with [isInSync] and rejected with an [Exception]
     * in case we are offline
     */
    fun fetchRemoteFileInfo(): Promise<Boolean, Exception> {

        val deferred = deferred<Boolean, Exception>()
        task {
            // TODO
            if (!NetworkStatus.isInternetAvailable(App.appContext)) {
                deferred.reject(Exception("Network not available"))
            } else {
                metadata = null // ensure to clear any past state

                try {
                    metadata = client.files().getMetadata(Preferences.remoteFilePath) as FileMetadata
                    metaFetched = true
                    isInSync = metadata?.rev.equals(Preferences.revision)
                    deferred.resolve(isInSync)
                } catch (e: GetMetadataErrorException) {
                    // session does not exist
                    with(Preferences) {
                        cachedPassword = null  // ensure it is clean
                        revision = null
                    }
                    isInSync = true
                    metaFetched = true // flag for isNewSession
                    deferred.resolve(isInSync)
                }
            }
        } fail {
            val ex = it
            if (ex is InvalidAccessTokenException) Preferences.dbxAccessToken = null
            deferred.reject(ex)
        }

        return deferred.promise
    }

    /**
     * Delete the local cache file.
     */
    fun removeLocalFile(): Boolean {
        val localFile = File(App.appContext.filesDir.absolutePath, localFileName)
        val ok = localFile.delete()
        Timber.d("""removed local file ? $ok""")
        Preferences.revision = null
        return ok
    }

    /**
     * Load the session into [accounts].
     * If a local file exists, it will download the file from Dropbox only if [isInSync] is false.
     *
     * @param password the password
     * @return a promise resolved with true or rejected with an exception in case the
     * session could not be loaded (no network and no cached file, network but no
     * metadata fetched)
     */
    fun openSession(password: String): Promise<Boolean, Exception> {
        val deferred = deferred<Boolean, Exception>()
        task {

            if (isNewSession) {
                // new account
                _accounts = Accounts(password, Preferences.remoteFilePath)
                deferred.resolve(true)

            } else if (!NetworkStatus.isInternetAvailable()) {
                if (localFileExists) {
                    loadCachedFile(password)
                    deferred.resolve(true)
                } else {
                    deferred.reject(Exception("No network connection (and no cached session)"))
                }
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

    /**
     * Encrypt and save the [accounts] to dropbox.
     */
    fun saveAccounts(): Promise<Boolean, Exception> {
        requireNotNull(_accounts)

        val deferred = deferred<Boolean, Exception>()
        task {
            Timber.d("begin save accounts %s", Thread.currentThread())
            val tempFile = "lala"
            // serialize accounts to private file
            App.appContext.openFileOutput(tempFile, Context.MODE_PRIVATE).use { out ->
                JsonManager.serialize(accounts, out, accounts.password)
            }

            // upload file to dropbox
            App.appContext.openFileInput(tempFile).use { `in` ->
                metadata = client.files()
                        .uploadBuilder(accounts.path)
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(`in`)
            }

            // make changes locally permanent
            // TODO
            IOUtil.copyStreamToStream(App.appContext.openFileInput(tempFile),
                    App.appContext.openFileOutput(localFileName, Context.MODE_PRIVATE))

            Preferences.revision = metadata!!.rev
            deferred.resolve(true)
            Timber.d("end save accounts %s", Thread.currentThread())
        } fail {
            val ex = it
            Timber.d(it)
            deferred.reject(ex)
        }
        return deferred.promise
    }

    /**
     * Get all the filenames in the Dropbox application directory.
     * Note that the starting slash is removed from all filenames.
     *
     * @return a promise resolved with the list of filenames and rejeted with an
     * exception in case the fetching failed.
     */
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

    // ----------------------------

    // fetch the accounts from Dropbox
    private fun loadSession(password: String, deferred: nl.komponents.kovenant.Deferred<Boolean, Exception>) {

        try {
            metadata = client.files()
                    .download(metadata!!.pathDisplay)
                    .download(App.appContext.openFileOutput(localFileName, Context.MODE_PRIVATE))

            val isUpdate = _accounts != null
            deserialize(App.appContext.openFileInput(localFileName), metadata?.pathDisplay, password)
            Preferences.revision = metadata!!.rev

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

    // read the accounts from the cached file
    private fun loadCachedFile(password: String) {
        deserialize(App.appContext.openFileInput(localFileName), Preferences.remoteFilePath, password)
        Timber.d("loaded cached file: rev=%s", Preferences.revision)
    }

    // deserialize and decrypt a file. This will update the accounts variable
    private fun deserialize(fin: FileInputStream, pathName: String?, password: String) {
        val accountList = JsonManager.deserialize(fin, password,
                object : TypeToken<SessionSerialisationType>() {
                }.type) as SessionSerialisationType

        _accounts = Accounts(password, pathName ?: "??", accountList)
    }
}