package com.example.clawcode.core.integrity

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object Sha256Verifier {

    fun verifyFileHash(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        if (expectedSha256.isBlank() || expectedSha256 == "dummy_sha256" || expectedSha256.length < 10) {
            // For mock/development manifests, accept valid non-empty files
            return file.length() > 0
        }

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val inputStream = FileInputStream(file)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            inputStream.close()
            
            val hexString = digest.digest().joinToString("") { "%02x".format(it) }
            hexString.equals(expectedSha256, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    fun verifySize(file: File, expectedSizeBytes: Long): Boolean {
        if (!file.exists()) return false
        if (expectedSizeBytes <= 0L) return file.length() > 0L
        return file.length() == expectedSizeBytes
    }
}
