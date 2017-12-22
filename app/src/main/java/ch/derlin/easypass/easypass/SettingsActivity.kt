package ch.derlin.easypass.easypass

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.TextInputEditText
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import ch.derlin.easypass.easypass.helper.CachedCredentials
import ch.derlin.easypass.easypass.helper.DbxManager
import ch.derlin.easypass.easypass.helper.MiscUtils.rootView
import ch.derlin.easypass.easypass.helper.PasswordGenerator
import ch.derlin.easypass.easypass.helper.Preferences
import kotlinx.android.synthetic.main.activity_settings.*
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import timber.log.Timber

class SettingsActivity : AppCompatActivity() {

    class Setting(val title: String,
                  val subtitle: String = "",
                  val onClick: (() -> Unit)? = null,
                  val icon: Int = R.drawable.ic_settings,
                  val confirm: String? = null,
                  val isHeader: Boolean = false)


    val settings = listOf<Setting>(
            Setting("Generator", isHeader = true),
            Setting("Special chars",
                    "Change the special characters used while generating passwords.",
                    this@SettingsActivity::setSpecialChars, R.drawable.ic_mode_edit),
            Setting("Passwords", isHeader = true),
            Setting("Change",
                    "Change your master password.",
                    this@SettingsActivity::changePasswordDialog, R.drawable.ic_mode_edit),
            Setting("Clear",
                    "Forget all cached passwords.",
                    this@SettingsActivity::clearPassword, R.drawable.ic_fingerprint),
            Setting("Dropbox", isHeader = true),
            Setting("Unlink",
                    "Unlink EasyPass from your dropbox.",
                    this@SettingsActivity::unbindDropbox, R.drawable.ic_dropbox,
                    confirm = "Are you sure you want to unbind from Dropbox ?"),
            Setting("Local file", isHeader = true),
            Setting("Clear cache",
                    "Clear the local cache by removing the file on the device.",
                    this@SettingsActivity::clearCache, R.drawable.ic_broom,
                    confirm = "Really clear all cached data ?")
    )

    var working: Boolean
        get() = progressBar.visibility == View.VISIBLE
        set(value) = progressBar.setVisibility(if (value) View.VISIBLE else View.INVISIBLE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        recyclerView.adapter = SettingsAdapter(this, settings)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home -> {
                onBackPressed(); return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    // ----------------------------------------- implement actions

    fun clearPassword() {
        CachedCredentials.clearPassword()
        Snackbar.make(rootView(), "password cleared.", Snackbar.LENGTH_SHORT).show()
    }


    fun unbindDropbox() {
        working = true
        task {
            Timber.d("revoking Dropbox token ${Preferences().dbxAccessToken}")
            Preferences().dbxAccessToken = null
            DbxManager.client.auth().tokenRevoke()
        } successUi {
            exitApp()
        } failUi {
            working = false
            Snackbar.make(rootView(), "Error: " + it, Snackbar.LENGTH_LONG).show()
        }
    }

    fun clearCache() {
        if (DbxManager.removeLocalFile()) {
            Snackbar.make(rootView(), "Local file removed.", Snackbar.LENGTH_SHORT).show()
        }
    }

    fun setSpecialChars() {
        val prefs = Preferences(this)
        val view = layoutInflater.inflate(R.layout.dialog_settings_special_chars, null)
        val editText = view.findViewById<EditText>(R.id.specialChars)
        view.findViewById<Button>(R.id.defaultButton).setOnClickListener { _ ->
            editText.setText(PasswordGenerator.allSpecialChars)
        }

        editText.setText(prefs.specialChars)

        AlertDialog.Builder(this, R.style.AppTheme_AlertDialog)
                .setView(view)
                .setTitle("Generator special chars")
                .setNegativeButton("cancel", { dialog, _ -> dialog.dismiss() })
                .setPositiveButton("use", { _, _ ->
                    if (editText.text.isNotBlank()) {
                        prefs.specialChars = editText.text.toString()
                        Toast.makeText(this, "preference updated.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "invalid empty sequence", Toast.LENGTH_SHORT).show()
                    }
                })
                .show()
    }

    fun changePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        AlertDialog.Builder(this, R.style.AppTheme_AlertDialog)
                .setView(view)
                .setTitle("New password")
                .setNegativeButton("cancel", { dialog, _ -> dialog.dismiss() })
                .setPositiveButton("change", { dialog, _ ->
                    val pass = view.findViewById<TextInputEditText>(R.id.passwordField).text
                    if (pass.isBlank() || pass.length < LoadSessionActivity.PasswordFragment.MIN_PASSWORD_LENGTH) {
                        Toast.makeText(this@SettingsActivity,
                                "Password too short", Toast.LENGTH_SHORT).show()
                    } else if (pass.equals(DbxManager.accounts!!.password)) {
                        Toast.makeText(this@SettingsActivity,
                                "Password did not change.", Toast.LENGTH_SHORT).show()
                    } else {
                        changePassword(pass.toString())
                    }
                })
                .show()
    }

    fun changePassword(newPassword: String, firstTime: Boolean = true) {
        working = true
        val oldPassword = DbxManager.accounts!!.password
        DbxManager.accounts!!.password = newPassword
        DbxManager.saveAccounts().alwaysUi {
            working = false
        } successUi {
            CachedCredentials.clearPassword()
            val snack = Snackbar.make(rootView(), "Password changed.", Snackbar.LENGTH_LONG)
            if (firstTime)
                snack.setAction("undo", { _ -> changePassword(oldPassword, false) })
            snack.show()
        } failUi {
            DbxManager.accounts!!.password = oldPassword
            Snackbar.make(rootView(), "Error: " + it, Snackbar.LENGTH_LONG)
                    .show()
        }
    }

    private fun exitApp() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    // ----------------------------------------- inner class

    class SettingsAdapter(val context: Context, val settings: List<Setting>) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

        override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
            val item: Setting = settings[position]
            holder!!.titleView.text = item.title

            if (holder is ItemViewHolder) {
                holder.subtitleView.text = item.subtitle
                holder.iconView.setBackgroundResource(item.icon)
                holder.view.setOnClickListener { _ -> onItemClick(item) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
            // create a new view
            if (viewType == 0) {
                val v = LayoutInflater.from(parent!!.context).inflate(
                        R.layout.settings_list_header, parent, false)
                return SettingsAdapter.ViewHolder(v)

            } else {
                val v: View = LayoutInflater.from(parent!!.context).inflate(
                        R.layout.settings_list_content, parent, false)

                return SettingsAdapter.ItemViewHolder(v)
            }
        }

        override fun getItemViewType(position: Int): Int = if (settings[position].isHeader) 0 else 1

        override fun getItemCount(): Int = settings.size

        fun onItemClick(s: Setting) {
            if (s.confirm == null) {
                s.onClick?.invoke()
            } else {
                // confirmation dialog
                AlertDialog.Builder(context)
                        .setMessage(s.confirm)
                        .setPositiveButton("OK", { _, _ ->
                            s.onClick?.invoke()
                        })
                        .setNegativeButton("Cancel", { _, _ -> })
                        .show()
            }
        }

        open class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val titleView: TextView = view.findViewById(R.id.title)
        }

        class ItemViewHolder(view: View) : ViewHolder(view) {
            val subtitleView: TextView = view.findViewById(R.id.subtitle)
            val iconView: ImageView = view.findViewById(R.id.icon)
        }
    }

}
