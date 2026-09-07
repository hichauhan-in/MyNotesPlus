package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EditHistoryTest {
    @Test fun typingIsGroupedButSeparateActionsAreNot() {
        val history = EditHistory<String>()
        history.record("", "typing", 100)
        history.record("h", "typing", 200)
        history.record("hi", null, 300)
        assertEquals("hi", history.undo("hi image"))
        assertEquals("", history.undo("hi"))
        assertEquals("hi", history.redo(""))
        assertEquals("hi image", history.redo("hi"))
    }

    @Test fun newChangesAfterUndoDiscardRedo() {
        val history = EditHistory<String>()
        history.record("before")
        history.undo("after")
        history.record("before")
        assertFalse(history.canRedo)
        assertNull(history.redo("changed"))
    }

    @Test fun historyIsBounded() {
        val history = EditHistory<Int>(2)
        history.record(0)
        history.record(1)
        history.record(2)
        assertEquals(2, history.undo(3))
        assertEquals(1, history.undo(2))
        assertNull(history.undo(1))
    }
}