package ch.derlin.easypass

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import ch.derlin.changelog.Changelog
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.helper.*
import ch.derlin.easypass.helper.MiscUtils.rootView
import ch.derlin.easypass.helper.MiscUtils.showIntro
import ch.derlin.easypass.helper.SelectFileDialog.createSelectFileDialog
import kotlinx.android.synthetic.main.activity_settings.*
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import timber.log.Timber

// TODO: check connectivity to avoid errors (changing mdp for example...)
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
                    "Set the characters used in passwords.",
                    this@SettingsActivity::setSpecialChars, R.drawable.ic_mode_edit),
            Setting("Passwords", isHeader = true),
            Setting("Change",
                    "Change your master password.",
                    this@SettingsActivity::changePasswordDialog, R.drawable.ic_mode_edit),
            Setting("Clear",
                    "Forget all cached passwords.",
                    this@SettingsActivity::clearPassword, R.drawable.ic_fingerprint),
            Setting("Data", isHeader = true),
            Setting("File",
                    "Change the session to use.",
                    this@SettingsActivity::changeSessionFileDialog, R.drawable.ic_cloud_download),
            Setting("Unlink",
                    "Unlink EasyPass from your dropbox.",
                    this@SettingsActivity::unbindDropbox, R.drawable.ic_dropbox,
                    confirm = "Are you sure you want to unbind from Dropbox ?"),
            Setting("Clear cache",
                    "Clear the local cache by removing the file on the device.",
                    this@SettingsActivity::clearCache, R.drawable.ic_broom,
                    confirm = "Really clear all cached data ?"),
            Setting("Other", isHeader = true),
            Setting("Intro",
                    "Show the introductory slides.",
                    { -> showIntro() }, R.drawable.ic_info_outline),
            Setting("Changelog",
                    "Show the complete changelog.",
                    { -> showChangelog() }, R.drawable.ic_info_outline)
    )

    var working: Boolean
        get() = progressBar.visibility == View.VISIBLE
        set(value) = progressBar.setVisibility(if (value) View.VISIBLE else View.INVISIBLE)


    // ----------------------------------------- activity stuff

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

    fun showChangelog(){
        Changelog.createDialog(this).show()
    }

    private fun changeSessionFileDialog() {
        createSelectFileDialog({
            val intent = Intent(this, LoadSessionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }).show()
    }

    private fun exitApp() {
        val intent = Intent()
        intent.putExtra(BUNDLE_RESTART_KEY, true)
        setResult(Activity.RESULT_OK, intent)
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
                return ViewHolder(v)

            } else {
                val v: View = LayoutInflater.from(parent!!.context).inflate(
                        R.layout.settings_list_content, parent, false)

                return ItemViewHolder(v)
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

// -----------------------------------------

    companion object {
        val BUNDLE_RESTART_KEY = "restart"
    }

}
