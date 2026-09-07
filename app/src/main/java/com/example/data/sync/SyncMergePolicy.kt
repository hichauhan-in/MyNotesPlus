package com.example.data.sync

internal enum class MergeAction { UNCHANGED, PUSH, PULL, KEEP_BOTH, TRASH_LOCAL, DELETE_REMOTE }

internal object SyncMergePolicy {
    fun decide(local: Long?, remote: Long?, base: Long?): MergeAction = when {
        local == null && remote == null -> MergeAction.UNCHANGED
        local == null -> if (base != null && remote == base) MergeAction.DELETE_REMOTE else MergeAction.PULL
        remote == null -> if (base != null && local == base) MergeAction.TRASH_LOCAL else MergeAction.PUSH
        local == remote -> MergeAction.UNCHANGED
        base == null -> MergeAction.KEEP_BOTH
        local == base -> MergeAction.PULL
        remote == base -> MergeAction.PUSH
        else -> MergeAction.KEEP_BOTH
    }
}