package at.ac.oeaw.acdh.dylensauto.entity.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class Resize(
    @JsonProperty("paneId") val paneId: String,
) : Message()