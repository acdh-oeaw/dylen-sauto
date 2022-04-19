package at.ac.oeaw.acdh.dylensauto.controller

import at.ac.oeaw.acdh.dylensauto.constants.Constants
import at.ac.oeaw.acdh.dylensauto.dao.SessionRepository
import at.ac.oeaw.acdh.dylensauto.entity.Session
import at.ac.oeaw.acdh.dylensauto.entity.analysis.CompletionEvent
import at.ac.oeaw.acdh.dylensauto.entity.dto.*
import org.springframework.core.env.Environment
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import java.time.Duration
import java.util.*

//this code is so kawaii (* ^ ω ^)(* ^ ω ^)|(* ^ ω ^)|

@Controller
class Analysis(val sessionRepository: SessionRepository, val env: Environment) {

    @PostMapping("/analysis")
    fun analysis(appVersion: String): ModelAndView {
        val sessions = sessionRepository.findSuitableSessions(
            env.getProperty("min.session.duration")!!.toLong(),
            appVersion.toDouble(),
            Constants.getTestEndTimeForVersion(appVersion)!!
        )

        //this code piece is used to determine all unique sauto ids in the current analysis
        //save the idSet print into log folder
//        val idSet = HashSet<String>()
//        sessions!!.forEach { session ->
//        var clicks = session!!.mouseClicks
//        //remove specific ids (only needed for v1)
//        if (appVersion.equals("1.0")) {
//            clicks = removeSpecificIds(clicks)
//        }
//            clicks.forEach { click ->
//                idSet.add(click.id)
//            }
//            session!!.visitedMouseOvers.forEach { click ->
//                idSet.add(click.id)
//            }
//        }
//        print(idSet)

        val modView = ModelAndView("results.html")

        //best and worst performers
        var bestElements = HashMap<String, Double>()
        var worstElements = HashMap<String, Double>()

        //duration avg
        val durations = sessions!!.map { session -> session!!.duration }
        val dur = Duration.ofSeconds(durations.average().toLong())
        modView.addObject(
            "durationAvg", dur.toString().substring(2)
                .toLowerCase()
        )

        //app version
        modView.addObject("appVersion", appVersion)

        //session count
        modView.addObject("sessionCount", sessions.size)

        //clicks
        var clicks = ArrayList<MouseClick>()
        sessions.forEach { session -> clicks.addAll(session!!.mouseClicks) }
        //remove specific ids (only needed for v1)
        if (appVersion.equals("1.0")) {
            clicks = removeSpecificIds(clicks)
        }

        val heatMapClicks = clicks.map { click -> arrayOf(click.x, click.y, 1) }
        modView.addObject("clicks", heatMapClicks)

        //remove some general big elements
        clicks.removeAll { it.id.equals("parallel-coordinates") }
        clicks.removeAll { it.id.equals("results") }

        val clickCounts = HashMap<String, Int>()
        clicks.forEach { click ->
            if (clickCounts.contains(click.id)) {
                clickCounts[click.id] = clickCounts[click.id]!! + 1
            } else {
                clickCounts[click.id] = 1
            }
        }
        //get averages
        //then filter for ones with more than 1
        //then sort
        val clickCountPerSession =
            clickCounts.mapValues { (key, value) ->
                val avg = value.toDouble() / sessions!!.size.toDouble()
                "%.${2}f".format(avg).toDouble()
            }
                .filter { (key, value) -> value > 1 }.toList().sortedByDescending { (_, value) -> value }.toMap()

        modView.addObject("clickCountPerSession", clickCountPerSession)

        bestElements = addToBestWorst(bestElements, clickCountPerSession.keys.take(3), 1.0)
        worstElements =
            addToBestWorst(
                worstElements,
                clickCountPerSession.keys.toTypedArray().takeLast(3).asReversed(),
                1.0
            )

        //movements
        val movements = ArrayList<MousePosition>()
        sessions.forEach { session -> movements.addAll(session!!.mousePositions) }
        val heatMapMovements = movements.map { movement -> arrayOf(movement.x, movement.y, 1) }
        modView.addObject("movements", heatMapMovements)

        //mouse overs
        val mouseOverCount = HashMap<String, Int>()
        sessions.forEach { session ->
            val grouped = session!!.visitedMouseOvers.filter { !it.id.equals("root") }.groupingBy { it.id }.eachCount()
            grouped.forEach { (key, value) ->
                if (mouseOverCount.contains(key)) {
                    mouseOverCount[key] = value + mouseOverCount[key]!!
                } else {
                    mouseOverCount[key] = value
                }
            }
        }
        //remove ignore from mouse overs. ignore are sauto ids that we need to
        // ignore because of some frontend hack(ex: parent of an element with sauto id)
        mouseOverCount.remove("ignore")
        val mouseOverAvg = mouseOverCount.mapValues { (_, value) ->
            val avg = value.toDouble() / sessions.size.toDouble()
            "%.${2}f".format(avg).toDouble()
        }
        val sortedMouseOverCount =
            mouseOverAvg.toList().filter { (_, value) -> value >= 5 }.sortedByDescending { (_, value) -> value }.toMap()
        modView.addObject("mouseOverCount", sortedMouseOverCount)

        //completion events
        modView.addObject("uniqueCompletionEventCount", Constants.uniqueCompletionEventCount(appVersion))

        val sessionToCompletionEvents: HashMap<Session, HashMap<String, ArrayList<CompletionEvent>>> =
            transformCompletionEvents(sessions, appVersion)

        var totalEventCompletionDuration = 0.0
        var totalCompletionEventCount = 0
        sessionToCompletionEvents.forEach { (_, completionEvents) ->
            completionEvents.forEach { (_, innerCompletionEvents) ->
                innerCompletionEvents.forEach { completionEvent ->
                    totalEventCompletionDuration += completionEvent.duration
                    totalCompletionEventCount++
                }
            }
        }

        var avgCompletionEventCount = totalCompletionEventCount.toDouble() / sessions.size.toDouble()
        modView.addObject("avgCompletionEventCount", "%.${2}f".format(avgCompletionEventCount).toDouble())

        var avgCompletionEventDuration =
            (totalEventCompletionDuration / totalCompletionEventCount.toDouble())
        modView.addObject(
            "avgCompletionEventDuration", "%.${2}f".format(avgCompletionEventDuration).toDouble()
        )//round to 2 decimal places

        //aggregate completion events into one
        val flatCompletionEvents = flattenCompletionEvents(sessionToCompletionEvents)


        val completionEventCountAvgs: HashMap<String, Double> = flatCompletionEvents.mapValues { (_, value) ->
            val avg = value.size.toDouble() / sessions.size.toDouble()
            "%.${2}f".format(avg).toDouble()
        } as HashMap<String, Double> /* = java.util.HashMap<kotlin.String, kotlin.Double> */
        Constants.completionIds(appVersion)!!.forEach { id ->
            if (!completionEventCountAvgs.contains(id)) {
                completionEventCountAvgs[id] = 0.0
            }
        }


        val sortedCompletionEventCountAvgs = completionEventCountAvgs.toList()
            .sortedByDescending { (_, value) -> value }
            .toMap()

        bestElements = addToBestWorst(bestElements, sortedCompletionEventCountAvgs.keys.take(3), 2.0)
        worstElements =
            addToBestWorst(
                worstElements,
                sortedCompletionEventCountAvgs.keys.toTypedArray().takeLast(3).asReversed(),
                2.0
            )

        val sortedCompletionEventCountAvgsWithDesc = sortedCompletionEventCountAvgs.entries.associate {
            it.key to Pair(
                it.value,
                Constants.completionIdsToTasks(appVersion)!![it.key]!!
            )
        }
        modView.addObject("completionEventCountAvgs", sortedCompletionEventCountAvgsWithDesc)

        val completionEventTimeStats = calculateCompletionEventTimeStats(flatCompletionEvents, appVersion)
        val completionEventInteractionStats = calculateCompletionEventInteractionStats(flatCompletionEvents, appVersion)


        modView.addObject("completionEventTimeStats", completionEventTimeStats)
        modView.addObject("completionEventInteractionStats", completionEventInteractionStats)

        bestElements = addToBestWorst(
            bestElements,
            completionEventTimeStats.filter { (_, value) -> value.avg != null }.keys.take(3),
            0.5
        )
        worstElements =
            addToBestWorst(
                worstElements,
                completionEventTimeStats.keys.toTypedArray().takeLast(3)
                    .asReversed(),
                0.5
            )

        bestElements = addToBestWorst(
            bestElements,
            completionEventInteractionStats.filter { (_, value) -> value.avg != null }.keys.take(3),
            0.5
        )
        worstElements =
            addToBestWorst(
                worstElements,
                completionEventInteractionStats.keys.toTypedArray()
                    .takeLast(3).asReversed(), 0.5
            )


        val repeatedClicks = getRepeatedClicks(sessions, appVersion)
        modView.addObject("repeatedClicks", repeatedClicks)

        val patterns2 = getMatchedPatterns2(sessions, appVersion)
        modView.addObject("patterns2", patterns2)

        val patterns3 = getMatchedPatterns3(sessions, appVersion)
        modView.addObject("patterns3", patterns3)

        val patterns4 = getMatchedPatterns4(sessions, appVersion)
        modView.addObject("patterns4", patterns4)

        val completionEvent2Patterns = getMatchedCompletionEvent2Patterns(sessionToCompletionEvents)
        modView.addObject("completionEvent2Patterns", completionEvent2Patterns)

        val completionEvent3Patterns = getMatchedCompletionEvent3Patterns(sessionToCompletionEvents)
        modView.addObject("completionEvent3Patterns", completionEvent3Patterns)

        val completionEvent4Patterns = getMatchedCompletionEvent4Patterns(sessionToCompletionEvents)
        modView.addObject("completionEvent4Patterns", completionEvent4Patterns)

        val expectations: SortedMap<String, ArrayList<Int>> = when (appVersion) {
            "1.0" -> evaluateAgainstDevExpectation1(sessionToCompletionEvents, appVersion)
            "2.0" -> evaluateAgainstDevExpectation2(sessionToCompletionEvents, appVersion)
            "3.0" -> evaluateAgainstDevExpectation3(sessionToCompletionEvents, appVersion)
            else -> evaluateAgainstDevExpectation1(sessionToCompletionEvents, appVersion)
        }

        modView.addObject("expectations", expectations)

        modView.addObject("completionIdsToTasks", Constants.completionIdsToTasks(appVersion))

        //first click durations
        val clickFirstStats = getClickFirstStats(sessions, appVersion)
        modView.addObject("clickFirstStats", clickFirstStats)

        bestElements = addToBestWorst(
            bestElements,
            clickFirstStats.keys.take(3),
            1.0
        )

        worstElements =
            addToBestWorst(
                worstElements,
                clickFirstStats.keys.toTypedArray()
                    .takeLast(3).asReversed(), 1.0
            )

        //inferential metrics
        val inferentialMetrics = HashMap<String, InteractionStat>()

        //key Presses
        val keyPresses = sessions.map { session ->
            session!!.keyPresses
        }
        val keyPressStat = getInteractionCountStats(keyPresses)
        inferentialMetrics.put("Key Press Count", keyPressStat)

        //scrolls
        val scrolls = sessions.map { session ->
            session!!.scrolls
        }
        val scrollStat = getInteractionCountStats(scrolls)
        inferentialMetrics.put("Scroll Count", scrollStat)

        if (appVersion != "1.0") {
            //resizes
            val resizes = sessions.map { session ->
                session!!.resizes
            }
            val resizeStat = getResizeCountStats(resizes)
            inferentialMetrics.put("Resize Count", resizeStat)
        }

        if (appVersion == "3.0") {
            val timeouts = sessions.map { session ->
                session!!.timeouts
            }
            val timeoutStat = getTimeoutCountStats(timeouts)
            inferentialMetrics.put("Timeout Count", timeoutStat)
        }

        modView.addObject("inferentialMetrics", inferentialMetrics)

        //drags
        val flattenedDrags = ArrayList<Drag>()
        sessions.forEach { session ->
            session!!.drags.forEach {
                if (it.start.x != it.end.x || it.end.x != it.end.y) { //remove drags that have the same start and end position
                    flattenedDrags.add(it)
                }
            }
        }
        modView.addObject("drags", flattenedDrags)

        //clicks per page and durations per page only exist on v3 because there were no pages before
        if (appVersion.equals("3.0")) {
            //clicks per page
            val clicksPerPage: HashMap<String, Int> =
                mapOf(
                    "info" to 0,
                    "ego-network-tab" to 0,
                    "general-network-tab" to 0,
                    "general-network-speaker-tab" to 0
                ) as HashMap<String, Int>

            sessions.forEach { session ->
                var clicksPerSession = session!!.mouseClicks
                clicksPerSession.sortBy { it.timestamp }
                var currentPageIndex = 0
                var currentPage = "info" //page loads with the info page until users click on one of the tabs
                for (i in 0 until clicksPerSession.size) {
                    if (clicksPerPage.keys.contains(clicksPerSession[i].id)) {
                        clicksPerPage[currentPage] = clicksPerPage[currentPage]!! + i - currentPageIndex
                        currentPageIndex = i
                        currentPage = clicksPerSession[i].id
                    }
                }
                clicksPerPage[currentPage] = clicksPerPage[currentPage]!! + clicksPerSession.size - 1 - currentPageIndex
            }

            val clicksSum = clicksPerPage.values.sum()
            var clicksPerPageWithPercentage = HashMap<String, Pair<Double, Double>>();
            clicksPerPage.forEach { (key, value) ->
                val first = value.toDouble() / sessions.size.toDouble()
                val second = (value * 100.0) / clicksSum
                clicksPerPageWithPercentage.put(
                    key,
                    Pair("%.${2}f".format(first).toDouble(), "%.${2}f".format(second).toDouble())
                )
            }
            modView.addObject(
                "clicksPerPage",
                clicksPerPageWithPercentage.toList().sortedByDescending { (_, value) -> value.first }.toMap()
            )

            //duration per page
            val durationsPerPage: HashMap<String, Long> =
                mapOf(
                    "info" to 0L,
                    "ego-network-tab" to 0L,
                    "general-network-tab" to 0L,
                    "general-network-speaker-tab" to 0L
                ) as HashMap<String, Long>
            var durationSum = 0L
            sessions.forEach { session ->
                var clicksPerSession = session!!.mouseClicks
                clicksPerSession.sortBy { it.timestamp }
                var currentPageTimestamp = session.startTime.time
                var currentPage = "info" //page loads with the info page until users click on one of the tabs
                for (i in 0 until clicksPerSession.size) {
                    if (durationsPerPage.keys.contains(clicksPerSession[i].id)) {
                        durationsPerPage[currentPage] =
                            durationsPerPage[currentPage]!! + clicksPerSession[i].timestamp - currentPageTimestamp
                        currentPageTimestamp = clicksPerSession[i].timestamp
                        currentPage = clicksPerSession[i].id
                    }
                }
                durationsPerPage[currentPage] =
                    durationsPerPage[currentPage]!! + session.endTime.time - currentPageTimestamp
                durationSum += session.endTime.time - session.startTime.time
            }


            var durationsPerPageWithPercentage = HashMap<String, Pair<Double, Double>>();
            durationsPerPage.forEach { (key, value) ->
                val first = (value / 1000.0) / sessions.size.toDouble() //first convert to second then get average
                val second = (value * 100.0) / durationSum
                durationsPerPageWithPercentage.put(
                    key,
                    Pair("%.${2}f".format(first).toDouble(), "%.${2}f".format(second).toDouble())
                )
            }
            modView.addObject(
                "durationsPerPage",
                durationsPerPageWithPercentage.toList().sortedByDescending { (_, value) -> value.first }.toMap()
            )
        }

        //remove from best elements if its in worst elements (can happen if it was touched once, so the duration and
        //interaction count on average will be low and it will look like it is performing well in those two categories)
        val worstElementsMap = worstElements.toList().sortedByDescending { (_, value) -> value }.take(3).toMap()
        val bestElementsMap = bestElements.filter { (key, _) -> !worstElementsMap.contains(key) }.toList()
            .sortedByDescending { (_, value) -> value }.take(3).toMap()

        modView.addObject("bestElements", bestElementsMap)
        modView.addObject("worstElements", worstElementsMap)

        return modView
    }

