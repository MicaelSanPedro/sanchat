package com.sanchat.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Padrao: portugues. Se o usuario ja escolheu, o AppCompat restaura sozinho
        // (autoStoreLocales). Se nao ha preferencia nenhuma, forcamos PT-BR.
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty && !Store(this).languageChosen()) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags("pt-BR")
            )
        }
    }
}
