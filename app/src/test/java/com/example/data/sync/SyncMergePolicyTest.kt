package com.example.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMergePolicyTest {
    @Test fun overlappingEditsAndUnknownBasesPreserveBoth() {
        assertEquals(MergeAction.KEEP_BOTH, SyncMergePolicy.decide(200, 300, 100))
        assertEquals(MergeAction.KEEP_BOTH, SyncMergePolicy.decide(200, 300, null))
        assertEquals(MergeAction.KEEP_BOTH, SyncMergePolicy.decide(50, 300, 100))
    }

    @Test fun oneSidedEditsUseTheirActualSideNotTheNewestClock() {
        assertEquals(MergeAction.PUSH, SyncMergePolicy.decide(50, 100, 100))
        assertEquals(MergeAction.PULL, SyncMergePolicy.decide(100, 50, 100))
    }

    @Test fun deletionDoesNotDiscardAnUnsyncedEdit() {
        assertEquals(MergeAction.PUSH, SyncMergePolicy.decide(200, null, 100))
        assertEquals(MergeAction.PULL, SyncMergePolicy.decide(null, 200, 100))
        assertEquals(MergeAction.TRASH_LOCAL, SyncMergePolicy.decide(100, null, 100))
        assertEquals(MergeAction.DELETE_REMOTE, SyncMergePolicy.decide(null, 100, 100))
    }

    @Test fun unchangedAndNewRecordsAreHandledWithoutConflicts() {
        assertEquals(MergeAction.UNCHANGED, SyncMergePolicy.decide(100, 100, 100))
        assertEquals(MergeAction.PUSH, SyncMergePolicy.decide(100, null, null))
        assertEquals(MergeAction.PULL, SyncMergePolicy.decide(null, 100, null))
    }
}