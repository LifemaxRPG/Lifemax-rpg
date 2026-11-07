package com.lifemax.rpg.feature.lifedirector.data.timer

import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerEngine as E
import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerRepository as R
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.*
import java.util.zip.CRC32

/** Single-owner journal, API 26+ filesystem primitives. No production caller until cutover. */
class FileTimerJournal(private val file: Path) : R.Journal, AutoCloseable {
    private val directory = requireNotNull(file.toAbsolutePath().parent)
    private val lockChannel: FileChannel
    private val lock: java.nio.channels.FileLock

    init {
        Files.createDirectories(directory)
        lockChannel =
            FileChannel.open(directory.resolve(file.fileName.toString() + ".lock"), CREATE, WRITE)
        try {
            lock = checkNotNull(lockChannel.tryLock()) { "Timer journal already open" }
        } catch (e: Exception) {
            lockChannel.close()
            throw e
        }
    }

    override fun read(): R.Snapshot? {
        check(lock.isValid)
        val length =
            try {
                Files.size(file)
            } catch (_: java.nio.file.NoSuchFileException) {
                return null
            }
        require(length <= MAX_BYTES) { "Timer journal too large" }
        val input = DataInputStream(ByteArrayInputStream(Files.readAllBytes(file)))
        require(input.readInt() == MAGIC && input.readInt() == 1) { "Unsupported timer journal" }
        val checksum = input.readLong()
        val bytes = input.readBytes()
        require(CRC32().apply { update(bytes) }.value == checksum) {
            "Timer journal checksum mismatch"
        }
        val data = DataInputStream(ByteArrayInputStream(bytes))
        val epoch = data.readUTF()
        val sequence = data.readLong()
        val generation = data.readLong()
        val timer =
            if (data.readBoolean())
                E.Timer(
                    data.readUTF(),
                    data.readLong(),
                    data.readLong(),
                    if (data.readBoolean()) data.readLong() else null,
                    data.readLong(),
                )
            else null
        val outbox =
            List(data.count(R.OUTBOX_LIMIT)) {
                R.Pending(
                    data.readUTF(),
                    data.readUTF(),
                    E.Completion(data.key(), data.readLong(), data.readLong()),
                )
            }
        val receipts =
            List(data.count(R.RECEIPT_LIMIT)) {
                val request =
                    R.Request(data.readUTF(), data.readLong(), data.readLong(), data.command())
                R.Receipt(request, E.Status.valueOf(data.readUTF()))
            }
        require(data.available() == 0) { "Unexpected timer journal fields" }
        return R.Snapshot(epoch, sequence, generation, timer, outbox, receipts)
    }

    override fun write(snapshot: R.Snapshot) {
        check(lock.isValid)
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { out ->
            out.writeUTF(snapshot.epoch)
            out.writeLong(snapshot.lastSequence)
            out.writeLong(snapshot.lastGeneration)
            out.writeBoolean(snapshot.timer != null)
            snapshot.timer?.let {
                out.writeUTF(it.activityId)
                out.writeLong(it.generation)
                out.writeLong(it.plannedMs)
                out.writeBoolean(it.pausedAt != null)
                it.pausedAt?.let(out::writeLong)
                out.writeLong(it.pausedMs)
            }
            out.writeInt(snapshot.outbox.size)
            snapshot.outbox.forEach {
                out.writeUTF(it.id)
                out.writeUTF(it.epoch)
                out.key(it.completion.timer)
                out.writeLong(it.completion.actualStartEpoch)
                out.writeLong(it.completion.minutes)
            }
            out.writeInt(snapshot.receipts.size)
            snapshot.receipts.forEach {
                out.writeUTF(it.request.epoch)
                out.writeLong(it.request.sequence)
                out.writeLong(it.request.atMs)
                out.command(it.request.command)
                out.writeUTF(it.status.name)
            }
        }
        val bytes = payload.toByteArray()
        val encoded = ByteArrayOutputStream()
        DataOutputStream(encoded).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(1)
            out.writeLong(CRC32().apply { update(bytes) }.value)
            out.write(bytes)
        }
        require(encoded.size() <= MAX_BYTES) { "Timer journal too large" }
        val temporary = Files.createTempFile(directory, "timer-", ".pending")
        try {
            FileChannel.open(temporary, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(encoded.toByteArray())
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            // No non-atomic fallback: unsupported filesystems fail closed.
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
            FileChannel.open(directory, READ).use { it.force(true) }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    override fun close() {
        try {
            lock.release()
        } finally {
            lockChannel.close()
        }
    }

    private fun DataInputStream.count(max: Int): Int = readInt().also { require(it in 0..max) }

    private fun DataInputStream.key() = E.Key(readUTF(), readLong())

    private fun DataOutputStream.key(key: E.Key) {
        writeUTF(key.activityId)
        writeLong(key.generation)
    }

    private fun DataInputStream.target() = E.Target(readUTF(), readLong(), readBoolean())

    private fun DataOutputStream.target(target: E.Target) {
        writeUTF(target.activityId)
        writeLong(target.plannedMs)
        writeBoolean(target.available)
    }

    private fun DataInputStream.command(): E.Command =
        when (readUTF()) {
            "start" -> E.Command.Start(target())
            "replace" -> E.Command.Replace(key(), target())
            "pause" -> E.Command.Pause(key())
            "resume" -> E.Command.Resume(key())
            "reset" -> E.Command.Reset(key())
            "stop" -> E.Command.Stop(key())
            "finish" -> E.Command.Finish(key(), readBoolean())
            else -> error("Unknown timer command")
        }

    private fun DataOutputStream.command(command: E.Command) {
        when (command) {
            is E.Command.Start -> {
                writeUTF("start")
                target(command.target)
            }
            is E.Command.Replace -> {
                writeUTF("replace")
                key(command.expected)
                target(command.target)
            }
            is E.Command.Pause -> {
                writeUTF("pause")
                key(command.expected)
            }
            is E.Command.Resume -> {
                writeUTF("resume")
                key(command.expected)
            }
            is E.Command.Reset -> {
                writeUTF("reset")
                key(command.expected)
            }
            is E.Command.Stop -> {
                writeUTF("stop")
                key(command.expected)
            }
            is E.Command.Finish -> {
                writeUTF("finish")
                key(command.expected)
                writeBoolean(command.automatic)
            }
        }
    }

    companion object {
        private const val MAGIC = 0x4c4d5452
        private const val MAX_BYTES = 1_048_576
    }
}
