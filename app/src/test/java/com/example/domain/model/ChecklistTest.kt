package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTest {
    @Test fun reorderingPreservesTextAndCheckedState() {
        val items = listOf(ChecklistItem("First", false), ChecklistItem("Second", true), ChecklistItem("Third", false))
        val moved = Checklist.move(items, 1, -1)
        assertEquals(listOf("Second", "First", "Third"), moved.map { it.text })
        assertTrue(moved.first().checked)
        assertEquals(moved, Checklist.parse(Checklist.serialize(moved)))
        assertEquals(items.last(), Checklist.move(items, 2, -100).first())
    }

    @Test fun linkedRemindersNeverChooseBetweenDuplicateTaskNames() {
        assertThrows(IllegalArgumentException::class.java) { Checklist.completeMatching("[ ] Bill\n[ ] Bill", "Bill") }
        assertThrows(IllegalArgumentException::class.java) { Checklist.completeMatching("[ ] Rent", "Bill") }
        assertEquals("[x] Bill\n[ ] Rent", Checklist.completeMatching("[ ] Bill\n[ ] Rent", "Bill"))
    }
}