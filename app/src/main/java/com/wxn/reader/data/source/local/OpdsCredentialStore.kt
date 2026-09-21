package com.wxn.reader.data.source.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wxn.base.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpdsCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 惰性初始化：AndroidKeyStore 仅在真正读写凭据时才访问（Robolectric 等无 KeyStore 环境可安全构造本类）
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "opds_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCredentials(catalogId: Long, username: String, password: String) {
        prefs.edit()
            .putString(keyUsername(catalogId), username)
            .putString(keyPassword(catalogId), password)
            .putString(keyAuthType(catalogId), "BASIC")
            .apply()
        Logger.d("OpdsCredentialStore::saveCredentials for catalogId=$catalogId")
    }

    fun getCredentials(catalogId: Long): Pair<String, String>? {
        val username = prefs.getString(keyUsername(catalogId), null) ?: return null
        val password = prefs.getString(keyPassword(catalogId), null) ?: return null
        return username to password
    }

    fun hasCredentials(catalogId: Long): Boolean {
        return prefs.contains(keyUsername(catalogId))
    }

    fun removeCredentials(catalogId: Long) {
        prefs.edit()
            .remove(keyUsername(catalogId))
            .remove(keyPassword(catalogId))
            .remove(keyAuthType(catalogId))
            .apply()
        Logger.d("OpdsCredentialStore::removeCredentials for catalogId=$catalogId")
    }

    private fun keyUsername(catalogId: Long) = "opds_${catalogId}_user"
    private fun keyPassword(catalogId: Long) = "opds_${catalogId}_pass"
    private fun keyAuthType(catalogId: Long) = "opds_${catalogId}_type"
}
