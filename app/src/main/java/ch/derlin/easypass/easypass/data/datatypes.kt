package ch.derlin.easypass.easypass.data


import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.google.gson.annotations.Expose
import kotlinx.android.parcel.Parcelize
import java.util.ArrayList


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
        @Expose @SerializedName("creation date") var creationDate: String = "",
        @Expose @SerializedName("modification date") var modificationDate: String = "",
        @Expose @SerializedName("favorite") var isFavorite: Boolean = false) : Parcelable {

    private var _uid = 0L

    val uid: Long
        get() {
            if (_uid == 0L) _uid = name.hashCode().toLong()
            return _uid
        }


    companion object {
        val nameComparator = Comparator<Account> { a1, a2 -> a1.name.compareTo(a2.name, true) }
    }
}

