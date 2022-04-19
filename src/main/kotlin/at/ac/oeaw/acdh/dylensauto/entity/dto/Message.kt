package at.ac.oeaw.acdh.dylensauto.entity.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

//General class to deserialize json payload
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = MouseClick::class, name = "MouseClick"),
    JsonSubTypes.Type(value = MousePosition::class, name = "MousePosition"),
    JsonSubTypes.Type(value = Scroll::class, name = "Scroll"),
    JsonSubTypes.Type(value = MouseOver::class, name = "MouseOver"),
    JsonSubTypes.Type(value = KeyPress::class, name = "KeyPress"),
    JsonSubTypes.Type(value = VersionInfo::class, name = "VersionInfo"),
    JsonSubTypes.Type(value = Drag::class, name = "Drag"),
    JsonSubTypes.Type(value = Resize::class, name = "Resize"),
    JsonSubTypes.Type(value = Timeout::class, name = "Timeout")
)
abstract class Message {
}