package com.jaafer.tvremote.tv

import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

/**
 * Pairing over port 6467 ("polo" protocol). After [start] the TV shows a 6-character
 * hex code; [finish] proves we saw it, after which the TV trusts our certificate.
 */
class PairingSession(private val certs: CertManager) : Closeable {
    class WrongCodeException : IOException("Wrong pairing code")
    class PairingException(message: String) : IOException(message)

    private var socket: SSLSocket? = null

    fun start(host: String, clientName: String) {
        val s = certs.sslContext().socketFactory.createSocket() as SSLSocket
        socket = s
        s.connect(InetSocketAddress(host, PORT), 5000)
        s.soTimeout = 60_000
        s.startHandshake()
        exchange(PAIRING_REQUEST, PAIRING_REQUEST_ACK) {
            string(1, "atvremote")
            string(2, clientName)
        }
        exchange(OPTIONS, OPTIONS) {
            message(1) { int(1, ENCODING_HEX); int(2, CODE_LENGTH) }
            int(3, ROLE_INPUT)
        }
        exchange(CONFIGURATION, CONFIGURATION_ACK) {
            message(1) { int(1, ENCODING_HEX); int(2, CODE_LENGTH) }
            int(2, ROLE_INPUT)
        }
    }

    fun finish(rawCode: String) {
        val s = socket ?: throw PairingException("Not started")
        val code = rawCode.trim().uppercase()
        if (code.length != CODE_LENGTH || code.any { it !in HEX }) throw WrongCodeException()

        val client = certs.certificate.publicKey as RSAPublicKey
        val server = s.session.peerCertificates[0].publicKey as RSAPublicKey
        val hash = MessageDigest.getInstance("SHA-256").run {
            update(client.modulus.unsigned())
            update(client.publicExponent.unsigned())
            update(server.modulus.unsigned())
            update(server.publicExponent.unsigned())
            update(code.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            digest()
        }
        // The first byte of the code is a checksum of the rest, so typos are caught locally.
        if ((hash[0].toInt() and 0xFF) != code.substring(0, 2).toInt(16)) throw WrongCodeException()
        exchange(SECRET, SECRET_ACK) { bytes(1, hash) }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun exchange(field: Int, expected: Int, body: ProtoWriter.() -> Unit) {
        val s = socket ?: throw PairingException("Not connected")
        s.outputStream.writeDelimited(proto {
            int(1, PROTOCOL_VERSION)
            int(2, STATUS_OK)
            message(field, body)
        })
        val reply = ProtoMessage.parse(s.inputStream.readDelimited())
        val status = reply.int(2)
        if (status != STATUS_OK) {
            throw if (status == STATUS_BAD_SECRET) WrongCodeException() else PairingException("Status $status")
        }
        if (!reply.has(expected)) throw PairingException("Unexpected reply")
    }

    private fun BigInteger.unsigned(): ByteArray {
        val bytes = toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private companion object {
        const val PORT = 6467
        const val PROTOCOL_VERSION = 2
        const val STATUS_OK = 200
        const val STATUS_BAD_SECRET = 402
        const val PAIRING_REQUEST = 10
        const val PAIRING_REQUEST_ACK = 11
        const val OPTIONS = 20
        const val CONFIGURATION = 30
        const val CONFIGURATION_ACK = 31
        const val SECRET = 40
        const val SECRET_ACK = 41
        const val ENCODING_HEX = 3
        const val ROLE_INPUT = 1
        const val CODE_LENGTH = 6
        const val HEX = "0123456789ABCDEF"
    }
}
