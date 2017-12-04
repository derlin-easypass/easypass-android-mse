package ch.derlin.easypass.easypass.data


import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.google.gson.annotations.Expose
import kotlinx.android.parcel.Parcelize
import java.text.SimpleDateFormat
import java.util.*


/**
 * Created by me on 22.10.2017.
 */

typealias SessionSerialisationType = ArrayList<Account>

class Accounts(var password: String, var path: String, data: SessionSerialisationType = arrayListOf())
    : ArrayList<Account>(data) {}


@Parcelize
data class Account(
        @Expose @SerializedName("name") var name: String,
        @Expose @SerializedName("pseudo") var pseudo: String,
        @Expose @SerializedName("email") var email: String,
        @Expose @SerializedName("password") var password: String,
        @Expose @SerializedName("notes") var notes: String = "",
        @Expose @SerializedName("creation date") var creationDate: String = Account.now,
        @Expose @SerializedName("modification date") var modificationDate: String = "",
        @Expose @SerializedName("favorite") var isFavorite: Boolean = false) : Parcelable {

    init {
        // trim
        name = name.trim()
        pseudo = pseudo.trim()
        email = email.trim()
        notes = notes.trim()
    }

    private var _uid = 0L

    val uid: Long
        get() {
            if (_uid == 0L) _uid = name.hashCode().toLong()
            return _uid
        }

    fun isDifferentFrom(acc: Account): Boolean =
            !this.name.equals(acc.name, ignoreCase = true) ||
                    !this.pseudo.equals(acc.pseudo, ignoreCase = true) ||
                    !this.email.equals(acc.email, ignoreCase = true) ||
                    !this.password.equals(acc.password, ignoreCase = true) ||
                    !this.notes.equals(acc.notes, ignoreCase = true)


    companion object {
        val nameComparatorAsc = Comparator<Account> { a1, a2 -> a1.name.compareTo(a2.name, true) }
        val nameComparatorDesc = Comparator<Account> { a1, a2 -> a2.name.compareTo(a1.name, true) }
        val modifiedComparatorAsc = Comparator<Account> { a1, a2 -> a1.modificationDate.compareTo(a2.modificationDate, true) }
        val modifiedComparatorDesc = Comparator<Account> { a1, a2 -> a2.modificationDate.compareTo(a1.modificationDate, true) }
        val now: String
            get() = SimpleDateFormat("yyyy-MM-dd' 'HH:mm", Locale.FRENCH).format(Date())
    }
}