    private fun flattenCompletionEvents(sessionToCompletionEvents: HashMap<Session, HashMap<String, ArrayList<CompletionEvent>>>): HashMap<String, ArrayList<CompletionEvent>> {
        val flatCompletionEvents = HashMap<String, ArrayList<CompletionEvent>>()

        sessionToCompletionEvents.values.forEach { events ->
            events.forEach { (key, value) ->
                if (flatCompletionEvents.contains(key)) {
                    flatCompletionEvents[key]!!.addAll(value)
                } else {
                    flatCompletionEvents[key] = ArrayList(value)
                }
            }
        }

        return flatCompletionEvents
    }

    private fun getClickFirstStats(
        sessions: List<Session?>,
        appVersion: String
    ): Map<String, TimeStat> {
        val flatFirstClickDurations = HashMap<String, ArrayList<Double>>()
        sessions.forEach { session ->
            var sessionClicks = session!!.mouseClicks
            //dont need these
            sessionClicks.removeAll { it.id.equals("results") }
            sessionClicks.removeAll { it.id.equals("parallel-coordinates") }
            sessionClicks.removeAll { it.id.equals("root") }


            if (appVersion.equals("1.0")) {
                sessionClicks = removeSpecificIds(session.mouseClicks)
            }

            val firstClickDurations = HashMap<String, Double>()
            val uniqueIds = HashSet<String>()
            sessionClicks.forEach { uniqueIds.add(it.id) }
            sessionClicks.sortBy { it.timestamp }
            uniqueIds.forEach { id ->
                val duration = sessionClicks.first { it.id.equals(id) }.timestamp - session.startTime.time
                if(duration>0){
                    firstClickDurations[id] = duration / 1000.0 //in secs
                }
            }

            firstClickDurations.forEach { (key, value) ->
                if (flatFirstClickDurations.contains(key)) {
                    flatFirstClickDurations[key]!!.add(value)
                } else {
                    flatFirstClickDurations[key] = arrayListOf(value)
                }
            }
        }

        //is not timestat but works anyways...
        val clickFirstStats = HashMap<String, TimeStat>()
        flatFirstClickDurations.forEach { (key, value) ->
            val min = value.minOf { it }
            val max = value.maxOf { it }
            val avg = value.average()
            clickFirstStats.put(
                key,
                TimeStat(
                    "%.${2}f".format(min).toDouble(),
                    "%.${2}f".format(max).toDouble(),
                    "%.${2}f".format(avg).toDouble()
                )
            )
        }

        return clickFirstStats.toList().sortedBy { (_, value) -> value.avg }.toMap()
    }

