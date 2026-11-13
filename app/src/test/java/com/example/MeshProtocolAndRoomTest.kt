package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AudioNoteEntity
import com.example.data.local.MeshDatabase
import com.example.data.local.NoteDirection
import com.example.data.local.TransferStatus
import com.example.mesh.MeshCrypto
import com.example.mesh.NoteTransferHeader
import com.example.mesh.P2pTransferProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MeshProtocolAndRoomTest {

    @Test
    fun `room dao stores and updates unread audio notes`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MeshDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.audioNoteDao()

        val incomingNote = AudioNoteEntity(
            id = "note-001",
            senderId = "NODE-9A1B",
            senderName = "Scout Alpha",
            filePath = "/tmp/note_001.m4a",
            durationMs = 4200L,
            fileSizeBytes = 34000L,
            timestampMs = 1_700_000_000_000L,
            direction = NoteDirection.INCOMING,
            isRead = false,
            transferStatus = TransferStatus.RECEIVED,
            hopCount = 1,
            maxHops = 3,
            waveformSamples = "0.2,0.5,0.8,0.4",
            sha256Checksum = "abc123"
        )

        dao.insertNote(incomingNote)
        assertEquals(1, dao.getUnreadCount().first())

        dao.setReadStatus("note-001", isRead = true)
        assertEquals(0, dao.getUnreadCount().first())

        dao.deleteNoteById("note-001")
        assertTrue(dao.getAllNotes().first().isEmpty())
        db.close()
    }

    @Test
    fun `chunked encrypted p2p transfer verifies sha256 over loopback socket`(): Unit = runBlocking {
        val tempDir = createTempDirectory("mesh_test_").toFile()
        try {
            val sourceFile = File(tempDir, "source.m4a")
            // Create 40KB payload so it spans 3 x 16KB chunks
            val payloadBytes = ByteArray(40 * 1024) { idx -> (idx % 251).toByte() }
            sourceFile.writeBytes(payloadBytes)
            val expectedSha = MeshCrypto.sha256Hex(sourceFile)

            val receivedDir = File(tempDir, "rx").apply { mkdirs() }
            val latch = CountDownLatch(1)
            var receivedFile: File? = null
            var receivedHeader: NoteTransferHeader? = null

            val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val receiver = P2pTransferProtocol(
                scope = serverScope,
                tempFileProvider = { id -> File(receivedDir, "$id.m4a") },
                onNoteReceived = { header, file, _ ->
                    receivedHeader = header
                    receivedFile = file
                    latch.countDown()
                },
                onProgress = {},
                onLog = { _, _, _ -> }
            )
            val boundPort = receiver.startServer(0)

            val sender = P2pTransferProtocol(
                scope = serverScope,
                tempFileProvider = { id -> File(tempDir, "$id.m4a") },
                onNoteReceived = { _, _, _ -> },
                onProgress = {},
                onLog = { _, _, _ -> }
            )

            val totalChunks = ceil(sourceFile.length().toDouble() / P2pTransferProtocol.CHUNK_SIZE_BYTES).toInt()
            val header = NoteTransferHeader(
                noteId = "loopback-note-1",
                senderId = "NODE-TX",
                senderName = "SenderNode",
                recipientId = null,
                durationMs = 5000L,
                totalBytes = sourceFile.length(),
                totalChunks = totalChunks,
                chunkSize = P2pTransferProtocol.CHUNK_SIZE_BYTES,
                hopCount = 0,
                maxHops = 3,
                waveformSamples = "0.1,0.9,0.5",
                sha256Checksum = expectedSha,
                seenNodeIds = listOf("NODE-TX")
            )

            val result = sender.sendNote("127.0.0.1", boundPort, "LoopbackPeer", header, sourceFile)
            assertTrue(result.isSuccess)
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertEquals("loopback-note-1", receivedHeader?.noteId)
            assertArrayEquals(payloadBytes, receivedFile?.readBytes())

            receiver.stopServer()
            serverScope.cancel()
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
