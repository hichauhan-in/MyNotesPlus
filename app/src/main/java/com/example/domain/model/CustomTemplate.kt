package com.example.domain.model

import java.util.UUID

/**
 * A user-defined draft that appears on the templates button in the bottom-left corner.
 * Selecting it opens a new note pre-filled with [content]. [iconKey] maps to an icon in the UI layer.
 */
data class CustomTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconKey: String,
    val content: String,
    /** When non-null, the template is in Trash and this is when it was deleted (epoch millis). */
    val trashedAt: Long? = null,
    /** Last time this template was created/edited/trashed (epoch millis) - used to merge on sync. */
    val updatedAt: Long = 0L,
)