    private fun <T> getInteractionCountStats(
        interactions: List<ArrayList<T>>,
    ): InteractionStat {
        val sizes = interactions.map { it.size }
        val interactionMin = sizes.minByOrNull { it }
        val interactionMax = sizes.maxByOrNull { it }
        var interactionCount = 0
        interactions.forEach { interactionCount += it.size }
        val interactionAvg = interactionCount.toDouble() / interactions.count().toDouble()
        val interaction = InteractionStat(interactionMin, interactionMax, "%.${2}f".format(interactionAvg).toDouble())
        return interaction
    }

    private fun getResizeCountStats(
        interactions: List<ArrayList<Resize>?>,
    ): InteractionStat {
        val interactionsWithoutNull = interactions.filterNotNull()
        val sizes = interactionsWithoutNull.map { it.size }
        val interactionMin = sizes.minByOrNull { it }
        val interactionMax = sizes.maxByOrNull { it }
        var interactionCount = 0
        interactionsWithoutNull.forEach { interactionCount += it.size }
        val interactionAvg = interactionCount.toDouble() / interactionsWithoutNull.count().toDouble()
        val interaction = InteractionStat(interactionMin, interactionMax, "%.${2}f".format(interactionAvg).toDouble())
        return interaction
    }

    private fun getTimeoutCountStats(
        interactions: List<ArrayList<Timeout>?>,
    ): InteractionStat {
        val interactionsWithoutNull = interactions.filterNotNull()
        val sizes = interactionsWithoutNull.map { it.size }
        val interactionMin = sizes.minByOrNull { it }
        val interactionMax = sizes.maxByOrNull { it }
        var interactionCount = 0
        interactionsWithoutNull.forEach { interactionCount += it.size }
        val interactionAvg = interactionCount.toDouble() / interactionsWithoutNull.count().toDouble()
        val interaction = InteractionStat(interactionMin, interactionMax, "%.${2}f".format(interactionAvg).toDouble())
        return interaction
    }

    //add points based on order in the list
    private fun addToBestWorst(
        bestWorst: HashMap<String, Double>,
        items: List<String>,
        weight: Double
    ): HashMap<String, Double> {
        if (items.size != 3) {
            return bestWorst
        }
        for (i in 0..2) {
            val id = items[i]
            if (bestWorst.contains(id)) {
                bestWorst[id] = bestWorst[id]!! + (3 - i) * weight
            } else {
                bestWorst[id] = (3 - i) * weight
            }
        }
        return bestWorst
    }

