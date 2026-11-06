package com.nothing.glyphbattery.service

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Small local-only event history, independent of logcat and never containing SDK keys. */
class RuntimeJournal(private val file: File, private val maxLines: Int = 200) {
    init { require(maxLines > 0) }

    companion object { private val lock = Any() }

    fun read(): List<String> = synchronized(lock) {
        if (file.isFile) file.readLines().takeLast(maxLines) else emptyList()
    }

    fun append(event: String): Unit = synchronized(lock) {
        val lines = (read() + event.replace('\n', ' ').replace('\r', ' ').take(512)).takeLast(maxLines)
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        temporary.writeText(lines.joinToString("\n", postfix = "\n"))
        try {
            Files.move(temporary.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
