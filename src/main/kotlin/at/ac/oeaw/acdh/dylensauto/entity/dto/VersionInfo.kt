package at.ac.oeaw.acdh.dylensauto.entity.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class VersionInfo(@JsonProperty("appVersion") val appVersion: Double) : Message()