package com.chartdesk.app.chart

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Creates independent controllers for the 2/4-chart grid. The main chart keeps the Hilt singleton. */
@Singleton
class ChartControllerFactory @Inject constructor(
    private val json: Json,
) {
    fun create(): ChartController = ChartController(json)
}
