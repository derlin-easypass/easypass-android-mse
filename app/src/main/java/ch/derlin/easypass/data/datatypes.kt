package ch.derlin.easypass.data


import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.google.gson.annotations.Expose
import kotlinx.android.parcel.Parcelize
import java.text.SimpleDateFormat
import java.util.*

/**
 * This file defines the main data types.
 *
 * date: 22.10.2017
 * @author Lucy Linder
 */

/** How the accounts are serialized in the JSON file before encryption */
typealias SessionSerialisationType = ArrayList<Account>

/** The accounts container */
class Accounts(
        /** The password (in memory only) */
        var password: String,
        /** The remote path to the file in Dropbox */
        var path: String, data:
        /** The actual list of accounts */
        SessionSerialisationType = arrayListOf())
    : ArrayList<Account>(data)


/**
 * An account. The serialisation names are used by GSON for json serialization
 * to the compatibility with older versions of EasyPass.
 */
@Parcelize
data class Account(
        /** The display name for the account. Should be unique. */
        @Expose @SerializedName("name") var name: String = "",
        /** An optional username */
        @Expose @SerializedName("pseudo") var pseudo: String = "",
        /** An optional email address */
        @Expose @SerializedName("email") var email: String = "",
        /** The password */
        @Expose @SerializedName("password") var password: String = "",
        /** Optional notes */
        @Expose @SerializedName("notes") var notes: String = "",
        /** The creation date in the format: YYYY-MM-DD HH:mm */
        @Expose @SerializedName("creation date") var creationDate: String = now,
        /** The last modification date in the format: YYYY-MM-DD HH:mm */
        @Expose @SerializedName("modification date") var modificationDate: String = "",
        /** Whether it is pinned as favorite */
        @Expose @SerializedName("favorite") var isFavorite: Boolean = false) : Parcelable {

    init {
        // trim all the fields on load (was not always done by previous versions)
        name = name.trim()
        pseudo = pseudo.trim()
        email = email.trim()
        notes = notes.trim()
    }

    // compute the uid only once, so that it does not change if the name is edited
    private var _uid = 0L

    /**
     * A unique UID that can be used by the [android.support.v7.widget.RecyclerView]
     * for nice animations
     */
    val uid: Long
        get() {
            if (_uid == 0L) _uid = name.hashCode().toLong()
            return _uid
        }

    /** Search for a substring in all fields except the password. Case insensitive. */
    fun contains(search: String): Boolean =
            name.contains(search, ignoreCase = true) ||
                    pseudo.contains(search, ignoreCase = true) ||
                    email.contains(search, ignoreCase = true) ||
                    notes.contains(search, ignoreCase = true)


    /** Test if two accounts are different. Case-sensitive. */
    fun isDifferentFrom(acc: Account): Boolean =
            !this.name.equals(acc.name, ignoreCase = false) ||
                    !this.pseudo.equals(acc.pseudo, ignoreCase = false) ||
                    !this.email.equals(acc.email, ignoreCase = false) ||
                    !this.password.equals(acc.password, ignoreCase = false) ||
                    !this.notes.equals(acc.notes, ignoreCase = false)

    /** Test if the account is valid. Currently, only the name is mandatory. */
    val isValid: Boolean
        get() = name.isNotBlank()

    /** Toggle the favorite flag. */
    fun toggleFavorite() {
        this.isFavorite = !this.isFavorite
    }


    companion object {
        /** Compare accounts based on names (ascending). The favorite flag is ignored. */
        val nameComparatorAsc = Comparator<Account> { a1, a2 -> a1.name.compareTo(a2.name, true) }
        /** Compare accounts based on names (descending). The favorite flag is ignored. */
        val nameComparatorDesc = Comparator<Account> { a1, a2 -> a2.name.compareTo(a1.name, true) }
        /** Compare accounts based on modified, then names (ascending). The favorite flag is ignored. */
        val modifiedComparatorAsc = Comparator<Account> { a1, a2 -> a1.modificationDate.compareTo(a2.modificationDate, true) }
        /** Compare accounts based on modified, then names (descending). The favorite flag is ignored. */
        val modifiedComparatorDesc = Comparator<Account> { a1, a2 -> a2.modificationDate.compareTo(a1.modificationDate, true) }
        /** Returns the current datetime for use in [Account.creationDate] and [Account.modificationDate] */
        val now: String
            get() = SimpleDateFormat("yyyy-MM-dd' 'HH:mm", Locale.FRENCH).format(Date())
    }
}