    private fun evaluateAgainstDevExpectation1(
        sessionToCompletionEvents: HashMap<Session, HashMap<String, ArrayList<CompletionEvent>>>,
        appVersion: String
    ): SortedMap<String, ArrayList<Int>> {
        val correctExpectations = ArrayList<String>()
        val incorrectExpectations = ArrayList<String>()
        fun String.addToExpectations(correct: Boolean) {
            if (correct) {
                correctExpectations.add(this)
            } else {
                incorrectExpectations.add(this)
            }
        }

        sessionToCompletionEvents.forEach { (session, completionEvents) ->
            val sortedCompletionEvents = ArrayList<CompletionEvent>()
            completionEvents.forEach { (key, value) ->
                sortedCompletionEvents.addAll(value)
            }
            sortedCompletionEvents.sortBy { it.timestamp }

            //only do this on dev expectation 1
            var clicks = removeSpecificIds(session.mouseClicks)

            val rule1 = "First User Event completed is 'visualize a word' (first completion event queryButton-pane1)"
            rule1.addToExpectations(sortedCompletionEvents[0].id.equals("queryButton-pane1"))

            val rule3 = "User story completed at least once: "
            Constants.completionIds(appVersion)!!.forEach { id ->
                val rule3Specific = rule3 + id
                rule3Specific.addToExpectations(!completionEvents.get(id)!!.isEmpty())
            }

            val rule4 = "Settings clicked not more than 4 times (opened max twice)"
            val settingsCount = sortedCompletionEvents.count { it.id.equals("settings-button") }
            rule4.addToExpectations(settingsCount <= 4)

            val rule8 =
                "Network graph 1 should be zoomed in/out at least once (either through button or through mouse wheel)"
            var scrollZoom = session.scrolls.any { it.id.equals("network-pane1") }
            if (scrollZoom) {
                correctExpectations.add(rule8)
            } else {
                val zoomButton = clicks.any {
                    it.id.equals("zoom-in-button-pane1") || it.id.equals("zoom-out-button-pane1")
                }
                rule8.addToExpectations(zoomButton)
            }

            val rule9 =
                "Network graph 2 should be zoomed in/out at least once (either through button or through mouse wheel)"
            scrollZoom = session.scrolls.any { it.id.equals("network-pane2") }
            if (scrollZoom) {
                correctExpectations.add(rule9)
            } else {
                val zoomButton = clicks.any {
                    it.id.equals("zoom-in-button-pane2") || it.id.equals("zoom-out-button-pane2")
                }
                rule9.addToExpectations(zoomButton)
            }

            val rule10 =
                "Select-all-checkbox in the network graph 1 should be clicked at least once"
            val selectAll1 = clicks.any { it.id.equals("select-all-checkbox-pane1") }
            rule10.addToExpectations(selectAll1)

            val rule11 =
                "Select-all-checkbox in the network graph 2 should be clicked at least once"
            val selectAll2 = clicks.any { it.id.equals("select-all-checkbox-pane2") }
            rule11.addToExpectations(selectAll2)

            val rule12 = "At most one of the setting options should be changed"
            val optionsChanged = clicks.count {
                it.id.equals("selected-words-top-checkbox-option") ||
                        it.id.equals("color-option-verb") ||
                        it.id.equals("color-option-adjective") ||
                        it.id.equals("color-option-proper_noun") ||
                        it.id.equals("color-option-noun") ||
                        it.id.equals("font-option") ||
                        it.id.equals("white-label-checkbox-option") ||
                        it.id.equals("bold-checkbox-option") ||
                        it.id.equals("digits-slider-option") ||
                        it.id.equals("opacity-slider-option")
            }
            rule12.addToExpectations(optionsChanged <= 1)

            val rule13 = "Select Subcorpus should be selected before and not after select target word"
            val timeSortedClicks = clicks.sortedBy { it.timestamp }
            val subcorpusIndex = timeSortedClicks.indexOfFirst { it.id.equals("selectSubCorpus-pane1") }
            val targetWordIndex = timeSortedClicks.indexOfFirst { it.id.equals("selectTargetword-pane1") }
            var rule13Bool = false
            rule13Bool = if (subcorpusIndex == -1) {
                true
            } else if (targetWordIndex == -1) {
                false
            } else {
                subcorpusIndex < targetWordIndex
            }
            rule13.addToExpectations(rule13Bool)
        }

        val expectations = mergeExpectations(correctExpectations, incorrectExpectations)

        return expectations.toSortedMap()
    }

    private fun evaluateAgainstDevExpectation2(
        sessionToCompletionEvents: HashMap<Session, HashMap<String, ArrayList<CompletionEvent>>>,
        appVersion: String
    ): SortedMap<String, ArrayList<Int>> {
        val correctExpectations = ArrayList<String>()
        val incorrectExpectations = ArrayList<String>()
        fun String.addToExpectations(correct: Boolean) {
            if (correct) {
                correctExpectations.add(this)
            } else {
                incorrectExpectations.add(this)
            }
        }

        sessionToCompletionEvents.forEach { (session, completionEvents) ->
            var sortedCompletionEvents = ArrayList<CompletionEvent>()
            completionEvents.forEach { (key, value) ->
                sortedCompletionEvents.addAll(value)
            }
            sortedCompletionEvents.sortBy { it.timestamp }

            val rule1 = "First User Event completed is 'visualize a word' (first completion event queryButton-pane1)"
            val rule2 = "Nodes in network graph 1 are clicked at least 3 times"
            val rule4 = "Settings clicked not more than 4 times (opened max twice)"
            val rule5 =
                "Table item should be clicked at least 10 times (for selecting words that appear in both networks)"
            val rule6 = "Table sort should be used at least once"
            if (sortedCompletionEvents.size > 0) {
                rule1.addToExpectations(sortedCompletionEvents[0].id.equals("queryButton-pane1"))

                val pane1Count = sortedCompletionEvents.count { it.id.equals("pane1-node") }
                rule2.addToExpectations(pane1Count >= 3)

                val settingsCount = sortedCompletionEvents.count { it.id.equals("settings-button") }
                rule4.addToExpectations(settingsCount <= 4)

                val tableItemCount = sortedCompletionEvents.count { it.id.equals("table-item") }
                rule5.addToExpectations(tableItemCount >= 10)

                val tableSortCount = sortedCompletionEvents.count { it.id.equals("table-sort") }
                rule6.addToExpectations(tableSortCount >= 1)
            } else {
                rule1.addToExpectations(false)
                rule2.addToExpectations(false)
                rule4.addToExpectations(false)
                rule5.addToExpectations(false)
                rule6.addToExpectations(false)
            }

            val rule3 = "User story completed at least once: "
            Constants.completionIds(appVersion)!!.forEach { id ->
                val rule3Specific = rule3 + id
                rule3Specific.addToExpectations(!completionEvents.get(id)!!.isEmpty())
            }

            val rule8 =
                "Network graph 1 should be zoomed in/out at least once (either through button or through mouse wheel)"
            var scrollZoom = session.scrolls.any { it.id.equals("network-pane1") }
            if (scrollZoom) {
                correctExpectations.add(rule8)
            } else {
                val zoomButton = session.mouseClicks.any {
                    it.id.equals("zoom-in-button-pane1") || it.id.equals("zoom-out-button-pane1")
                }
                rule8.addToExpectations(zoomButton)
            }

            val rule9 =
                "Network graph 2 should be zoomed in/out at least once (either through button or through mouse wheel)"
            scrollZoom = session.scrolls.any { it.id.equals("network-pane2") }
            if (scrollZoom) {
                correctExpectations.add(rule9)
            } else {
                val zoomButton = session.mouseClicks.any {
                    it.id.equals("zoom-in-button-pane2") || it.id.equals("zoom-out-button-pane2")
                }
                rule9.addToExpectations(zoomButton)
            }

            val rule10 =
                "Select all checkbox interaction should be done at least twice(select and deselect) for network 1"
            val selectAll1 = session.mouseClicks.count {
                it.id.equals("select-all-checkbox-pane1") ||
                        it.id.equals("parallel-coordinates-deselectAll-pane1") ||
                        it.id.equals("parallel-coordinates-selectAll-pane1") ||
                        it.id.equals("table-select-all")
            }
            rule10.addToExpectations(selectAll1 >= 2)

            val rule11 =
                "Select all checkbox interaction should be done at least twice(select and deselect) for network 2"
            val selectAll2 = session.mouseClicks.count {
                it.id.equals("select-all-checkbox-pane2") ||
                        it.id.equals("parallel-coordinates-deselectAll-pane2") ||
                        it.id.equals("parallel-coordinates-selectAll-pane2") ||
                        it.id.equals("table-select-all")
            }
            rule11.addToExpectations(selectAll2 >= 2)

            val rule12 = "At most one of the setting options should be changed"
            val optionsChanged = session.mouseClicks.count {
                it.id.equals("selected-words-top-checkbox-option") ||
                        it.id.equals("color-option-verb") ||
                        it.id.equals("color-option-adjective") ||
                        it.id.equals("color-option-proper_noun") ||
                        it.id.equals("color-option-noun") ||
                        it.id.equals("font-option") ||
                        it.id.equals("white-label-checkbox-option") ||
                        it.id.equals("bold-checkbox-option") ||
                        it.id.equals("digits-slider-option") ||
                        it.id.equals("opacity-slider-option")
            }
            rule12.addToExpectations(optionsChanged <= 1)

            val rule13 = "Select Subcorpus should be selected before and not after select target word"
            val timeSortedClicks = session.mouseClicks.sortedBy { it.timestamp }
            val subcorpusIndex = timeSortedClicks.indexOfFirst { it.id.equals("selectSubCorpus-pane1") }
            val targetWordIndex = timeSortedClicks.indexOfFirst { it.id.equals("selectTargetword-pane1") }
            rule13.addToExpectations(subcorpusIndex < targetWordIndex)

            val rule14 = "User should hover over *(overlapping node labels) on parallel coordinates at least twice"
            val starHoverCount = session.visitedMouseOvers.count { it.id.equals("parallel-coordinates-*") }
            rule14.addToExpectations(starHoverCount >= 2)

            val rule15 = "User should hover over parallel coordinates lines at least twice"
            val parallelCoordinateLineCount =
                session.visitedMouseOvers.count { it.id.equals("parallel-coordinates-line") }
            rule15.addToExpectations(parallelCoordinateLineCount >= 2)

            val rule16 = "Full screen button should be clicked at least once"
            val fullScreen = session.mouseClicks.any {
                it.id.equals("toggleFullScreenButton-pane1") ||
                        it.id.equals("toggleFullScreenButton-pane2") ||
                        it.id.equals("toggleFullScreenButton-nodeMetrics") ||
                        it.id.equals("toggleFullScreenButton-timeSeries")
            }
            rule16.addToExpectations(fullScreen)

            //1 vertical and 1 right-horizontal in the resizes list is extra and must not be taken into consideration, because they are called automatically once on page load)
            //therefore i remove them before analysing the list
            val rule17 = "The resize functionality should be used at least once"
            var resizes = session.resizes
            if (resizes == null || resizes.isEmpty()) {
                rule17.addToExpectations(false)
            } else {
                val firstVertical = resizes.indexOfFirst { it.paneId.equals("vertical") }
                if (firstVertical != -1) {
                    resizes = ArrayList(resizes.drop(firstVertical))
                }
                val firstRightHorizontal = resizes.indexOfFirst { it.paneId.equals("right-horizontal") }
                if (firstRightHorizontal != -1) {
                    resizes = ArrayList(resizes.drop(firstRightHorizontal))
                }
                rule17.addToExpectations(resizes!!.size > 0)
            }

            val rule18 = "Time series metric changed at least once"
            val timeSeriesMetric = session.mouseClicks.any { it.id.equals("timeSeries-metric-option") }
            rule18.addToExpectations(timeSeriesMetric)

            val rule19 = "Time series hover at least once"
            val timeSeriesHover = session.visitedMouseOvers.any { it.id.equals("timeSeries-line") }
            rule19.addToExpectations(timeSeriesHover)
        }

        val expectations = mergeExpectations(correctExpectations, incorrectExpectations)

        return expectations.toSortedMap()
    }

