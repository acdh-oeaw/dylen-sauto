package at.ac.oeaw.acdh.dylensauto.entity.dto

data class Drag(
    val id: String,
    val start: DragStartEnd,
    val end: DragStartEnd,
) : Message() {
    data class DragStartEnd(
        val x: Double,
        val y: Double,
        val timestamp: Long
    )
}

