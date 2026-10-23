package com.jaafer.tvremote.tv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Minimal protobuf encoder, enough for the Android TV Remote v2 protocol. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int(field: Int, value: Int) = apply { tag(field, 0); writeVarint(out, value.toLong()) }
    fun bool(field: Int, value: Boolean) = int(field, if (value) 1 else 0)
    fun bytes(field: Int, value: ByteArray) = apply {
        tag(field, 2)
        writeVarint(out, value.size.toLong())
        out.write(value)
    }
    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))
    fun message(field: Int, block: ProtoWriter.() -> Unit = {}) = bytes(field, proto(block))

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())
}

fun proto(block: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(block).toByteArray()

fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while (v and 0x7FL.inv() != 0L) {
        out.write(((v and 0x7F) or 0x80).toInt())
        v = v ushr 7
    }
    out.write(v.toInt())
}

/** Decoded protobuf message: varints are kept as Long, length-delimited fields as ByteArray. */
class ProtoMessage private constructor(private val fields: Map<Int, List<Any>>) {
    fun has(field: Int) = fields.containsKey(field)
    fun int(field: Int): Int = (fields[field]?.lastOrNull() as? Long)?.toInt() ?: 0
    fun bool(field: Int) = int(field) != 0
    fun bytes(field: Int): ByteArray? = fields[field]?.lastOrNull() as? ByteArray
    fun string(field: Int): String = bytes(field)?.toString(Charsets.UTF_8) ?: ""
    fun message(field: Int): ProtoMessage? = bytes(field)?.let(::parse)

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = HashMap<Int, MutableList<Any>>()
            var pos = 0
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    require(pos < data.size) { "Truncated varint" }
                    val b = data[pos++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            while (pos < data.size) {
                val key = varint().toInt()
                val value: Any = when (key and 7) {
                    0 -> varint()
                    1 -> { pos += 8; continue }
                    2 -> {
                        val len = varint().toInt()
                        data.copyOfRange(pos, pos + len).also { pos += len }
                    }
                    5 -> { pos += 4; continue }
                    else -> throw IllegalArgumentException("Unsupported wire type ${key and 7}")
                }
                fields.getOrPut(key ushr 3) { mutableListOf() }.add(value)
            }
            return ProtoMessage(fields)
        }
    }
}

/** Reads one varint-length-prefixed message. */
fun InputStream.readDelimited(): ByteArray {
    var len = 0
    var shift = 0
    while (true) {
        val b = read()
        if (b < 0) throw EOFException()
        len = len or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) break
        shift += 7
    }
    val buf = ByteArray(len)
    var off = 0
    while (off < len) {
        val n = read(buf, off, len - off)
        if (n < 0) throw EOFException()
        off += n
    }
    return buf
}

fun OutputStream.writeDelimited(message: ByteArray) {
    val frame = ByteArrayOutputStream(message.size + 5)
    writeVarint(frame, message.size.toLong())
    frame.write(message)
    write(frame.toByteArray())
    flush()
}