    private fun evaluateAgainstDevExpectation3(
        sessionToCompletionEvents: HashMap<Session, HashMap<String, ArrayList<CompletionEvent>>>,
        appVersion: String
    ): SortedMap<String, ArrayList<Int>> {

        val correctExpectations = ArrayList<String>()
        val incorrectExpectations = ArrayList<String>()
        fun String.addToExpectations(correct: Boolean) {
            if (correct) {
                correctExpectations.add(this)
            } else {
                incorrectExpectations.add(this)
            }
        }
        sessionToCompletionEvents.forEach { (session, completionEvents) ->
            val sortedCompletionEvents = ArrayList<CompletionEvent>()
            completionEvents.forEach { (key, value) ->
                sortedCompletionEvents.addAll(value)
            }
            sortedCompletionEvents.sortBy { it.timestamp }

            val rule1 =
                "First User Event completed is on of the 'visualize a word/party/speaker' (queryButton-Ego-pane1/queryButton-Party-pane1/queryButton-Speaker-pane1)"
            val rule4 = "Settings buttons clicked not more than 6 times"
            if (sortedCompletionEvents.size > 0) {
                rule1.addToExpectations(
                    sortedCompletionEvents[0].id.equals("queryButton-Ego-pane1") ||
                            sortedCompletionEvents[0].id.equals("queryButton-Party-pane1") ||
                            sortedCompletionEvents[0].id.equals("queryButton-Speaker-pane1")
                )

                val settingsCount =
                    sortedCompletionEvents.count { it.id.equals("settings-button-nav") || it.id.equals("settings-button-result") }
                rule4.addToExpectations(settingsCount <= 6)
            } else {
                rule1.addToExpectations(false)
                rule4.addToExpectations(false)
            }

            val rule3 = "User story completed at least once: "
            Constants.completionIds(appVersion)!!.forEach { id ->
                val rule3Specific = rule3 + id
                rule3Specific.addToExpectations(!completionEvents.get(id)!!.isEmpty())
            }

            val rule8 =
                "A network graph should be zoomed in/out at least once (either through button or through mouse wheel)"
            var scrollZoom = session.scrolls.any { it.id.equals("network-pane1") || it.id.equals("network-pane2") }
            if (scrollZoom) {
                correctExpectations.add(rule8)
            } else {
                val zoomButton = session.mouseClicks.any {
                    it.id.equals("zoom-in-button-pane1") ||
                            it.id.equals("zoom-out-button-pane1") ||
                            it.id.equals("zoom-in-button-pane2") ||
                            it.id.equals("zoom-out-button-pane2")
                }
                rule8.addToExpectations(zoomButton)
            }

            val rule10 =
                "Select all interaction(all select all/deselect all buttons) should be done at least once"
            val selectAll = session.mouseClicks.any {
                it.id.equals("select-all-checkbox-pane1") ||
                        it.id.equals("parallel-coordinates-deselectAll-pane1") ||
                        it.id.equals("parallel-coordinates-selectAll-pane1") ||
                        it.id.equals("table-select-all") ||
                        it.id.equals("select-all-checkbox-pane2") ||
                        it.id.equals("parallel-coordinates-deselectAll-pane2") ||
                        it.id.equals("parallel-coordinates-selectAll-pane2") ||
                        it.id.equals("table-select-all")
            }
            rule10.addToExpectations(selectAll)

            val rule11 =
                "Show clusters interaction should be done at least once"
            val showClusters = session.mouseClicks.any {
                it.id.equals("show-clusters-checkbox-pane1") ||
                        it.id.equals("show-clusters-checkbox-pane2")
            }
            rule11.addToExpectations(showClusters)

            val rule12 = "At most one of the setting options should be changed"
            val optionsChanged = session.mouseClicks.count {
                it.id.equals("selected-words-top-checkbox-option") ||
                        it.id.equals("color-option-verb") ||
                        it.id.equals("color-option-adjective") ||
                        it.id.equals("color-option-proper_noun") ||
                        it.id.equals("color-option-noun") ||
                        it.id.equals("font-option") ||
                        it.id.equals("white-label-checkbox-option") ||
                        it.id.equals("bold-checkbox-option") ||
                        it.id.equals("digits-slider-option") ||
                        it.id.equals("opacity-slider-option") ||
                        it.id.equals("minimum-similarity-slider-option")
            }
            rule12.addToExpectations(optionsChanged <= 1)

            val rule13 = "Select Subcorpus should be selected before and not after select target word"
            val timeSortedClicks = session.mouseClicks.sortedBy { it.timestamp }
            val subcorpusIndex = timeSortedClicks.indexOfFirst { it.id.equals("selectSubCorpus-pane1") }
            val targetWordIndex = timeSortedClicks.indexOfFirst { it.id.equals("selectTargetword-pane1") }
            rule13.addToExpectations(subcorpusIndex < targetWordIndex)

            val rule14 = "User should hover over *(overlapping node labels) on parallel coordinates at least once"
            val starHover = session.visitedMouseOvers.any { it.id.equals("parallel-coordinates-*") }
            rule14.addToExpectations(starHover)

            val rule15 = "User should hover over parallel coordinates lines at least once"
            val parallelCoordinateLine =
                session.visitedMouseOvers.any { it.id.equals("parallel-coordinates-line") }
            rule15.addToExpectations(parallelCoordinateLine)

            val rule16 = "Full screen button should be clicked at least once"
            val fullScreen = session.mouseClicks.any {
                it.id.equals("toggleFullScreenButton-pane1") ||
                        it.id.equals("toggleFullScreenButton-pane2") ||
                        it.id.equals("toggleFullScreenButton-nodeMetrics") ||
                        it.id.equals("toggleFullScreenButton-timeSeries")
            }
            rule16.addToExpectations(fullScreen)

            //1 vertical and 1 right-horizontal in the resizes list is extra and must not be taken into consideration,
            // because they are called automatically once on page load)
            //therefore i remove them before analysing the list
            val rule17 = "The resize functionality should be used at least once"
            var resizes = session.resizes
            if (resizes == null || resizes.isEmpty()) {
                rule17.addToExpectations(false)
            } else {
                val firstVertical = resizes.indexOfFirst { it.paneId.equals("vertical") }
                if (firstVertical != -1) {
                    resizes = ArrayList(resizes.drop(firstVertical))
                }
                val firstRightHorizontal = resizes.indexOfFirst { it.paneId.equals("right-horizontal") }
                if (firstRightHorizontal != -1) {
                    resizes = ArrayList(resizes.drop(firstRightHorizontal))
                }
                rule17.addToExpectations(resizes.size > 0)
            }

            val rule18 = "Time series metric changed at least once"
            val timeSeriesMetric = session.mouseClicks.any { it.id.equals("timeSeries-metric-option") }
            rule18.addToExpectations(timeSeriesMetric)

            val rule19 = "Time series hover at least once"
            val timeSeriesHover = session.visitedMouseOvers.any { it.id.equals("timeSeries-line") }
            rule19.addToExpectations(timeSeriesHover)

            val rule20 = "At least 5% of the users download table as json/csv at least once"
            val jsonCsv =
                session.mouseClicks.any { it.id.equals("export-json-button") || it.id.equals("export-csv-button") }
            rule20.addToExpectations(jsonCsv)

            val rule21 = "At least 15% of the users change at least one parallel coordinates axis from settings"
            val axisOption = session.mouseClicks.any { it.id.equals("parallel-coordinates-axis-checkbox-option") }
            rule21.addToExpectations(axisOption)

            val rule22 = "User gets general network query timeout at most once"
            if (session.timeouts != null) {
                rule22.addToExpectations(session.timeouts.size <= 1)
            } else {
                rule22.addToExpectations(false)
            }

            val rule23 =
                "At least 30% of the users change general network query metric or percentage of nodes at least once"
            val nodeFilter = session.mouseClicks.any {
                it.id.equals("metricGeneralOption") ||
                        it.id.equals("node-filter-slider-pane1") ||
                        it.id.equals("node-filter-slider-pane2")
            }
            rule23.addToExpectations(nodeFilter)

            val rule24 = "At least 50% of the users change general network query party or speaker at least once"
            val partySpeaker = session.mouseClicks.any {
                it.id.equals("partyOption") ||
                        it.id.equals("speakerOption")
            }
            rule24.addToExpectations(partySpeaker)

            val rule25 = "Info tabs clicked at least once"
            val infoTab = session.mouseClicks.any { it.id.equals("info-tab") }
            rule25.addToExpectations(infoTab)

            val rule26 = "At least 20% of users click on the Dysen link"
            val dysenLink = session.mouseClicks.any { it.id.equals("dysen-link") }
            rule26.addToExpectations(dysenLink)

            val rule27 = "One of the reset query buttons is clicked at least once"
            val resetQuery = session.mouseClicks.any {
                it.id.equals("resetQueryButton-Ego-pane1") ||
                        it.id.equals("resetQueryButton-Ego-pane2") ||
                        it.id.equals("resetQueryButton-Party-pane1") ||
                        it.id.equals("resetQueryButton-Party-pane1") ||
                        it.id.equals("resetQueryButton-Speaker-pane1") ||
                        it.id.equals("resetQueryButton-Speaker-pane2")
            }
            rule27.addToExpectations(resetQuery)
        }

        val expectations = mergeExpectations(correctExpectations, incorrectExpectations)

        return expectations.toSortedMap()
    }

