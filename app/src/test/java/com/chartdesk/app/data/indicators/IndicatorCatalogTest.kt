package com.chartdesk.app.data.indicators

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorCatalogTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `catalog exposes twelve unique indicators`() {
        assertEquals(12, IndicatorCatalog.all.size)
        assertEquals(IndicatorCatalog.all.size, IndicatorCatalog.all.map { it.type }.toSet().size)
        assertTrue(IndicatorCatalog.all.any { it.type == "supertrend" })
        assertTrue(IndicatorCatalog.all.any { it.type == "ichimoku" })
        assertTrue(IndicatorCatalog.all.any { it.type == "stochastic" })
    }

    @Test
    fun `pane assignment matches the chart layer`() {
        assertEquals("overlay", IndicatorCatalog.spec("ma")?.pane)
        assertEquals("separate", IndicatorCatalog.spec("rsi")?.pane)
        assertEquals("separate", IndicatorCatalog.spec("macd")?.pane)
        assertEquals("volume", IndicatorCatalog.spec("volume")?.pane)
    }

    @Test
    fun `defaults are positive numbers`() {
        IndicatorCatalog.all.forEach { spec ->
            spec.params.forEach { param ->
                assertTrue("${spec.type}.${param.key} must be positive", param.default > 0.0)
            }
        }
        assertEquals(20.0, IndicatorCatalog.defaults("ma")["period"])
        assertEquals(26.0, IndicatorCatalog.defaults("macd")["slow"])
    }

    @Test
    fun `params round-trip through json`() {
        val params = mapOf("period" to 20.0, "mult" to 2.0)
        val encoded = IndicatorCodec.encodeParams(json, params)
        assertEquals(params, IndicatorCodec.decodeParams(json, encoded))
    }

    @Test
    fun `corrupt params fall back to empty instead of throwing`() {
        assertEquals(emptyMap<String, Double>(), IndicatorCodec.decodeParams(json, "{broken"))
    }

    @Test
    fun `volume is the only default indicator`() {
        val defaults = IndicatorCatalog.defaultConfigs()
        assertEquals(1, defaults.size)
        assertEquals("volume", defaults.first().type)
        assertTrue(defaults.first().visible)
    }
}
