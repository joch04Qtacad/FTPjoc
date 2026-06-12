package com.example.ftpt_joc

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SettingsManager(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences = EncryptedSharedPreferences.create(
        "ftp_settings",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSettings(host: String, user: String, pass: String, dir: String) {
        sharedPreferences.edit().apply {
            putString("host", host)
            putString("user", user)
            putString("pass", pass)
            putString("dir", dir)
            apply()
        }
    }

    fun getSettings(): Map<String, String> {
        return mapOf(
            "host" to (sharedPreferences.getString("host", "") ?: ""),
            "user" to (sharedPreferences.getString("user", "") ?: ""),
            "pass" to (sharedPreferences.getString("pass", "") ?: ""),
            "dir" to (sharedPreferences.getString("dir", "/photos") ?: "")
        )
    }
}
