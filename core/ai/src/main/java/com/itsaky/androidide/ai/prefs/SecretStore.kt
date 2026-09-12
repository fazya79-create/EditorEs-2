/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.ai.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.slf4j.LoggerFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore(context: Context) {

  private val prefs =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  init {
    // Secrets of removed providers would otherwise linger in storage forever.
    LEGACY_KEYS.filter { prefs.contains(it) }.forEach { key ->
      prefs.edit().remove(key).apply()
    }
  }

  fun put(key: String, value: String) {
    if (value.isEmpty()) {
      remove(key)
      return
    }

    val encrypted = runCatching { encrypt(value) }.getOrElse { err ->
      log.error("Failed to encrypt secret for key {}", key, err)
      return
    }

    prefs.edit().putString(key, encrypted).apply()
  }

  fun get(key: String): String {
    val stored = prefs.getString(key, null) ?: return ""
    return runCatching { decrypt(stored) }.getOrElse { err ->
      log.error("Failed to decrypt secret for key {}", key, err)
      ""
    }
  }

  fun has(key: String): Boolean = get(key).isNotEmpty()

  fun remove(key: String) {
    prefs.edit().remove(key).apply()
  }

  private fun encrypt(value: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())

    val iv = cipher.iv
    val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

    val combined = ByteArray(iv.size + encrypted.size)
    iv.copyInto(combined)
    encrypted.copyInto(combined, iv.size)

    return Base64.encodeToString(combined, Base64.NO_WRAP)
  }

  private fun decrypt(value: String): String {
    val combined = Base64.decode(value, Base64.NO_WRAP)
    if (combined.size <= IV_LENGTH) {
      return ""
    }

    val iv = combined.copyOfRange(0, IV_LENGTH)
    val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)

    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))

    return String(cipher.doFinal(encrypted), Charsets.UTF_8)
  }

  private fun secretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
      return it.secretKey
    }

    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(KEY_SIZE)
        .build()
    )
    return generator.generateKey()
  }

  companion object {

    private val log = LoggerFactory.getLogger(SecretStore::class.java)

    private const val PREFS_NAME = "ide.ai.secrets"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "ide.ai.secrets.masterKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private const val KEY_SIZE = 256
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    /** Secrets written by provider integrations that no longer exist. */
    internal val LEGACY_KEYS = listOf("brave.apiKey")
  }
}
