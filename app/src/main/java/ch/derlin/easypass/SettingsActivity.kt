package ch.derlin.easypass

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import ch.derlin.changelog.Changelog
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.easypass.databinding.ActivitySettingsBinding
import ch.derlin.easypass.helper.CachedCredentials
import ch.derlin.easypass.helper.DbxManager
import ch.derlin.easypass.helper.MiscUtils.rootView
import ch.derlin.easypass.helper.MiscUtils.showIntro
import ch.derlin.easypass.helper.PasswordGenerator
import ch.derlin.easypass.helper.Preferences
import ch.derlin.easypass.helper.SelectFileDialog.createSelectFileDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi

// TODO: check connectivity to avoid errors (changing mdp for example...)
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    class Setting(
        val title: String,
        val subtitle: String = "",
        val onClick: (() -> Unit)? = null,
        val icon: Int = R.drawable.ic_settings,
        val confirm: String? = null,
        val isHeader: Boolean = false
    )


    private val settings = listOf(
        Setting("Generator", isHeader = true),
        Setting(
            "Special chars",
            "Set the characters used in passwords.",
            this@SettingsActivity::setSpecialChars, R.drawable.ic_mode_edit
        ),
        Setting("Passwords", isHeader = true),
        Setting(
            "Change",
            "Change your master password.",
            this@SettingsActivity::changePasswordDialog, R.drawable.ic_mode_edit
        ),
        Setting(
            "Clear",
            "Forget all cached passwords.",
            this@SettingsActivity::clearPassword, R.drawable.ic_fingerprint
        ),
        Setting("Data", isHeader = true),
        Setting(
            "File",
            "Change the session to use.",
            this@SettingsActivity::changeSessionFileDialog, R.drawable.ic_cloud_download
        ),
        Setting(
            "Unlink",
            "Unlink EasyPass from your dropbox.",
            this@SettingsActivity::unbindDropbox, R.drawable.ic_dropbox,
            confirm = "Are you sure you want to unbind from Dropbox ?"
        ),
        Setting(
            "Clear cache",
            "Clear the local cache by removing the file on the device.",
            this@SettingsActivity::clearCache, R.drawable.ic_broom,
            confirm = "Really clear all cached data ?"
        ),
        Setting("Other", isHeader = true),
        Setting(
            "Intro",
            "Show the introductory slides.",
            { showIntro() }, R.drawable.ic_info_outline
        ),
        Setting(
            "Changelog",
            "Show the complete changelog.",
            { showChangelog() }, R.drawable.ic_info_outline
        )
    )

    private var working: Boolean
        get() = binding.progressBar.visibility == View.VISIBLE
        set(value) {
            binding.progressBar.visibility = if (value) View.VISIBLE else View.INVISIBLE
        }


    // ----------------------------------------- activity stuff

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.recyclerView.adapter = SettingsAdapter(this, settings)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed(); true
        }

        else -> super.onOptionsItemSelected(item)
    }


    // ----------------------------------------- implement actions

    private fun clearPassword() {
        CachedCredentials.clearPassword()
        Snackbar.make(rootView(), "password cleared.", Snackbar.LENGTH_SHORT).show()
    }

    private fun unbindDropbox() {
        working = true
        DbxManager.unbind().successUi {
            exitApp()
        }.failUi {
            working = false
            Snackbar.make(rootView(), "Error: $it", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun clearCache() {
        if (DbxManager.removeLocalFile()) {
            Snackbar.make(rootView(), "Local file removed.", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setSpecialChars() {
        val view = layoutInflater.inflate(R.layout.dialog_settings_special_chars, null)
        val editText = view.findViewById<EditText>(R.id.specialChars)
        view.findViewById<Button>(R.id.defaultButton).setOnClickListener {
            editText.setText(PasswordGenerator.ALL_SPECIAL_CHARS)
        }

        editText.setText(Preferences.specialChars)

        AlertDialog.Builder(this, R.style.AppTheme_AlertDialog)
            .setView(view)
            .setTitle("Generator special chars")
            .setNegativeButton("cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("use") { _, _ ->
                if (editText.text.isNotBlank()) {
                    Preferences.specialChars = editText.text.toString()
                    Toast.makeText(this, "preference updated.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "invalid empty sequence", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun changePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        AlertDialog.Builder(this, R.style.AppTheme_AlertDialog)
            .setView(view)
            .setTitle("New password")
            .setNegativeButton("cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("change") { _, _ ->
                val pass = view.findViewById<TextInputEditText>(R.id.passwordField).text ?: ""
                if (pass.isBlank() || pass.length < LoadSessionActivity.PasswordFragment.MIN_PASSWORD_LENGTH) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Password too short", Toast.LENGTH_SHORT
                    ).show()
                } else if (pass == DbxManager.accounts.password) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Password did not change.", Toast.LENGTH_SHORT
                    ).show()
                } else {
                    changePassword(pass.toString())
                }
            }
            .show()
    }

    private fun changePassword(newPassword: String, firstTime: Boolean = true) {
        working = true
        val oldPassword = DbxManager.accounts.password
        DbxManager.accounts.password = newPassword
        DbxManager.saveAccounts().alwaysUi {
            working = false
        } successUi {
            CachedCredentials.clearPassword()
            val snack = Snackbar.make(rootView(), "Password changed.", Snackbar.LENGTH_LONG)
            if (firstTime)
                snack.setAction("undo") { changePassword(oldPassword, false) }
            snack.show()
        } failUi {
            DbxManager.accounts.password = oldPassword
            Snackbar.make(rootView(), "Error: $it", Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private fun showChangelog() {
        Changelog.createDialog(this).show()
    }

    private fun changeSessionFileDialog() {
        createSelectFileDialog {
            val intent = Intent(this, LoadSessionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }.show()
    }

    private fun exitApp() {
        val intent = Intent()
        intent.putExtra(BUNDLE_RESTART_KEY, true)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

// ----------------------------------------- inner class

    class SettingsAdapter(val context: Context, private val settings: List<Setting>) :
        RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item: Setting = settings[position]
            holder.titleView.text = item.title

            if (holder is ItemViewHolder) {
                holder.subtitleView.text = item.subtitle
                holder.iconView.setBackgroundResource(item.icon)
                holder.view.setOnClickListener { onItemClick(item) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // create a new view
            return if (viewType == 0) {
                val v = LayoutInflater.from(parent.context).inflate(
                    R.layout.settings_list_header, parent, false
                )
                ViewHolder(v)

            } else {
                val v: View = LayoutInflater.from(parent.context).inflate(
                    R.layout.settings_list_content, parent, false
                )
                ItemViewHolder(v)
            }
        }

        override fun getItemViewType(position: Int): Int = if (settings[position].isHeader) 0 else 1

        override fun getItemCount(): Int = settings.size

        private fun onItemClick(s: Setting) {
            if (s.confirm == null) {
                s.onClick?.invoke()
            } else {
                // confirmation dialog
                AlertDialog.Builder(context)
                    .setMessage(s.confirm)
                    .setPositiveButton("OK") { _, _ -> s.onClick?.invoke() }
                    .setNegativeButton("Cancel") { _, _ -> }
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
        const val BUNDLE_RESTART_KEY = "restart"
    }

}
