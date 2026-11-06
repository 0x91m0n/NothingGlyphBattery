package com.nothing.glyphbattery.service

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RuntimeJournalTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `history survives a new service and retains the last events only`() {
        val file = File(temporary.root, "no-backup/runtime.log")
        val journal = RuntimeJournal(file, maxLines = 3)
        assertEquals(emptyList<String>(), journal.read())
        repeat(5) { journal.append("event $it") }
        val reopened = RuntimeJournal(file, maxLines = 3)
        assertEquals(listOf("event 2", "event 3", "event 4"), reopened.read())
        reopened.append("new process")
        assertEquals(listOf("event 3", "event 4", "new process"), journal.read())
    }

    @Test fun `a leftover incomplete temporary write never replaces committed history`() {
        val file = File(temporary.root, "runtime.log")
        RuntimeJournal(file).append("before failure")
        File(file.path + ".tmp").writeText("incomplete")
        val reopened = RuntimeJournal(file)
        assertEquals(listOf("before failure"), reopened.read())
        reopened.append("after restart")
        assertEquals(listOf("before failure", "after restart"), reopened.read())
    }

    @Test fun `event size is bounded and multiline input cannot forge history entries`() {
        val journal = RuntimeJournal(File(temporary.root, "runtime.log"))
        journal.append("one\ntwo\r" + "x".repeat(1000))
        assertEquals(1, journal.read().size)
        assertEquals(512, journal.read().single().length)
        assertTrue(journal.read().single().startsWith("one two "))
    }
}
