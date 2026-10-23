package com.jaafer.tvremote.tv

import android.annotation.SuppressLint
import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Holds the client certificate the TV remembers after pairing.
 * The self-signed certificate is built by hand (tiny DER encoder) to avoid a crypto library.
 */
class CertManager(context: Context) {
    private val file = File(context.filesDir, "atv_client.p12")
    private val password = "atvremote".toCharArray()
    private val keyStore: KeyStore by lazy { loadOrCreate() }
    @Volatile private var context: SSLContext? = null

    val certificate: X509Certificate get() = keyStore.getCertificate(ALIAS) as X509Certificate

    fun sslContext(): SSLContext = context ?: synchronized(this) {
        context ?: SSLContext.getInstance("TLS").apply {
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, password)
            init(kmf.keyManagers, arrayOf(TrustAll), SecureRandom())
        }.also { context = it }
    }

    private fun loadOrCreate(): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        if (file.exists()) {
            val loaded = runCatching {
                file.inputStream().use { ks.load(it, password) }
                ks.containsAlias(ALIAS)
            }.getOrDefault(false)
            if (loaded) return ks
        }
        ks.load(null, null)
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = selfSigned(pair.public, pair.private)
        ks.setKeyEntry(ALIAS, pair.private, password, arrayOf(cert))
        file.outputStream().use { ks.store(it, password) }
        return ks
    }

    private fun selfSigned(publicKey: PublicKey, privateKey: PrivateKey): X509Certificate {
        val now = System.currentTimeMillis()
        val sha256WithRsa = seq(oid("1.2.840.113549.1.1.11"), byteArrayOf(0x05, 0x00))
        val name = seq(set(seq(oid("2.5.4.3"), der(0x0C, "atvremote".toByteArray()))))
        val tbs = seq(
            der(0xA0, integer(BigInteger.valueOf(2))), // v3
            integer(BigInteger.valueOf(now)),
            sha256WithRsa,
            name,
            seq(utcTime(Date(now - DAY)), utcTime(Date(now + 10 * 365 * DAY))),
            name,
            publicKey.encoded,
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(tbs)
            sign()
        }
        val certDer = seq(tbs, sha256WithRsa, der(0x03, byteArrayOf(0) + signature))
        return CertificateFactory.getInstance("X.509").generateCertificate(certDer.inputStream()) as X509Certificate
    }

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private object TrustAll : X509TrustManager {
        // The TV uses a self-signed certificate; trust is established by the pairing code.
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val ALIAS = "client"
        const val DAY = 86_400_000L

        fun der(tag: Int, content: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(tag)
            val len = content.size
            when {
                len < 0x80 -> out.write(len)
                len < 0x100 -> { out.write(0x81); out.write(len) }
                else -> { out.write(0x82); out.write(len shr 8); out.write(len and 0xFF) }
            }
            out.write(content)
            return out.toByteArray()
        }

        fun seq(vararg parts: ByteArray) = der(0x30, parts.fold(ByteArray(0)) { acc, p -> acc + p })
        fun set(vararg parts: ByteArray) = der(0x31, parts.fold(ByteArray(0)) { acc, p -> acc + p })
        fun integer(value: BigInteger) = der(0x02, value.toByteArray())

        fun oid(dotted: String): ByteArray {
            val parts = dotted.split('.').map { it.toLong() }
            val out = ByteArrayOutputStream()
            out.write((parts[0] * 40 + parts[1]).toInt())
            for (p in parts.drop(2)) {
                val stack = ArrayList<Int>()
                var v = p
                do { stack.add((v and 0x7F).toInt()); v = v shr 7 } while (v > 0)
                for (i in stack.indices.reversed()) out.write(if (i > 0) stack[i] or 0x80 else stack[i])
            }
            return der(0x06, out.toByteArray())
        }

        fun utcTime(date: Date): ByteArray {
            val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return der(0x17, fmt.format(date).toByteArray())
        }
    }
}
