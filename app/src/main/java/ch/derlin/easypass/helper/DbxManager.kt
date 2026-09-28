package ch.derlin.easypass.helper

import android.content.Context
import ch.derlin.easypass.App
import ch.derlin.easypass.data.Accounts
import ch.derlin.easypass.data.JsonManager
import ch.derlin.easypass.data.SessionSerialisationType
import ch.derlin.easypass.easypass.R
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.util.IOUtil
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.GetMetadataErrorException
import com.dropbox.core.v2.files.WriteMode
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    private const val LOCAL_FILE_NAME = "easypass_cached.enc"

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

    /** get the requestConfig necessary to start oauth authentication using 2PKCE */
    fun requestConfig(): DbxRequestConfig = DbxRequestConfig
        .newBuilder(App.appContext.getString(R.string.dbx_request_config_name)).build()

    /** The Dropbox client */
    private val client: DbxClientV2 by lazy {
        val token = Preferences.dbxAccessToken
        Timber.d("Dropbox token ?? client created")
        DbxClientV2(requestConfig(), DbxCredential.Reader.readFully(token))
    }

    /** Is the local session in sync with Dropbox ? */
    var isInSync = false
        private set

    // One worker thread avoids concurrent account updates. NonCancellable lets a started
    // save finish even if the caller's screen is closed.
    private val worker = Dispatchers.IO.limitedParallelism(1)

    private suspend fun <T> onWorker(block: () -> T): T =
        withContext(NonCancellable + worker) { block() }

    /** Unbind from DropBox. */
    suspend fun unbind() = onWorker {
        Timber.d("revoking Dropbox token")
        Preferences.dbxAccessToken = null
        Preferences.revision = null
        client.auth().tokenRevoke()
    }

    /**
     * Fetch the Dropbox metadata about the current session.
     * @return [isInSync]
     * @throws Exception in case we are offline
     */
    suspend fun fetchRemoteFileInfo(): Boolean = onWorker {
        // TODO
        if (!NetworkStatus.isInternetAvailable(App.appContext)) {
            throw Exception("Network not available")
        }
        metadata = null // ensure to clear any past state

        try {
            metadata =
                client.files().getMetadata(Preferences.remoteFilePath) as FileMetadata
            metaFetched = true
            isInSync = metadata?.rev.equals(Preferences.revision)
        } catch (e: GetMetadataErrorException) {
            // session does not exist
            with(Preferences) {
                cachedPassword = null  // ensure it is clean
                revision = null
            }
            isInSync = true
            metaFetched = true // flag for isNewSession
        } catch (e: InvalidAccessTokenException) {
            Preferences.dbxAccessToken = null
            throw e
        }
        isInSync
    }

    /**
     * Delete the local cache file.
     */
    fun removeLocalFile(): Boolean {
        val localFile = File(App.appContext.filesDir.absolutePath, LOCAL_FILE_NAME)
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
     * @throws Exception in case the session could not be loaded (no network and no
     * cached file, network but no metadata fetched)
     */
    suspend fun openSession(password: String) = onWorker {
        if (isNewSession) {
            // new account
            _accounts = Accounts(password, Preferences.remoteFilePath)

        } else if (!NetworkStatus.isInternetAvailable()) {
            if (localFileExists) {
                loadCachedFile(password)
            } else {
                throw Exception("No network connection (and no cached session)")
            }
        } else if (isInSync && localFileExists) {
            loadCachedFile(password)

        } else {
            if (metaFetched) {
                // no cached file, but ok, we have the connection
                // (at least we should since we have fetched the metadata)
                loadSession(password)
            } else {
                throw Exception("Missing metadata (no network?) and offline mode not available (no cached file)")
            }
        }
    }

    /**
     * Encrypt and save the [accounts] to dropbox.
     */
    suspend fun saveAccounts() {
        requireNotNull(_accounts)

        onWorker {
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
            IOUtil.copyStreamToStream(
                App.appContext.openFileInput(tempFile),
                App.appContext.openFileOutput(LOCAL_FILE_NAME, Context.MODE_PRIVATE)
            )

            Preferences.revision = metadata!!.rev
            Timber.d("end save accounts %s", Thread.currentThread())
        }
    }

    /**
     * Get the session filenames (ending with [Preferences.SESSION_FILE_EXTENSION]) in the
     * Dropbox application directory.
     * Note that the starting slash is removed from all filenames.
     *
     * @return the list of filenames
     * @throws Exception in case the fetching failed.
     */
    suspend fun listSessionFiles(): Array<String> = onWorker {
        val files = client.files().listFolder("").entries.map { f -> f.name }
            .filter { it.endsWith(Preferences.SESSION_FILE_EXTENSION) }.toTypedArray()
        files.sort()
        files
    }

    // ----------------------------

    // fetch the accounts from Dropbox
    private fun loadSession(password: String) {
        try {
            metadata = client.files()
                .download(metadata!!.pathDisplay)
                .download(App.appContext.openFileOutput(LOCAL_FILE_NAME, Context.MODE_PRIVATE))

            val isUpdate = _accounts != null
            deserialize(
                App.appContext.openFileInput(LOCAL_FILE_NAME),
                metadata?.pathDisplay,
                password
            )
            Preferences.revision = metadata!!.rev

            if (isUpdate) {
                //notifyEvent(EVT_SESSION_CHANGED)
                Timber.d("session changed: %s", metadata!!.rev)
            } else {
                //notifyEvent(EVT_SESSION_OPENED)
                Timber.d("remote session loaded: rev: %s", metadata!!.rev)
            }

            isInSync = true

        } catch (e: Exception) {
            // TODO: undo local change
            Timber.d(e)
            throw e
        }
    }

    // read the accounts from the cached file
    private fun loadCachedFile(password: String) {
        deserialize(
            App.appContext.openFileInput(LOCAL_FILE_NAME),
            Preferences.remoteFilePath,
            password
        )
        Timber.d("loaded cached file: rev=%s", Preferences.revision)
    }

    // deserialize and decrypt a file. This will update the accounts variable
    private fun deserialize(fin: FileInputStream, pathName: String?, password: String) {
        @Suppress("UNCHECKED_CAST") // Gson returns Any, typed by the TypeToken
        val accountList = JsonManager.deserialize(
            fin, password,
            object : TypeToken<SessionSerialisationType>() {
            }.type
        ) as SessionSerialisationType

        _accounts = Accounts(password, pathName ?: "??", accountList)
    }
}