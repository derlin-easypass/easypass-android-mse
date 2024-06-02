package ch.derlin.easypass.helper

import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import ch.derlin.easypass.easypass.R
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi

/**
 * This class lets you create a dialog in order to change the session to use.
 * date 08.01.18
 * @author Lucy Linder
 */

object SelectFileDialog {

    /**
     * Create the dialog to change session.
     * The dialog lets you:
     *  - specify a name from an EditText
     *  - select an already existing remove file from a dropdown
     *
     *  @param callback the function to call in case the user confirmed the
     *      session change. Usually, the only thing left to do is to restart the
     *      application (preferences already updated)
     *  @return the dialog. Don't forget to call [AlertDialog.show] !
     */
    fun Activity.createSelectFileDialog(callback: () -> Unit): AlertDialog {
        // create an initialise the view
        val view = layoutInflater.inflate(R.layout.edit_filename, null)
        val filenameEditText = view.findViewById<EditText>(R.id.file_name)
        val chooseFileButton = view.findViewById<Button>(R.id.choose_file_btn)
        val oldFilename = Preferences.remoteFilePathDisplay

        filenameEditText.setText(oldFilename)
        chooseFileButton.isEnabled = false

        view.findViewById<Button>(R.id.btn_filename_default).setOnClickListener { _ ->
            filenameEditText.setText(Preferences.defaultRemoteFilePath)
        }

        // get the list of session and construct the dropdown on success
        DbxManager.listSessionFiles().successUi { files ->
            if (files.isNotEmpty()) {
                chooseFileButton.isEnabled = true
                chooseFileButton.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setItems(files) { dialog, pos -> filenameEditText.setText(files[pos]) }
                        .setNegativeButton("dismiss") { _, _ -> }
                        .show()
                }
            }
        } failUi {
            Toast.makeText(this, "error: $it", Toast.LENGTH_LONG).show()
        }

        // actually create the dialog
        return AlertDialog.Builder(this, R.style.AppTheme_AlertDialog)
            .setView(view)
            .setNegativeButton("cancel") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("change") { _, _ ->
                val filename = filenameEditText.text.toString().trim()
                if (filename.isBlank() || !filename.matches(Regex("""^[0-9a-zA-Z_ -]+\.[a-zA-Z_-]+$"""))) {
                    Toast.makeText(
                        this,
                        "Wrong characters in filename", Toast.LENGTH_SHORT
                    ).show()
                } else if (filename == oldFilename) {
                    Toast.makeText(
                        this,
                        "Filename did not change.", Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Preferences.remoteFilePath = filename
                    CachedCredentials.clearPassword()
                    callback()
                }
            }.create()
    }
}