package at.ac.oeaw.acdh.dylensauto.constants

import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

object Constants {
    //examples:
    //table-item-{word}
    //year-slider-pane1-{year}
    //x-button-parallel-coordinates-{word}
    //pane1-node-{word}
    //table-sort-{metric}
    private val completionIds1: ArrayList<String> =
        arrayListOf(
            "queryButton-pane1",
            "queryButton-pane2",
            "settings-button",
            "table-button",
            "table-item",
            "year-slider-pane1",
            "year-slider-pane2",
            "x-button-parallel-coordinates",
            "pane1-node",
            "pane2-node",
            "table-sort"
        )

    //examples:
    //there are no different examples as i removed specific ids from the front end
    //so we dont track for example pane1-node-AHS and pane1-node-Leher separately anymore
    //they are all tracked as pane1-node
    private val completionIds2: ArrayList<String> =
        arrayListOf(
            "queryButton-pane1",
            "queryButton-pane2",
            "settings-button",
            "table-button",
            "table-item",
            "year-slider-pane1",
            "year-slider-pane2",
            "parallel-coordinates-x-button",
            "pane1-node",
            "pane2-node",
            "table-sort",
            "timeSeries-relative-option",
            "resetQueryButton-pane2"
        )

    private val completionIds3: ArrayList<String> =
        arrayListOf(
            "queryButton-Ego-pane1",
            "queryButton-Party-pane1",
            "queryButton-Speaker-pane1",
            "queryButton-Ego-pane2",
            "queryButton-Party-pane2",
            "queryButton-Speaker-pane2",
            "context-menu-select-as-targetword",
            "right-click-pane1-node",
            "right-click-pane2-node",
            "node-metrics-filter",
            "time-series-filter",
            "node-metrics-table-button",
            "time-series-table-button",
            "settings-button-nav",
            "settings-button-result",
            "table-item",
            "year-slider-pane1",
            "year-slider-pane2",
            "parallel-coordinates-x-button",
            "pane1-node",
            "pane2-node",
            "table-sort",
            "timeSeries-relative-option",
        )


    private val completionIdsToTasks1 = mapOf(
        "queryButton-pane1" to "Basic Word Search (Visualize a word)",
        "pane1-node" to "Visualization (Select a word in the graph)",
        "pane2-node" to "Visualization (Select a word in the graph)",
        "queryButton-pane2" to "Graph Comparison",
        "table-button" to "Table View Visualization",
        "table-item" to "Select Table Item",
        "year-slider-pane1" to "Visualize a different year",
        "year-slider-pane2" to "Visualize a different year",
        "table-sort" to "Sort Table",
        "x-button-parallel-coordinates" to "Remove line from Parallel Coordinates",//changed in new version
        "settings-button" to "Settings Button"
    )

    private val completionIdsToTasks2 = mapOf(
        "queryButton-pane1" to "Basic Word Search (Visualize a word)",
        "pane1-node" to "Visualization (Select a word in the graph)",
        "pane2-node" to "Visualization (Select a word in the graph)",
        "queryButton-pane2" to "Graph Comparison",
        "table-button" to "Table View Visualization",
        "table-item" to "Select Table Item",
        "year-slider-pane1" to "Visualize a different year",
        "year-slider-pane2" to "Visualize a different year",
        "table-sort" to "Sort Table",
        "parallel-coordinates-x-button" to "Remove line from Parallel Coordinates",
        "settings-button" to "Settings Button",
        "timeSeries-relative-option" to "Compare Time Series",
        "resetQueryButton-pane2" to "Reset query for network 2"
    )

    private val completionIdsToTasks3 = mapOf(
        "queryButton-Ego-pane1" to "Basic Ego Network Word Search (Visualize a word)",
        "queryButton-Party-pane1" to "Basic General Network Party Search (Visualize a party)",
        "queryButton-Speaker-pane1" to "Basic General Network Speaker Search (Visualize a speaker)",
        "queryButton-Ego-pane2" to "Ego Network Word Graph Comparison",
        "queryButton-Party-pane2" to "General Network Party Graph Comparison",
        "queryButton-Speaker-pane2" to "General Network Speaker Graph Comparison",
        "context-menu-select-as-targetword" to "Select word from node context menu",
        "right-click-pane1-node" to "View node specific stats (open Context Menu of node)",
        "right-click-pane2-node" to "View node specific stats (open Context Menu of node)",
        "node-metrics-table-button" to "View Node metrics Table Visualization",
        "time-series-table-button" to "View Time series Table Visualization",
        "node-metrics-filter" to "Use filter on node metrics table",
        "time-series-filter" to "Use filter on time series table",
        "pane1-node" to "Visualization (Select a word in the graph)",
        "pane2-node" to "Visualization (Select a word in the graph)",
        "table-item" to "Select Table Item",
        "year-slider-pane1" to "Visualize a different year",
        "year-slider-pane2" to "Visualize a different year",
        "table-sort" to "Sort Table",
        "parallel-coordinates-x-button" to "Remove line from Parallel Coordinates",
        "settings-button-nav" to "Settings Button",
        "settings-button-result" to "Settings Button",
        "timeSeries-relative-option" to "Compare Time Series",
    )

    //i realize initializing all this below with a class once and not in static a constant would have be smarter but whats done is done
    //even if its a 5 minute fix, i wont do it. dont touch unbroken code
    fun completionIds(appVersion: String): ArrayList<String>? {
        return when (appVersion) {
            "1.0" -> completionIds1
            "2.0" -> completionIds2
            "3.0" -> completionIds3
            else -> null
        }
    }

    fun completionIdsToTasks(appVersion: String): Map<String, String>? {
        return when (appVersion) {
            "1.0" -> completionIdsToTasks1
            "2.0" -> completionIdsToTasks2
            "3.0" -> completionIdsToTasks3
            else -> null
        }
    }

    fun uniqueCompletionEventCount(appVersion: String): Int? {
        return when (appVersion) {
            "1.0" -> completionIdsToTasks1.values.distinct().size
            "2.0" -> completionIdsToTasks2.values.distinct().size
            "3.0" -> completionIdsToTasks3.values.distinct().size
            else -> null
        }
    }

    fun getTestEndTimeForVersion(appVersion: String): Date? {
        return when (appVersion) {
            "1.0" -> SimpleDateFormat("yyyy-MM-dd").parse("2021-10-12")
            "2.0" -> SimpleDateFormat("yyyy-MM-dd").parse("2021-12-04")
            "3.0" -> SimpleDateFormat("yyyy-MM-dd").parse("2022-12-31")
            else -> null
        }
    }
}