package at.ac.oeaw.acdh.dylensauto.entity.analysis

data class CompletionEvent(
    val id: String,
    val timestamp: Long,
    val duration: Double,
    val interactionCount: Int
)