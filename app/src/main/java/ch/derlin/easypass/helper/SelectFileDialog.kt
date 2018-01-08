package ch.derlin.easypass.helper

import android.app.Activity
import android.content.Context
import android.support.v7.app.AlertDialog
import android.view.View
import android.widget.*
import ch.derlin.easypass.easypass.R
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi

/**
 * Created by Lin on 08.01.18.
 */

object SelectFileDialog {

    fun Activity.createSelectFileDialog(callback: () -> Unit): AlertDialog {
        val prefs = Preferences()
        val view = layoutInflater.inflate(R.layout.edit_filename, null)
        val editText = view.findViewById<EditText>(R.id.filename)
        val chooseBtn = view.findViewById<Button>(R.id.choose_btn)
        val oldFilename = prefs.remoteFilePathDisplay

        editText.setText(oldFilename)
        chooseBtn.isEnabled = false

        view.findViewById<Button>(R.id.btn_default).setOnClickListener { _ ->
            editText.setText(Preferences.defaultRemoteFilePath)
        }



        DbxManager.listSessionFiles().successUi { files ->
            if(files.isNotEmpty()) {
                chooseBtn.isEnabled = true
                chooseBtn.setOnClickListener { _ ->
                    AlertDialog.Builder(this)
                            .setItems(files, { dialog, pos -> editText.setText(files[pos]) })
                            .setNegativeButton("dismiss", { _, _ -> })
                            .show()
                }
            }
//            val adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, files)
//            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//            val spinner = view.findViewById<Spinner>(R.id.files_spinner)
//            spinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
//                override fun onItemSelected(p0: AdapterView<*>?, v: View?, pos: Int, id: Long) {
//                    editText.setText(files[pos])
//                }
//
//                override fun onNothingSelected(p0: AdapterView<*>?) {}
//            })
//            spinner.adapter = adapter
//            val currentPosition = files.indexOf(prefs.remoteFilePathDisplay)
//            if(currentPosition >= 0) spinner.setSelection(currentPosition)

        } failUi {
            Toast.makeText(this, "error: " + it, Toast.LENGTH_LONG).show()
        }

        return android.support.v7.app.AlertDialog.Builder(this, R.style.AppTheme_AlertDialog)
                .setView(view)
                .setNegativeButton("cancel", { dialog, _ -> dialog.dismiss() })
                .setPositiveButton("change", { dialog, _ ->
                    val filename = editText.text.toString().trim()
                    if (filename.isBlank() || !filename.matches(Regex("""^[0-9a-zA-Z_ -]+\.[a-zA-Z_-]+$"""))) {
                        Toast.makeText(this,
                                "Wrong characters in filename", Toast.LENGTH_SHORT).show()
                    } else if (filename.equals(oldFilename)) {
                        Toast.makeText(this,
                                "Filename did not change.", Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.remoteFilePath = filename
                        CachedCredentials.clearPassword()
                        callback()
                    }
                }).create()

    }
}