package com.example.domain.model

internal class EditHistory<Value>(private val limit: Int = 30) {
    private val previous = ArrayDeque<Value>()
    private val following = ArrayDeque<Value>()
    private var lastGroup: String? = null
    private var lastChange = Long.MIN_VALUE
    val canUndo: Boolean get() = previous.isNotEmpty()
    val canRedo: Boolean get() = following.isNotEmpty()

    fun record(value: Value, group: String? = null, now: Long = System.currentTimeMillis()) {
        val grouped = group != null && group == lastGroup && now >= lastChange && now - lastChange < 700
        if (!grouped) {
            previous.addLast(value)
            while (previous.size > limit) previous.removeFirst()
        }
        following.clear()
        lastGroup = group
        lastChange = now
    }

    fun undo(current: Value): Value? {
        if (previous.isEmpty()) return null
        following.addLast(current)
        lastGroup = null
        return previous.removeLast()
    }

    fun redo(current: Value): Value? {
        if (following.isEmpty()) return null
        previous.addLast(current)
        lastGroup = null
        return following.removeLast()
    }
}