package at.ac.oeaw.acdh.dylensauto.entity.dto

data class KeyPress(
    val id: String,
    val key: String,
    val charCode: Int,
    val timestamp: Long
) : Message()