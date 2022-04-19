package at.ac.oeaw.acdh.dylensauto.entity.dto

data class MouseClick(
    val id: String,
    val x: Double,
    val y: Double,
    val timestamp: Long
) : Message()