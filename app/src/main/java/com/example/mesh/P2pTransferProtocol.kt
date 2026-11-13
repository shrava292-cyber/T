package com.example.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class NoteTransferHeader(
    val noteId: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String?,
    val durationMs: Long,
    val totalBytes: Long,
    val totalChunks: Int,
    val chunkSize: Int,
    val hopCount: Int,
    val maxHops: Int,
    val waveformSamples: String,
    val sha256Checksum: String,
    val seenNodeIds: List<String>
)

object MeshCrypto {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private val secureRandom = SecureRandom()

    fun deriveMeshSessionKey(meshDomainSecret: String = "LocalMeshAudioNotes-v1-P2P-Key"): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(meshDomainSecret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainBytes: ByteArray, key: SecretKey): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(plainBytes)
        return iv to cipherText
    }

    fun decrypt(iv: ByteArray, cipherText: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read = input.read(buf)
            while (read > 0) {
                digest.update(buf, 0, read)
                read = input.read(buf)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Implements a secure, chunked binary frame protocol over TCP local sockets (Wi-Fi Direct / LAN).
 *
 * Frame layout:
 * [MAGIC: Int 4B = 0x4D455348][VERSION: Byte 1B][FRAME_TYPE: Byte 1B]
 * [IV_LEN: Int 4B][IV: N bytes][CIPHER_LEN: Int 4B][AES-256-GCM CIPHERTEXT]
 */
class P2pTransferProtocol(
    private val scope: CoroutineScope,
    private val tempFileProvider: (noteId: String) -> File,
    private val onNoteReceived: suspend (header: NoteTransferHeader, assembledFile: File, remoteAddress: String) -> Unit,
    private val onProgress: (TransferProgress) -> Unit,
    private val onLog: (tag: String, message: String, level: LogLevel) -> Unit
) {
    companion object {
        const val DEFAULT_PORT = 48912
        const val CHUNK_SIZE_BYTES = 16 * 1024 // 16 KB chunks for reliable P2P streaming
        private const val MAGIC = 0x4D455348 // "MESH"
        private const val VERSION: Byte = 1

        private const val FRAME_META: Byte = 0x01
        private const val FRAME_CHUNK: Byte = 0x02
        private const val FRAME_ACK: Byte = 0x03
        private const val FRAME_DONE: Byte = 0x04
        private const val SOCKET_TIMEOUT_MS = 15_000
    }

    private val sessionKey: SecretKey = MeshCrypto.deriveMeshSessionKey()
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    var activeListeningPort: Int = DEFAULT_PORT
        private set

    fun startServer(preferredPort: Int = DEFAULT_PORT): Int {
        if (serverJob?.isActive == true && serverSocket?.isClosed == false) {
            return activeListeningPort
        }

        val boundSocket = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(preferredPort))
            }
        }.getOrElse {
            ServerSocket(0)
        }

        serverSocket = boundSocket
        activeListeningPort = boundSocket.localPort
        onLog("SOCKET", "Encrypted chunk server listening on TCP :$activeListeningPort", LogLevel.INFO)

        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive && !boundSocket.isClosed) {
                val client = runCatching { boundSocket.accept() }.getOrNull() ?: break
                launch {
                    handleIncomingClient(client)
                }
            }
        }
        return activeListeningPort
    }

    fun stopServer() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    suspend fun sendNote(
        hostAddress: String,
        port: Int,
        peerName: String,
        header: NoteTransferHeader,
        audioFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Audio file does not exist"))
        }

        var socket: Socket? = null
        try {
            socket = Socket().apply {
                soTimeout = SOCKET_TIMEOUT_MS
                connect(InetSocketAddress(hostAddress, port), 8_000)
            }

            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

            // 1. Send encrypted metadata frame
            val metaBytes = encodeHeader(header)
            writeEncryptedFrame(output, FRAME_META, metaBytes)
            output.flush()

            val ackMeta = readAck(input)
            if (!ackMeta) {
                throw IllegalStateException("Peer rejected transfer metadata")
            }

            // 2. Stream encrypted 16KB chunks
            val buffer = ByteArray(header.chunkSize)
            var chunkIndex = 0
            var bytesSent = 0L

            FileInputStream(audioFile).use { fis ->
                while (true) {
                    val read = fis.read(buffer)
                    if (read <= 0) break

                    val chunkSlice = if (read == buffer.size) buffer else buffer.copyOf(read)
                    val chunkPayload = encodeChunkPayload(chunkIndex, chunkSlice)
                    writeEncryptedFrame(output, FRAME_CHUNK, chunkPayload)
                    output.flush()

                    if (!readAck(input)) {
                        throw IllegalStateException("Chunk $chunkIndex ACK failed")
                    }

                    chunkIndex++
                    bytesSent += read
                    onProgress(
                        TransferProgress(
                            noteId = header.noteId,
                            peerName = peerName,
                            bytesTransferred = bytesSent,
                            totalBytes = header.totalBytes,
                            chunksCompleted = chunkIndex,
                            totalChunks = header.totalChunks,
                            direction = TransferDirection.OUTBOUND
                        )
                    )
                }
            }

            // 3. Finalize frame
            writeEncryptedFrame(output, FRAME_DONE, header.noteId.toByteArray(Charsets.UTF_8))
            output.flush()
            readAck(input)

            onProgress(
                TransferProgress(
                    noteId = header.noteId,
                    peerName = peerName,
                    bytesTransferred = header.totalBytes,
                    totalBytes = header.totalBytes,
                    chunksCompleted = header.totalChunks,
                    totalChunks = header.totalChunks,
                    direction = TransferDirection.OUTBOUND,
                    isFinished = true
                )
            )
            onLog(
                "TX",
                "Sent ${header.totalChunks} encrypted chunks (${header.totalBytes / 1024} KB) to $peerName",
                LogLevel.SUCCESS
            )
            Result.success(Unit)
        } catch (e: Exception) {
            onProgress(
                TransferProgress(
                    noteId = header.noteId,
                    peerName = peerName,
                    bytesTransferred = 0L,
                    totalBytes = header.totalBytes,
                    chunksCompleted = 0,
                    totalChunks = header.totalChunks,
                    direction = TransferDirection.OUTBOUND,
                    isFinished = true,
                    error = e.localizedMessage ?: "Transfer failed"
                )
            )
            onLog("TX", "Failed transfer to $peerName: ${e.message}", LogLevel.WARN)
            Result.failure(e)
        } finally {
            runCatching { socket?.close() }
        }
    }

    private suspend fun handleIncomingClient(socket: Socket) {
        val remoteIp = socket.inetAddress?.hostAddress ?: "unknown"
        var tempPartFile: File? = null
        try {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            // 1. Read META frame
            val (metaType, metaPlain) = readEncryptedFrame(input)
            if (metaType != FRAME_META) {
                throw IllegalStateException("Expected FRAME_META, got $metaType")
            }
            val header = decodeHeader(metaPlain)
            tempPartFile = File(tempFileProvider(header.noteId).absolutePath + ".part")
            tempPartFile.parentFile?.mkdirs()

            writeAck(output, ok = true)
            output.flush()

            var bytesReceived = 0L
            var chunksReceived = 0

            FileOutputStream(tempPartFile).use { fos ->
                while (true) {
                    val (frameType, plainPayload) = readEncryptedFrame(input)
                    when (frameType) {
                        FRAME_CHUNK -> {
                            val (idx, chunkBytes) = decodeChunkPayload(plainPayload)
                            if (idx != chunksReceived) {
                                throw IllegalStateException("Out-of-order chunk $idx (expected $chunksReceived)")
                            }
                            fos.write(chunkBytes)
                            chunksReceived++
                            bytesReceived += chunkBytes.size

                            writeAck(output, ok = true)
                            output.flush()

                            onProgress(
                                TransferProgress(
                                    noteId = header.noteId,
                                    peerName = header.senderName,
                                    bytesTransferred = bytesReceived,
                                    totalBytes = header.totalBytes,
                                    chunksCompleted = chunksReceived,
                                    totalChunks = header.totalChunks,
                                    direction = TransferDirection.INBOUND
                                )
                            )
                        }
                        FRAME_DONE -> {
                            break
                        }
                        else -> throw IllegalStateException("Unexpected frame type: $frameType")
                    }
                }
                fos.fd.sync()
            }

            // Verify SHA-256 integrity
            val actualDigest = MeshCrypto.sha256Hex(tempPartFile)
            if (!actualDigest.equals(header.sha256Checksum, ignoreCase = true)) {
                tempPartFile.delete()
                writeAck(output, ok = false)
                output.flush()
                throw IllegalStateException("SHA-256 mismatch on received audio note")
            }

            val finalFile = tempFileProvider(header.noteId)
            if (finalFile.exists()) finalFile.delete()
            if (!tempPartFile.renameTo(finalFile)) {
                tempPartFile.copyTo(finalFile, overwrite = true)
                tempPartFile.delete()
            }

            writeAck(output, ok = true)
            output.flush()

            onProgress(
                TransferProgress(
                    noteId = header.noteId,
                    peerName = header.senderName,
                    bytesTransferred = header.totalBytes,
                    totalBytes = header.totalBytes,
                    chunksCompleted = header.totalChunks,
                    totalChunks = header.totalChunks,
                    direction = TransferDirection.INBOUND,
                    isFinished = true
                )
            )
            onLog(
                "RX",
                "Verified SHA-256 & received ${chunksReceived} chunks from ${header.senderName} ($remoteIp)",
                LogLevel.SUCCESS
            )
            onNoteReceived(header, finalFile, remoteIp)
        } catch (e: Exception) {
            tempPartFile?.delete()
            onLog("RX", "Incoming transfer aborted ($remoteIp): ${e.message}", LogLevel.WARN)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun writeEncryptedFrame(output: DataOutputStream, frameType: Byte, plainBytes: ByteArray) {
        val (iv, cipherText) = MeshCrypto.encrypt(plainBytes, sessionKey)
        output.writeInt(MAGIC)
        output.writeByte(VERSION.toInt())
        output.writeByte(frameType.toInt())
        output.writeInt(iv.size)
        output.write(iv)
        output.writeInt(cipherText.size)
        output.write(cipherText)
    }

    private fun readEncryptedFrame(input: DataInputStream): Pair<Byte, ByteArray> {
        val magic = input.readInt()
        if (magic != MAGIC) throw IllegalStateException("Invalid mesh frame magic: $magic")
        val version = input.readByte()
        if (version != VERSION) throw IllegalStateException("Unsupported protocol version: $version")
        val frameType = input.readByte()
        val ivLen = input.readInt()
        if (ivLen !in 12..32) throw IllegalStateException("Invalid IV length: $ivLen")
        val iv = ByteArray(ivLen).also { input.readFully(it) }
        val cipherLen = input.readInt()
        if (cipherLen <= 0 || cipherLen > 512 * 1024) {
            throw IllegalStateException("Invalid frame size: $cipherLen")
        }
        val cipherText = ByteArray(cipherLen).also { input.readFully(it) }
        val plain = MeshCrypto.decrypt(iv, cipherText, sessionKey)
        return frameType to plain
    }

    private fun writeAck(output: DataOutputStream, ok: Boolean) {
        output.writeByte(FRAME_ACK.toInt())
        output.writeBoolean(ok)
    }

    private fun readAck(input: DataInputStream): Boolean {
        val type = input.readByte()
        if (type != FRAME_ACK) return false
        return input.readBoolean()
    }

    private fun encodeChunkPayload(index: Int, chunkBytes: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(4 + chunkBytes.size)
        DataOutputStream(out).use { dos ->
            dos.writeInt(index)
            dos.write(chunkBytes)
        }
        return out.toByteArray()
    }

    private fun decodeChunkPayload(bytes: ByteArray): Pair<Int, ByteArray> {
        DataInputStream(bytes.inputStream()).use { dis ->
            val index = dis.readInt()
            val payload = ByteArray(bytes.size - 4)
            dis.readFully(payload)
            return index to payload
        }
    }

    private fun encodeHeader(h: NoteTransferHeader): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            dos.writeUTF(h.noteId)
            dos.writeUTF(h.senderId)
            dos.writeUTF(h.senderName)
            dos.writeUTF(h.recipientId ?: "")
            dos.writeLong(h.durationMs)
            dos.writeLong(h.totalBytes)
            dos.writeInt(h.totalChunks)
            dos.writeInt(h.chunkSize)
            dos.writeInt(h.hopCount)
            dos.writeInt(h.maxHops)
            dos.writeUTF(h.waveformSamples)
            dos.writeUTF(h.sha256Checksum)
            dos.writeUTF(h.seenNodeIds.joinToString(","))
        }
        return out.toByteArray()
    }

    private fun decodeHeader(bytes: ByteArray): NoteTransferHeader {
        DataInputStream(bytes.inputStream()).use { dis ->
            val noteId = dis.readUTF()
            val senderId = dis.readUTF()
            val senderName = dis.readUTF()
            val recipientRaw = dis.readUTF()
            val durationMs = dis.readLong()
            val totalBytes = dis.readLong()
            val totalChunks = dis.readInt()
            val chunkSize = dis.readInt()
            val hopCount = dis.readInt()
            val maxHops = dis.readInt()
            val waveformSamples = dis.readUTF()
            val sha256Checksum = dis.readUTF()
            val seenRaw = dis.readUTF()
            return NoteTransferHeader(
                noteId = noteId,
                senderId = senderId,
                senderName = senderName,
                recipientId = recipientRaw.ifBlank { null },
                durationMs = durationMs,
                totalBytes = totalBytes,
                totalChunks = totalChunks,
                chunkSize = chunkSize,
                hopCount = hopCount,
                maxHops = maxHops,
                waveformSamples = waveformSamples,
                sha256Checksum = sha256Checksum,
                seenNodeIds = seenRaw.split(",").filter { it.isNotBlank() }
            )
        }
    }
}
