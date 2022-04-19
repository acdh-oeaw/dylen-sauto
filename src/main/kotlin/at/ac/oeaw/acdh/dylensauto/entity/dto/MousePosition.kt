package at.ac.oeaw.acdh.dylensauto.entity.dto

data class MousePosition (
    val x: Double,
    val y: Double,
    val timestamp: Long
) : Message()