    private fun mergeExpectations(
        correctExpectations: ArrayList<String>,
        incorrectExpectations: ArrayList<String>
    ): HashMap<String, ArrayList<Int>> {
        val correctExp = correctExpectations.groupingBy { it }.eachCount()
        val incorrectExp = incorrectExpectations.groupingBy { it }.eachCount()
        val expectations = HashMap<String, ArrayList<Int>>()
        correctExp.forEach { (key, value) ->
            expectations[key] = arrayListOf(value, 0)
        }
        incorrectExp.forEach { (key, value) ->
            if (expectations.keys.contains(key)) {
                expectations[key] = arrayListOf(expectations[key]!![0], value)
            } else {
                expectations[key] = arrayListOf(0, value)
            }

        }
        return expectations
    }

    data class ClickOrderedPair(val id1: String, val id2: String) {
        override fun toString(): String {
            return "$id1 -> $id2"
        }
    }

    data class ClickOrderedTriple(val id1: String, val id2: String, val id3: String) {
        override fun toString(): String {
            return "$id1 -> $id2 -> $id3"
        }
    }

    data class ClickOrderedQuadruple(val id1: String, val id2: String, val id3: String, val id4: String) {
        override fun toString(): String {
            return "$id1 -> $id2 -> $id3 -> $id4"
        }
    }

    private fun removeSpecificIds(clicks: ArrayList<MouseClick>): ArrayList<MouseClick> {
        return ArrayList(clicks.map {
            if (it.id.contains("selectTargetWord-option")) {
                MouseClick("selectTargetWord-option", it.x, it.y, it.timestamp)
            } else if (it.id.contains("table-sort")) {
                MouseClick("table-sort", it.x, it.y, it.timestamp)
            } else if (it.id.contains("year-slider-pane1")) {
                MouseClick("year-slider-pane1", it.x, it.y, it.timestamp)
            } else if (it.id.contains("year-slider-pane2")) {
                MouseClick("year-slider-pane2", it.x, it.y, it.timestamp)
            } else if (it.id.contains("subCorpusOption-")) {
                MouseClick("selectSubCorpus-option", it.x, it.y, it.timestamp)
            } else if (it.id.contains("corpusOption-")) { //comes after subcorpus option so those are out
                MouseClick("selectCorpus-option", it.x, it.y, it.timestamp)
            } else if (it.id.contains("year-")) {
                MouseClick("year-option", it.x, it.y, it.timestamp)
            } else if (it.id.contains("pane2-node")) {
                MouseClick("pane2-node", it.x, it.y, it.timestamp)
            } else if (it.id.contains("pane1-node")) {
                MouseClick("pane1-node", it.x, it.y, it.timestamp)
            } else if (it.id.contains("x-button-parallel-coordinates")) {
                MouseClick("x-button-parallel-coordinates", it.x, it.y, it.timestamp)
            } else if (it.id.contains("table-item")) {
                MouseClick("table-item", it.x, it.y, it.timestamp)
            } else {
                it
            }
        })
    }

