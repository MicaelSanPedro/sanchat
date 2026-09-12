package com.sanchat.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sanchat.app.R
import com.sanchat.app.store.Store

class SettingsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var etBackend: EditText
    private lateinit var etToken: EditText
    private lateinit var rgLanguage: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = Store(this)

        etBackend = findViewById(R.id.etBackend)
        etToken = findViewById(R.id.etToken)
        rgLanguage = findViewById(R.id.rgLanguage)
        val rbPt = findViewById<RadioButton>(R.id.rbPt)
        val rbEn = findViewById<RadioButton>(R.id.rbEn)

        etBackend.setText(store.backendUrl())
        etToken.setText(store.accessToken())

        val locales = AppCompatDelegate.getApplicationLocales()
        val isEn = locales.toLanguageTags().startsWith("en")
        if (isEn) rbEn.isChecked = true else rbPt.isChecked = true

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            store.setBackendUrl(etBackend.text.toString())
            store.setAccessToken(etToken.text.toString())
            val tag = if (rbEn.isChecked) "en" else "pt-BR"
            store.setLanguageChosen()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_conversations)
                .setMessage(R.string.clear_conversations_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    store.deleteAllConversations()
                    Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
