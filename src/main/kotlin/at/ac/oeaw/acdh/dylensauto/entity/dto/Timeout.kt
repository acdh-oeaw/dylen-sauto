package at.ac.oeaw.acdh.dylensauto.entity.dto

data class Timeout(
    val slidValMin: Double,
    val slidValMax: Double,
) : Message()