    private fun getMatchedPatterns2(sessions: List<Session?>, appVersion: String): Map<ClickOrderedPair, Int> {
        val clickOrderedPairs = ArrayList<ClickOrderedPair>()
        sessions.forEach { session ->
            var clicks = session!!.mouseClicks
            //remove specific ids (only needed for v1)
            if (appVersion.equals("1.0")) {
                clicks = removeSpecificIds(clicks)
            }

            clicks.removeAll { it.id.equals("results") }
            clicks.removeAll { it.id.equals("parallel-coordinates") }

            clicks.sortBy { mouseClick -> mouseClick.timestamp }
            for (i in 0 until clicks.size) {
                if (i != clicks.size - 1) {
                    if (!clicks[i].id.equals(clicks[i + 1].id)) { //only add if its not a repeated action
                        clickOrderedPairs.add(ClickOrderedPair(clicks[i].id, clicks[i + 1].id))
                    }
                }
            }
        }


        val clickOrderedPairsCounts = clickOrderedPairs.groupingBy { it }.eachCount()
        return clickOrderedPairsCounts.toList().filter { (_, value) -> value >= 5 }
            .sortedByDescending { (_, value) -> value }
            .toMap()
    }

    private fun getMatchedPatterns3(sessions: List<Session?>, appVersion: String): Map<ClickOrderedTriple, Int> {
        val clickOrderedTriples = ArrayList<ClickOrderedTriple>()
        sessions.forEach { session ->
            var clicks = session!!.mouseClicks
            //remove specific ids (only needed for v1)
            if (appVersion.equals("1.0")) {
                clicks = removeSpecificIds(clicks)
            }

            clicks.removeAll { it.id.equals("results") }
            clicks.removeAll { it.id.equals("parallel-coordinates") }

            clicks.sortBy { mouseClick -> mouseClick.timestamp }
            for (i in 0 until clicks.size) {
                if (i < clicks.size - 2) {
                    if (!(clicks[i].id.equals(clicks[i + 1].id) && clicks[i].id.equals(clicks[i + 2].id))) { //only add if its not a repeated action
                        clickOrderedTriples.add(ClickOrderedTriple(clicks[i].id, clicks[i + 1].id, clicks[i + 2].id))
                    }
                }
            }
        }


        val clickOrderedTriplesCounts = clickOrderedTriples.groupingBy { it }.eachCount()
        return clickOrderedTriplesCounts.toList().filter { (_, value) -> value >= 5 }
            .sortedByDescending { (_, value) -> value }
            .toMap()
    }

    private fun getMatchedPatterns4(sessions: List<Session?>, appVersion: String): Map<ClickOrderedQuadruple, Int> {
        val clickOrderedQuadruples = ArrayList<ClickOrderedQuadruple>()
        sessions.forEach { session ->
            var clicks = session!!.mouseClicks
            //remove specific ids (only needed for v1)
            if (appVersion.equals("1.0")) {
                clicks = removeSpecificIds(clicks)
            }

            clicks.removeAll { it.id.equals("results") }
            clicks.removeAll { it.id.equals("parallel-coordinates") }

            clicks.sortBy { mouseClick -> mouseClick.timestamp }
            for (i in 0 until clicks.size) {
                if (i < clicks.size - 3) {
                    if (!(clicks[i].id.equals(clicks[i + 1].id) && clicks[i].id.equals(clicks[i + 2].id) && clicks[i].id.equals(
                            clicks[i + 3].id
                        ))
                    ) { //only add if its not a repeated action
                        clickOrderedQuadruples.add(
                            ClickOrderedQuadruple(
                                clicks[i].id,
                                clicks[i + 1].id,
                                clicks[i + 2].id,
                                clicks[i + 3].id
                            )
                        )
                    }
                }
            }
        }


        val clickOrderedQuadruplesCounts = clickOrderedQuadruples.groupingBy { it }.eachCount()
        return clickOrderedQuadruplesCounts.toList().filter { (_, value) -> value >= 5 }
            .sortedByDescending { (_, value) -> value }
            .toMap()
    }

    private fun getMatchedCompletionEvent2Patterns(
        sessionToCompletionEvents: HashMap<Session, HashMap<String, ArrayList<CompletionEvent>>>
    ): Map<ClickOrderedPair, Int> {
        val clickOrderedPairs = ArrayList<ClickOrderedPair>()
        sessionToCompletionEvents.forEach { (session, events) ->
            val flatCompletionEvents = ArrayList<CompletionEvent>()
            events.forEach { (_, value) ->
                flatCompletionEvents.addAll(value)
            }
            flatCompletionEvents.sortBy { event -> event.timestamp }

            for (i in 0 until flatCompletionEvents.size) {
                if (i < flatCompletionEvents.size - 1) { //avoid array index out of bounds
                    if (!flatCompletionEvents[i].id.equals(flatCompletionEvents[i + 1].id)) { //only add if its not a repeated action
                        clickOrderedPairs.add(
                            ClickOrderedPair(
                                flatCompletionEvents[i].id,
                                flatCompletionEvents[i + 1].id
                            )
                        )
                    }
                }
            }
        }

        val eventOrderedPairsCounts = clickOrderedPairs.groupingBy { it }.eachCount()
        return eventOrderedPairsCounts.toList().filter { (_, value) -> value >= 5 }
            .sortedByDescending { (_, value) -> value }
            .toMap()
    }

    private fun getMatchedCompletionEvent3Patterns(
        sessionToCompletionEvents: HashMap<Session, HashMap<String, ArrayList<CompletionEvent>>>
    ): Map<ClickOrderedTriple, Int> {
        val clickOrderedTriples = ArrayList<ClickOrderedTriple>()
        sessionToCompletionEvents.forEach { (_, events) ->
            val flatCompletionEvents = ArrayList<CompletionEvent>()
            events.forEach { (_, value) ->
                flatCompletionEvents.addAll(value)
            }
            flatCompletionEvents.sortBy { event -> event.timestamp }

            for (i in 0 until flatCompletionEvents.size) {
                if (i < flatCompletionEvents.size - 2) { //avoid array index out of bounds
                    if (!(flatCompletionEvents[i].id.equals(flatCompletionEvents[i + 1].id)
                                && flatCompletionEvents[i].id.equals(flatCompletionEvents[i + 2].id))
                    ) { //only add if its not a repeated action
                        clickOrderedTriples.add(
                            ClickOrderedTriple(
                                flatCompletionEvents[i].id,
                                flatCompletionEvents[i + 1].id,
                                flatCompletionEvents[i + 2].id
                            )
                        )
                    }
                }
            }
        }

        val eventOrderedTriplesCounts = clickOrderedTriples.groupingBy { it }.eachCount()
        return eventOrderedTriplesCounts.toList().filter { (_, value) -> value >= 5 }
            .sortedByDescending { (_, value) -> value }
            .toMap()
    }

    private fun getMatchedCompletionEvent4Patterns(
        sessionToCompletionEvents: HashMap<Session, HashMap<String, ArrayList<CompletionEvent>>>
    ): Map<ClickOrderedQuadruple, Int> {
        val clickOrderedQuadruples = ArrayList<ClickOrderedQuadruple>()
        sessionToCompletionEvents.forEach { (_, events) ->
            val flatCompletionEvents = ArrayList<CompletionEvent>()
            events.forEach { (_, value) ->
                flatCompletionEvents.addAll(value)
            }
            flatCompletionEvents.sortBy { event -> event.timestamp }

            for (i in 0 until flatCompletionEvents.size) {
                if (i < flatCompletionEvents.size - 3) {//avoid array index out of bounds
                    if (!(flatCompletionEvents[i].id.equals(flatCompletionEvents[i + 1].id)
                                && flatCompletionEvents[i].id.equals(flatCompletionEvents[i + 2].id)
                                && flatCompletionEvents[i].id.equals(flatCompletionEvents[i + 3].id))
                    ) { //only add if its not a repeated action
                        clickOrderedQuadruples.add(
                            ClickOrderedQuadruple(
                                flatCompletionEvents[i].id,
                                flatCompletionEvents[i + 1].id,
                                flatCompletionEvents[i + 2].id,
                                flatCompletionEvents[i + 3].id
                            )
                        )
                    }
                }
            }
        }

        val eventOrderedQuadruplesCounts = clickOrderedQuadruples.groupingBy { it }.eachCount()
        return eventOrderedQuadruplesCounts.toList().filter { (_, value) -> value >= 5 }
            .sortedByDescending { (_, value) -> value }
            .toMap()
    }


