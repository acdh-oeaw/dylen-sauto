package at.ac.oeaw.acdh.dylensauto.entity

import at.ac.oeaw.acdh.dylensauto.constants.Constants
import at.ac.oeaw.acdh.dylensauto.entity.dto.*
import at.ac.oeaw.acdh.dylensauto.exceptions.JSONException
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import java.util.*
import java.util.stream.Collectors

data class Session(
    @get:Id
    val id: String,
    val startTime: Date,
    var endTime: Date,
    var duration: Long,
    val mousePositions: ArrayList<MousePosition>,
    val visitedMouseOvers: ArrayList<MouseOver>,
    val mouseClicks: ArrayList<MouseClick>,
    val drags: ArrayList<Drag>,
    val keyPresses: ArrayList<KeyPress>,
    val resizes: ArrayList<Resize>?,
    val timeouts: ArrayList<Timeout>?,
    //this is a list of every scroll event.
    //you need to calculate how much scroll there really is
    //hint: add positives until negative(or end) is reached = scrolled down by that much
    //hint: add negatives until positive(or end) is reached = scrolled up by that much
    val scrolls: ArrayList<Scroll>,
    var appVersion: Double = 1.0,
    val completionEvents: HashMap<String, ArrayList<MouseClick>>,
    var code: Int?
) {
    @Transient
    private val messageTypeToListMap = mapOf(
        MousePosition::class to mousePositions,
        MouseOver::class to visitedMouseOvers,
        MouseClick::class to mouseClicks,
        Scroll::class to scrolls,
        Drag::class to drags,
        KeyPress::class to keyPresses,
        Resize::class to resizes,
        Timeout::class to timeouts
    )

    fun toReadableString(): String {
        return toString().split("],").stream().map { r -> r.toString() }
            .collect(Collectors.joining("],\n"))
    }

    fun <T : Message> add(message: T) {
        if (message::class == VersionInfo::class) {
            appVersion = (message as VersionInfo).appVersion
        } else {
            //This is not used anymore
            //but i will capture it here anyways
            if (message::class == MouseClick::class) {
                val click = message as MouseClick
                for (id in Constants.completionIds(appVersion.toString())!!) {
                    if (click.id.contains(id)) {
                        var completionEventList = completionEvents[id]
                        if (completionEventList == null) {
                            completionEventList = arrayListOf(click)
                        } else {
                            completionEventList.add(click)
                        }
                        completionEvents[id] = completionEventList
                    }
                }
            }

            @Suppress("UNCHECKED_CAST")
            val list = (messageTypeToListMap.get(message::class) as? ArrayList<T>)
                ?: throw JSONException("json can't be deserialized, because it doesn't match any known classes.")

            list.add(message)
        }
    }
}