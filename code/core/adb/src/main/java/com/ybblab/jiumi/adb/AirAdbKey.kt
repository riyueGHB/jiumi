// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.adb

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.ProviderException
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Arrays
import java.util.Base64
import javax.crypto.Cipher
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

class AirAdbKey private constructor(
    private val privateKey: PrivateKey,
    private val publicKey: RSAPublicKey,
    val publicKeyBytes: ByteArray
) {
    val sslContext: SSLContext by lazy { createSslContext() }

    fun signToken(token: ByteArray): ByteArray {
        val digestInfo = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        )
        val payload = digestInfo + token
        val block = ByteArray(KEY_LENGTH_BYTES)
        block[0] = 0
        block[1] = 1
        val separator = KEY_LENGTH_BYTES - payload.size - 1
        Arrays.fill(block, 2, separator, 0xff.toByte())
        block[separator] = 0
        System.arraycopy(payload, 0, block, KEY_LENGTH_BYTES - payload.size, payload.size)

        return Cipher.getInstance("RSA/ECB/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, privateKey)
            doFinal(block)
        }
    }

    /**
     * TLS for Wireless debugging. Trust runs the other way round from the web: adbd decides whether to
     * accept this app, by the key it allowed when pairing (or the user allows on the local port), and
     * its own certificate is self-signed and made anew, so there is no authority to check it against;
     * Shizuku and the adb tool accept it the same way. Connections only ever go to this phone itself,
     * 127.0.0.1 or its own Wi-Fi address.
     */
    @android.annotation.SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun createSslContext(): SSLContext {
        val certificate = createSelfSignedCertificate(privateKey, publicKey)
        val keyManager = object : X509ExtendedKeyManager() {
            private val alias = "adb"

            override fun chooseClientAlias(keyTypes: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? {
                return keyTypes?.firstOrNull { it == "RSA" }?.let { alias }
            }

            override fun chooseEngineClientAlias(keyTypes: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String? {
                return keyTypes?.firstOrNull { it == "RSA" }?.let { alias }
            }

            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
            override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? = null
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
                if (alias == this.alias) arrayOf(certificate) else null

            override fun getPrivateKey(alias: String?): PrivateKey? =
                if (alias == this.alias) privateKey else null
        }
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        return SSLContext.getInstance("TLS").apply {
            init(arrayOf(keyManager), arrayOf(trustManager), null)
        }
    }

    companion object {
        private const val KEY_LENGTH_BITS = 2048
        private const val KEY_LENGTH_BYTES = KEY_LENGTH_BITS / 8
        private const val KEY_LENGTH_WORDS = KEY_LENGTH_BYTES / 4
        private const val PUBLIC_KEY_STRUCT_SIZE = 524
        private val base64MimeEncoder = Base64.getMimeEncoder(64, "\n".toByteArray(Charsets.UTF_8))

        private data class KeyMaterial(val privateKey: PrivateKey, val publicKey: RSAPublicKey)

        @Synchronized
        internal fun loadOrCreate(
            privateKeyFile: File,
            publicKeyFile: File,
            replaceInvalidKey: Boolean = false,
            onRejectedKey: (InvalidAdbPrivateKeyException) -> Unit = {}
        ): AirAdbKey {
            val keys = if (!privateKeyFile.exists()) {
                generate(privateKeyFile)
            } else {
                try {
                    readPrivateKey(privateKeyFile)
                } catch (error: InvalidAdbPrivateKeyException) {
                    onRejectedKey(error)
                    if (!replaceInvalidKey) throw error
                    generate(privateKeyFile)
                }
            }
            val publicFileBytes = adbPublicKeyFileBytes(keys.publicKey)
            if (!publicKeyFile.exists() || !publicKeyFile.readBytes().contentEquals(publicFileBytes)) {
                publicKeyFile.parentFile?.mkdirs()
                publicKeyFile.writeBytes(publicFileBytes)
            }
            return AirAdbKey(keys.privateKey, keys.publicKey, publicFileBytes + 0.toByte())
        }

        private fun generate(privateKeyFile: File): KeyMaterial {
            val bytes = try {
                KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_LENGTH_BITS) }
                    .generateKeyPair().private.encoded
            } catch (error: GeneralSecurityException) {
                throw InvalidAdbPrivateKeyException("generate RSA key", error)
            } catch (error: ProviderException) {
                throw InvalidAdbPrivateKeyException("generate RSA key", error)
            } catch (error: IllegalArgumentException) {
                throw InvalidAdbPrivateKeyException("generate RSA key", error)
            } ?: throw InvalidAdbPrivateKeyException("RSA provider did not export a private key")
            // Check the exact bytes that future starts will read before replacing the stored key.
            val keys = decodePrivateKey(bytes)
            privateKeyFile.parentFile?.mkdirs()
            val encoded = base64MimeEncoder.encodeToString(bytes)
            val temporary = File.createTempFile("adb-key-", ".tmp", privateKeyFile.parentFile)
            try {
                temporary.writeText("-----BEGIN PRIVATE KEY-----\n$encoded\n-----END PRIVATE KEY-----", Charsets.UTF_8)
                Files.move(temporary.toPath(), privateKeyFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
            return keys
        }

        private fun readPrivateKey(file: File): KeyMaterial {
            val encoded = file.readText(Charsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .filterNot { it.isWhitespace() }
            val bytes = try {
                Base64.getDecoder().decode(encoded)
            } catch (error: IllegalArgumentException) {
                throw InvalidAdbPrivateKeyException("Base64", error)
            }
            return decodePrivateKey(bytes)
        }

        private fun decodePrivateKey(bytes: ByteArray): KeyMaterial {
            try {
                val factory = KeyFactory.getInstance("RSA")
                val key = factory.generatePrivate(PKCS8EncodedKeySpec(bytes))
                val publicSpec = if (key is RSAPrivateCrtKey) {
                    RSAPublicKeySpec(key.modulus, key.publicExponent)
                } else {
                    // Android 12 can import a complete PKCS#8 key as a plain RSAPrivateKey.
                    val encodedPublic = rsaPublicParameters(bytes)
                    val modulus = (key as? RSAPrivateKey)?.modulus
                        ?: factory.getKeySpec(key, RSAPrivateKeySpec::class.java).modulus
                    if (modulus != encodedPublic.modulus) throw InvalidKeySpecException("RSA modulus mismatch")
                    encodedPublic
                }
                val modulusBits = publicSpec.modulus.bitLength()
                if (modulusBits != KEY_LENGTH_BITS || !publicSpec.modulus.testBit(0)) {
                    throw InvalidAdbPrivateKeyException("invalid RSA modulus ($modulusBits bits)")
                }
                if (publicSpec.publicExponent.signum() <= 0 || publicSpec.publicExponent.bitLength() > 32) {
                    throw InvalidAdbPrivateKeyException("invalid RSA public exponent")
                }
                val publicKey = factory.generatePublic(publicSpec) as? RSAPublicKey
                    ?: throw InvalidKeySpecException("RSA public key required")
                return KeyMaterial(key, publicKey)
            } catch (error: GeneralSecurityException) {
                throw InvalidAdbPrivateKeyException("read PKCS#8 RSA key", error)
            } catch (error: ProviderException) {
                throw InvalidAdbPrivateKeyException("read PKCS#8 RSA key", error)
            }
        }

        /** Read just the public fields of the PKCS#8 key already accepted by the RSA factory. */
        private fun rsaPublicParameters(bytes: ByteArray): RSAPublicKeySpec {
            val privateKeyInfo = ByteBuffer.wrap(bytes).derElement(0x30)
            privateKeyInfo.derElement(0x02) // version
            privateKeyInfo.derElement(0x30) // algorithm identifier
            val rsa = privateKeyInfo.derElement(0x04).derElement(0x30)
            rsa.derElement(0x02) // version
            return RSAPublicKeySpec(rsa.derInteger(), rsa.derInteger())
        }

        private fun ByteBuffer.derInteger(): BigInteger {
            val value = derElement(0x02)
            if (!value.hasRemaining()) throw InvalidKeySpecException("Empty RSA DER integer")
            return BigInteger(ByteArray(value.remaining()).also { value.get(it) })
        }

        private fun ByteBuffer.derElement(tag: Int): ByteBuffer {
            if (remaining() < 2 || (get().toInt() and 0xff) != tag) {
                throw InvalidKeySpecException("Unexpected RSA DER element")
            }
            val marker = get().toInt() and 0xff
            var length = marker.toLong()
            if (marker >= 0x80) {
                val count = marker and 0x7f
                if (count !in 1..4 || count > remaining()) throw InvalidKeySpecException("Invalid RSA DER length")
                length = 0
                repeat(count) { length = (length shl 8) or (get().toLong() and 0xff) }
            }
            if (length > remaining()) throw InvalidKeySpecException("Truncated RSA DER element")
            val size = length.toInt()
            val element = slice().apply { limit(size) }
            position(position() + size)
            return element
        }

        private fun adbPublicKeyFileBytes(publicKey: RSAPublicKey): ByteArray {
            val encoded = Base64.getEncoder().encodeToString(convertRsaPublicKeyToAdbFormat(publicKey))
            return "$encoded unknown@unknown".toByteArray(Charsets.UTF_8)
        }

        private fun convertRsaPublicKeyToAdbFormat(publicKey: RSAPublicKey): ByteArray {
            val r32 = BigInteger.ZERO.setBit(32)
            val modulus = publicKey.modulus
            val n0Inv = modulus.remainder(r32).modInverse(r32).negate()
            val rr = BigInteger.ZERO.setBit(KEY_LENGTH_WORDS * 32)
                .modPow(BigInteger.valueOf(2), modulus)

            return ByteBuffer.allocate(PUBLIC_KEY_STRUCT_SIZE).order(ByteOrder.LITTLE_ENDIAN).run {
                putInt(KEY_LENGTH_WORDS)
                putInt(n0Inv.toInt())
                toAdbWords(modulus).forEach { putInt(it) }
                toAdbWords(rr).forEach { putInt(it) }
                putInt(publicKey.publicExponent.toInt())
                array()
            }
        }

        private fun toAdbWords(value: BigInteger): IntArray {
            val words = IntArray(KEY_LENGTH_WORDS)
            val r32 = BigInteger.ZERO.setBit(32)
            var current = value
            for (index in words.indices) {
                val parts = current.divideAndRemainder(r32)
                words[index] = parts[1].toInt()
                current = parts[0]
            }
            return words
        }

        private fun createSelfSignedCertificate(
            privateKey: PrivateKey,
            publicKey: RSAPublicKey
        ): X509Certificate {
            val algorithmIdentifier = derSequence(
                derOid("1.2.840.113549.1.1.11"),
                derNull()
            )
            val name = derSequence(
                derSet(
                    derSequence(
                        derOid("2.5.4.3"),
                        derUtf8String("Jiumi")
                    )
                )
            )
            val validity = derSequence(
                derGeneralizedTime("20200101000000Z"),
                derGeneralizedTime("20491231235959Z")
            )
            val tbsCertificate = derSequence(
                derInteger(BigInteger.ONE),
                algorithmIdentifier,
                name,
                validity,
                name,
                publicKey.encoded
            )
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(privateKey)
                update(tbsCertificate)
                sign()
            }
            val certificate = derSequence(
                tbsCertificate,
                algorithmIdentifier,
                derBitString(signature)
            )
            return CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certificate)) as X509Certificate
        }

        private fun derSequence(vararg values: ByteArray): ByteArray = derTagged(0x30, values.concat())
        private fun derSet(vararg values: ByteArray): ByteArray = derTagged(0x31, values.concat())
        private fun derNull(): ByteArray = byteArrayOf(0x05, 0x00)
        private fun derUtf8String(value: String): ByteArray = derTagged(0x0c, value.toByteArray(Charsets.UTF_8))
        private fun derGeneralizedTime(value: String): ByteArray = derTagged(0x18, value.toByteArray(Charsets.US_ASCII))
        private fun derBitString(value: ByteArray): ByteArray = derTagged(0x03, byteArrayOf(0) + value)

        private fun derInteger(value: BigInteger): ByteArray {
            val bytes = value.toByteArray()
            return derTagged(0x02, bytes)
        }

        private fun derOid(value: String): ByteArray {
            val parts = value.split(".").map { it.toLong() }
            val body = ByteArrayOutputStream()
            body.write((parts[0] * 40 + parts[1]).toInt())
            parts.drop(2).forEach { part ->
                val stack = ArrayDeque<Byte>()
                var current = part
                stack.addFirst((current and 0x7f).toByte())
                current = current shr 7
                while (current > 0) {
                    stack.addFirst(((current and 0x7f) or 0x80).toByte())
                    current = current shr 7
                }
                stack.forEach { body.write(it.toInt()) }
            }
            return derTagged(0x06, body.toByteArray())
        }

        private fun derTagged(tag: Int, body: ByteArray): ByteArray {
            return byteArrayOf(tag.toByte()) + derLength(body.size) + body
        }

        private fun derLength(length: Int): ByteArray {
            if (length < 128) return byteArrayOf(length.toByte())
            var current = length
            val bytes = ArrayDeque<Byte>()
            while (current > 0) {
                bytes.addFirst((current and 0xff).toByte())
                current = current shr 8
            }
            return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
        }

        private fun Array<out ByteArray>.concat(): ByteArray {
            val out = ByteArrayOutputStream()
            forEach { out.write(it) }
            return out.toByteArray()
        }
    }
}

internal class InvalidAdbPrivateKeyException(private val stage: String, cause: Throwable? = null) :
    IOException("Invalid local ADB private key", cause) {
    val diagnostic: String get() = listOfNotNull(
        stage, (cause as? InvalidAdbPrivateKeyException)?.diagnostic ?: cause?.javaClass?.simpleName
    ).joinToString(": ")
}
