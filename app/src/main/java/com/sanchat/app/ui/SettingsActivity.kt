package com.sanchat.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.sanchat.app.R
import com.sanchat.app.store.Store

class SettingsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var etBackend: EditText
    private lateinit var etToken: EditText
    private lateinit var rgLanguage: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        store = Store(this)

        etBackend = findViewById(R.id.etBackend)
        etToken = findViewById(R.id.etToken)
        rgLanguage = findViewById(R.id.rgLanguage)
        val rbPt = findViewById<RadioButton>(R.id.rbPt)
        val rbEn = findViewById<RadioButton>(R.id.rbEn)
        val topBar = findViewById<View>(R.id.topBar)
        val root = findViewById<View>(R.id.root)

        etBackend.setText(store.backendUrl())
        etToken.setText(store.accessToken())

        val locales = AppCompatDelegate.getApplicationLocales()
        val isEn = locales.toLanguageTags().startsWith("en")
        if (isEn) rbEn.isChecked = true else rbPt.isChecked = true

        // Rodape com a versao do app
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        findViewById<TextView>(R.id.tvVersion).text =
            getString(R.string.app_name) + "  v" + ver

        applyInsets(root, topBar)

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

    /** Status bar em cima, nav bar embaixo (o conteudo ganha padding bottom). */
    private fun applyInsets(root: View, topBar: View) {
        val baseTop = topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(bars.bottom, ime.bottom)
            topBar.setPadding(
                topBar.paddingLeft, baseTop + bars.top, topBar.paddingRight, topBar.paddingBottom
            )
            root.setPadding(0, 0, 0, bottom)
            insets
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