    private fun getRepeatedClicks(sessions: List<Session?>, appVersion: String): Map<String, Int> {
        var repeatedIds = ArrayList<String>()
        sessions.forEach { session ->
            var clicks = session!!.mouseClicks
            //remove specific ids (only needed for v1)
            if (appVersion.equals("1.0")) {
                clicks = removeSpecificIds(clicks)
            }

            //byproduct ids need to be removed
            clicks.removeAll { it.id.equals("parallel-coordinates") }
            clicks.removeAll { it.id.equals("results") }

            clicks.sortBy { mouseClick -> mouseClick.timestamp }
            for (i in 0 until clicks.size) {
                if (i != clicks.size - 1) {
                    if (clicks[i].id.equals(clicks[i + 1].id)) {
                        repeatedIds.add(clicks[i].id)
                    }
                }
            }
        }
        val repeatedIdCounts = repeatedIds.groupingBy { it }.eachCount()
        return repeatedIdCounts.toList().filter { (_, value) -> value >= 5 }.sortedByDescending { (_, value) -> value }
            .toMap()
    }

    //used in html table
    data class TimeStat(val min: Double?, val max: Double?, val avg: Double?, val desc: String? = null)
    data class InteractionStat(val min: Int? = 0, val max: Int? = 0, val avg: Double?, val desc: String? = null)

    private fun calculateCompletionEventTimeStats(
        flatCompletionEvents: HashMap<String, ArrayList<CompletionEvent>>,
        appVersion: String
    ): Map<String, TimeStat> {
        val completionEventTimeStats: Map<String, TimeStat> = flatCompletionEvents.mapValues { (key, value) ->
            if (value.isEmpty()) {
                TimeStat(null, null, null, Constants.completionIdsToTasks(appVersion)!![key])
            } else {
                var totalDuration = 0.0
                value.forEach { event -> totalDuration += event.duration }
                val durationAvg = totalDuration / value.size.toDouble()
                val min = value.minByOrNull { event -> event.duration }!!.duration
                val max = value.maxByOrNull { event -> event.duration }!!.duration
                TimeStat(
                    "%.${2}f".format(min).toDouble(),
                    "%.${2}f".format(max).toDouble(),
                    "%.${2}f".format(durationAvg).toDouble(),
                    Constants.completionIdsToTasks(appVersion)!![key]
                )
            }

        }
        return completionEventTimeStats.toList().sortedBy { (_, value) -> value.avg }.toMap()
    }

    private fun calculateCompletionEventInteractionStats(
        flatCompletionEvents: HashMap<String, ArrayList<CompletionEvent>>,
        appVersion: String
    ): Map<String, InteractionStat> {
        val completionEventInteractionStats: Map<String, InteractionStat> =
            flatCompletionEvents.mapValues { (key, value) ->
                if (value.isEmpty()) {
                    InteractionStat(null, null, null, Constants.completionIdsToTasks(appVersion)!![key])
                } else {
                    var totalInteractionCount = 0.0
                    value.forEach { event -> totalInteractionCount += event.interactionCount }
                    val interactionCountAvg = totalInteractionCount / value.size.toDouble()
                    InteractionStat(
                        value.minByOrNull { event -> event.interactionCount }!!.interactionCount,
                        value.maxByOrNull { event -> event.interactionCount }!!.interactionCount,
                        "%.${2}f".format(interactionCountAvg).toDouble(),
                        Constants.completionIdsToTasks(appVersion)!![key]
                    )
                }

            }
        return completionEventInteractionStats.toList().sortedBy { (_, value) -> value.avg }.toMap()
    }


    //sorry for this abomination
    //sorry for this whole class
    fun transformCompletionEvents(
        sessions: List<Session?>,
        appVersion: String
    ): HashMap<Session, HashMap<String, ArrayList<CompletionEvent>>> {
        val sessionToCompletionEvents: HashMap<Session, HashMap<String, ArrayList<CompletionEvent>>> = HashMap()
        for (session in sessions) {

            var clicks = session!!.mouseClicks
            if (appVersion.equals("1.0")) {
                clicks = removeSpecificIds(clicks)
            }

            //workaround start
            val sessionCompletionEvents = HashMap<String, ArrayList<MouseClick>>()
            for (id in Constants.completionIds(appVersion)!!) {
                val clickList = ArrayList(clicks.filter { it.id.equals(id) })
                sessionCompletionEvents[id] = ArrayList(clickList)
            }
            //workaround end

            val completionEvents = HashMap<String, ArrayList<CompletionEvent>>()


            for (entry in sessionCompletionEvents) {
                val completionEventList = ArrayList<CompletionEvent>()
                for (mouseClick in entry.value) {
                    completionEventList.add(CompletionEvent(entry.key, mouseClick.timestamp, 0.0, 0))
                }
                completionEvents[entry.key] = ArrayList(completionEventList)
            }

            val flattenedCompletionEvents = ArrayList<CompletionEvent>()
            completionEvents.forEach { (_, value) ->
                flattenedCompletionEvents.addAll(value)
            }
            flattenedCompletionEvents.sortBy { it.timestamp }

            if (flattenedCompletionEvents.size > 0) {
                completionEvents.clear()
                var event = CompletionEvent(
                    flattenedCompletionEvents[0].id,
                    flattenedCompletionEvents[0].timestamp,
                    (flattenedCompletionEvents[0].timestamp - session!!.startTime.time) / 1000.0, //in secs
                    calculateInteractionCount(session, session.startTime.time, flattenedCompletionEvents[0].timestamp)
                )
                completionEvents[event.id] = arrayListOf(event)

                for (i in 1 until flattenedCompletionEvents.size) {
                    event = CompletionEvent(
                        flattenedCompletionEvents[i].id,
                        flattenedCompletionEvents[i].timestamp,
                        (flattenedCompletionEvents[i].timestamp - flattenedCompletionEvents[i - 1].timestamp) / 1000.0, //in secs
                        calculateInteractionCount(
                            session,
                            flattenedCompletionEvents[i - 1].timestamp,
                            flattenedCompletionEvents[i].timestamp
                        )
                    )
                    if (completionEvents.contains(event.id)) {
                        completionEvents[event.id]!!.add(event)
                    } else {
                        completionEvents[event.id] = arrayListOf(event)
                    }
                }

                for (id in Constants.completionIds(appVersion)!!) {
                    if (!completionEvents.contains(id)) {
                        completionEvents[id] = ArrayList()
                    }
                }
            }

            sessionToCompletionEvents[session] = HashMap(completionEvents)
        }


        return sessionToCompletionEvents
    }

    private fun calculateInteractionCount(session: Session, start: Long, end: Long): Int {
        var interactionCount = 0
        interactionCount += session.keyPresses.count { event -> event.timestamp in (start + 1) until end }
        interactionCount += session.visitedMouseOvers.count { event -> event.timestamp in (start + 1) until end }
        interactionCount += session.mouseClicks.count { event -> event.timestamp in (start + 1) until end }
        interactionCount += session.drags.count { event -> event.start.timestamp in (start + 1) until end }
        return interactionCount
    }

}
