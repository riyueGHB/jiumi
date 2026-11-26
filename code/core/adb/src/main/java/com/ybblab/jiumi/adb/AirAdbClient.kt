// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.adb

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

class AirAdbClient(
    private val host: String,
    private val port: Int,
    private val adbKey: AirAdbKey,
    private val allowUserAuthorization: Boolean = false,
    private val legacyOnly: Boolean = false,
    private val requireTls: Boolean = false
) : Closeable {
    private val socket = Socket()
    @Volatile private var tlsSocket: SSLSocket? = null
    private lateinit var inputStream: DataInputStream
    private lateinit var outputStream: DataOutputStream
    private val nextLocalId = AtomicInteger(1)
    @Volatile private var connected = false
    // Serialize streams on the shared socket so one command cannot consume another command's packets.
    private val shellLock = Any()

    fun connect() {
        if (connected) return
        val tcpSocket = socket.also {
            it.tcpNoDelay = true
            it.connect(InetSocketAddress(host, port), if (legacyOnly) 750 else CONNECT_TIMEOUT_MS)
            // Give the socket a read timeout. On a half-open connection (the peer's Wi-Fi dropped or it slept without sending RST),
            // readFully without one blocks until the OS-level TCP timeout (minutes). That was the real cause of background work
            // being "very slow and mostly failing" (a single screenshot or injection hung for 6 minutes until the wall timeout).
            // With soTimeout set, any stuck read throws SocketTimeoutException within seconds; the caller then
            // marks the shell down and reconnects, so the loop is never hung forever by one read.
            it.soTimeout = if (legacyOnly) 1_500 else READ_TIMEOUT_MS
        }
        inputStream = DataInputStream(tcpSocket.inputStream)
        outputStream = DataOutputStream(tcpSocket.outputStream)

        writeMessage(A_CNXN, A_VERSION, A_MAXDATA, "host::features=shell_v2".adbPayload())
        var message = readMessage()
        if (requireTls && message.command != A_STLS) {
            throw IOException("Expected a Wireless debugging TLS port")
        }
        when (message.command) {
            A_STLS -> {
                if (legacyOnly) error("Expected a classic ADB port")
                writeMessage(A_STLS, A_VERSION, 0, null)
                val sslSocket = adbKey.sslContext.socketFactory
                    .createSocket(tcpSocket, host, port, true) as SSLSocket
                tlsSocket = sslSocket
                sslSocket.soTimeout = READ_TIMEOUT_MS
                try {
                    sslSocket.startHandshake()
                    inputStream = DataInputStream(sslSocket.inputStream)
                    outputStream = DataOutputStream(sslSocket.outputStream)
                    // TLS 1.3 can deliver a client-certificate rejection on the first read.
                    message = readMessage()
                } catch (error: SSLException) {
                    if (AirAdbConnectionFailurePolicy.isExplicitTlsAuthorizationRejection(error)) {
                        throw AirAdbAuthorizationException(
                            "Wireless debugging rejected the paired ADB certificate",
                            error
                        )
                    }
                    throw error
                }
            }
            A_AUTH -> {
                message = authenticate(message)
            }
        }
        if (message.command != A_CNXN) {
            if (message.command == A_AUTH) {
                throw AirAdbAuthorizationException("Wireless debugging rejected the paired ADB key")
            }
            throw IllegalStateException("ADB connection failed: command=${message.command}")
        }
        tcpSocket.soTimeout = READ_TIMEOUT_MS
        connected = true
    }

    fun shell(command: String): AdbShellResult = decodeAdbShellV2(requestService("shell,v2,raw:$command"))

    fun restartUsb(): String = requestService("usb:").toString(Charsets.UTF_8)

    private fun requestService(destination: String): ByteArray = synchronized(shellLock) {
        connect()
        val localId = nextLocalId.getAndIncrement()
        val output = ByteArrayOutputStream()
        writeMessage(A_OPEN, localId, 0, destination.adbPayload())
        var accepted = false

        while (true) {
            val message = readMessage()
            // In the ADB protocol every server → client packet carries arg1 = the client's local-id.
            // A message for another stream is a leftover A_WRTE / A_CLSE from an earlier shell() still in the socket buffer
            // (typically dumpsys output leaking into screencap+base64).
            // Skip it: keep it out of the output, and send no ACK, since that stream on the server is long closed.
            if (message.arg1 != localId) continue
            when (message.command) {
                A_OKAY -> accepted = true
                A_WRTE -> {
                    output.write(message.payload)
                    writeMessage(A_OKAY, localId, message.arg0, null)
                }
                A_CLSE -> {
                    check(accepted) { "ADB service refused: ${destination.substringBefore(':')}" }
                    writeMessage(A_CLSE, localId, message.arg0, null)
                    return output.toByteArray()
                }
                else -> throw IllegalStateException("Unexpected ADB shell packet: command=${message.command}")
            }
        }
        @Suppress("UNREACHABLE_CODE") byteArrayOf()
    }

    private fun authenticate(firstAuth: AdbMessage): AdbMessage {
        if (firstAuth.arg0 != ADB_AUTH_TOKEN) {
            throw IllegalStateException("Unsupported ADB auth type: ${firstAuth.arg0}")
        }
        writeMessage(A_AUTH, ADB_AUTH_SIGNATURE, 0, adbKey.signToken(firstAuth.payload))
        var message = readMessage()
        if (message.command == A_AUTH && allowUserAuthorization) {
            // A TLS-paired key may still need one explicit approval for the classic TCP transport.
            writeMessage(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, adbKey.publicKeyBytes)
            if (legacyOnly) socket.soTimeout = 25_000
            message = try {
                readMessage()
            } catch (error: java.io.IOException) {
                if (!legacyOnly) throw error
                throw AirAdbAuthorizationException("ADB authorization did not complete", error)
            }
        }
        if (message.command == A_AUTH) {
            throw AirAdbAuthorizationException("ADB rejected the key or is awaiting user authorization")
        }
        return message
    }

    private fun writeMessage(command: Int, arg0: Int, arg1: Int, payload: ByteArray?) {
        val bytes = payload ?: ByteArray(0)
        val header = ByteBuffer.allocate(ADB_HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(arg0)
            .putInt(arg1)
            .putInt(bytes.size)
            .putInt(bytes.checksum())
            .putInt(command xor -1)
            .array()
        outputStream.write(header)
        if (bytes.isNotEmpty()) outputStream.write(bytes)
        outputStream.flush()
    }

    private fun readMessage(): AdbMessage {
        val header = ByteArray(ADB_HEADER_LENGTH)
        inputStream.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val payloadLength = buffer.int
        val checksum = buffer.int
        val magic = buffer.int
        if (magic != (command xor -1)) {
            throw IllegalStateException("Invalid ADB packet magic")
        }
        if (payloadLength < 0 || payloadLength > A_MAXDATA) {
            throw IllegalStateException("Invalid ADB payload length: $payloadLength")
        }
        val payload = ByteArray(payloadLength)
        if (payloadLength > 0) inputStream.readFully(payload)
        if (payload.checksum() != checksum) {
            throw IllegalStateException("Invalid ADB payload checksum")
        }
        return AdbMessage(command, arg0, arg1, payload)
    }

    internal fun abort() {
        connected = false
        // Unblock any in-flight read or handshake before closing the TLS wrapper.
        runCatching { socket.close() }
    }

    override fun close() {
        abort()
        runCatching { tlsSocket?.close() }
        tlsSocket = null
    }

    private data class AdbMessage(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray
    )

    private companion object {
        const val A_CNXN = 0x4e584e43
        const val A_AUTH = 0x48545541
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_CLSE = 0x45534c43
        const val A_WRTE = 0x45545257
        const val A_STLS = 0x534c5453
        const val A_VERSION = 0x01000000
        const val A_MAXDATA = 1024 * 1024
        const val ADB_HEADER_LENGTH = 24
        const val ADB_AUTH_TOKEN = 1
        const val ADB_AUTH_SIGNATURE = 2
        const val ADB_AUTH_RSAPUBLICKEY = 3
        const val CONNECT_TIMEOUT_MS = 5_000
        // The hard cap on a single socket read. A normal screencap's first byte takes ~1-2s and a health check is near instant;
        // 10s covers every legitimate read while bounding a half-dead connection to seconds instead of minutes.
        // Hot paths such as screenshots and injection have tighter wrapper timeouts (8s/15s); this is the floor for every read path.
        const val READ_TIMEOUT_MS = 10_000

        fun String.adbPayload(): ByteArray = toByteArray(Charsets.UTF_8) + 0.toByte()

        fun ByteArray.checksum(): Int {
            var sum = 0
            forEach { sum += it.toInt() and 0xff }
            return sum
        }
    }
}

/** Output and exit code from a completed ADB shell command. */
data class AdbShellResult(val output: String, val exitCode: Int) {
    internal val uid: Int? get() = output.trim().toIntOrNull()?.takeIf { exitCode == 0 && it >= 0 }

    internal companion object {
        const val ANDROID_SHELL_UID = 2000
    }
}

internal fun decodeAdbShellV2(bytes: ByteArray): AdbShellResult {
    val frames = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val output = ByteArrayOutputStream()
    while (frames.hasRemaining()) {
        check(frames.remaining() >= 5) { "Truncated ADB shell v2 header" }
        val stream = frames.get().toInt() and 255
        val length = frames.int
        check(length in 0..frames.remaining()) { "Invalid ADB shell v2 payload length" }
        when (stream) {
            1, 2 -> {
                output.write(bytes, frames.position(), length)
                frames.position(frames.position() + length)
            }
            3 -> {
                check(length == 1 && frames.remaining() == 1) { "Invalid ADB shell v2 exit frame" }
                return AdbShellResult(output.toString(Charsets.UTF_8.name()), frames.get().toInt() and 255)
            }
            else -> error("Unexpected ADB shell v2 stream: $stream")
        }
    }
    error("Missing ADB shell v2 exit frame")
}
