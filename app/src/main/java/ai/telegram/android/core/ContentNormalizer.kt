package ai.telegram.android.core

import java.security.MessageDigest
import java.text.Normalizer

object ContentNormalizer {
    fun normalize(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFKC)
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }

    fun contentHash(input: String): String {
        val normalized = normalize(input)
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
