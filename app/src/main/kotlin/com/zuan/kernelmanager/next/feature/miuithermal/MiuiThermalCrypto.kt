package com.zuan.kernelmanager.next.feature.miuithermal

import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object MiuiThermalCrypto {
    private const val KEY = "thermalopenssl.h"
    private const val IV = "thermalopenssl.h"

    fun encrypt(data: ByteArray): ByteArray = crypt(Cipher.ENCRYPT_MODE, data)

    fun decrypt(data: ByteArray): ByteArray = crypt(Cipher.DECRYPT_MODE, data)

    private fun crypt(mode: Int, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val key = SecretKeySpec(KEY.toByteArray(StandardCharsets.UTF_8), "AES")
        val iv = IvParameterSpec(IV.toByteArray(StandardCharsets.UTF_8))
        cipher.init(mode, key, iv)
        return cipher.doFinal(data)
    }
}
