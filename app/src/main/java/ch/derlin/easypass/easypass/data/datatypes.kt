package ch.derlin.easypass.easypass.data


import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize
import java.util.ArrayList


/**
 * Created by me on 22.10.2017.
 */

typealias SessionSerialisationType = ArrayList<Account>

class Accounts(var password: String, var path: String, data: SessionSerialisationType = arrayListOf())
    : ArrayList<Account>(data){}


@Parcelize
data class Account(
        @SerializedName("name") var name: String,
        @SerializedName("pseudo") var pseudo: String,
        @SerializedName("email") var email: String,
        @SerializedName("password") var password: String,
        @SerializedName("notes") var notes: String,
        @SerializedName("creation date") var creationDate: String,
        @SerializedName("modification date") var modificationDate: String): Parcelable

