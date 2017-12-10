package ch.derlin.easypass.easypass

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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import ch.derlin.easypass.easypass.helper.CachedCredentials
import ch.derlin.easypass.easypass.helper.DbxManager
import ch.derlin.easypass.easypass.helper.MiscUtils.restartApp
import ch.derlin.easypass.easypass.helper.MiscUtils.rootView
import ch.derlin.easypass.easypass.helper.Preferences
import kotlinx.android.synthetic.main.activity_settings.*
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi

class SettingsActivity : AppCompatActivity() {

    class Setting(val title: String,
                  val subtitle: String = "",
                  val onClick: (() -> Unit)? = null,
                  val icon: Int = R.drawable.ic_settings,
                  val isHeader: Boolean = false)


    val settings = listOf<Setting>(
            Setting("Passwords", isHeader = true),
            Setting("Change", "Change your master password.", this@SettingsActivity::changePasswordDialog, R.drawable.ic_mode_edit),
            Setting("Clear", "Forget all cached passwords.", this@SettingsActivity::clearPassword, R.drawable.ic_fingerprint),
            Setting("Dropbox", isHeader = true),
            Setting("Unlink", "Unlink EasyPass from your dropbox.", this@SettingsActivity::unbindDropbox, R.drawable.ic_dropbox),
            Setting("Local file", isHeader = true),
            Setting("Clear cache", "Clear the local cache by removing the file on the device.", this@SettingsActivity::clearCache, R.drawable.ic_broom)
    )

    var working: Boolean
        get() = progressBar.visibility == View.VISIBLE
        set(value) = progressBar.setVisibility(if (value) View.VISIBLE else View.INVISIBLE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        recyclerView.adapter = SettingsAdapter(settings)
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
            DbxManager.client.auth().tokenRevoke()
            Preferences().dbxAccessToken = null
        } successUi {
            restartApp()
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

    fun changePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.alert_change_password, null)
        AlertDialog.Builder(this)
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

    // ----------------------------------------- inner class

    class SettingsAdapter(val settings: List<Setting>) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

        override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
            val item: Setting = settings[position]
            holder!!.titleView.text = item.title

            if (holder is ItemViewHolder) {
                holder.subtitleView.text = item.subtitle
                holder.iconView.setBackgroundResource(item.icon)
                holder.view.setOnClickListener { _ -> item.onClick?.invoke() }
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


        open class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val titleView: TextView = view.findViewById(R.id.title)
        }

        class ItemViewHolder(view: View) : ViewHolder(view) {
            val subtitleView: TextView = view.findViewById(R.id.subtitle)
            val iconView: ImageView = view.findViewById(R.id.icon)
        }
    }

}