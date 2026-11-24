package com.chartdesk.app.data.alerts

import com.chartdesk.app.data.db.AlertEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {

    private fun alert(symbol: String, direction: String, price: Double, active: Boolean = true) = AlertEntity(
        id = "$symbol-$direction-$price",
        symbol = symbol,
        direction = direction,
        price = price,
        active = active,
        createdAt = 0,
    )

    @Test
    fun `above triggers at or above the target`() {
        val a = alert("BTCUSDT", "above", 100.0)
        assertTrue(AlertEngine.isHit(a, 100.0))
        assertTrue(AlertEngine.isHit(a, 100.01))
        assertFalse(AlertEngine.isHit(a, 99.99))
    }

    @Test
    fun `below triggers at or below the target`() {
        val a = alert("BTCUSDT", "below", 100.0)
        assertTrue(AlertEngine.isHit(a, 100.0))
        assertTrue(AlertEngine.isHit(a, 99.5))
        assertFalse(AlertEngine.isHit(a, 100.5))
    }

    @Test
    fun `unknown direction never triggers`() {
        assertFalse(AlertEngine.isHit(alert("BTCUSDT", "sideways", 100.0), 50.0))
    }

    @Test
    fun `evaluate returns only matching active alerts with a price`() {
        val alerts = listOf(
            alert("BTCUSDT", "above", 100.0),
            alert("ETHUSDT", "above", 10.0),
            alert("SOLUSDT", "above", 1.0, active = false),
            alert("XRPUSDT", "below", 1.0),
        )
        val hits = AlertEngine.evaluate(alerts, mapOf("BTCUSDT" to 101.0, "ETHUSDT" to 9.0, "SOLUSDT" to 2.0))
        assertEquals(1, hits.size)
        assertEquals("BTCUSDT", hits[0].alert.symbol)
        assertEquals(101.0, hits[0].price, 0.0001)
    }

    @Test
    fun `ids are unique`() {
        val ids = (1..50).map { AlertEngine.newId() }.toSet()
        assertEquals(50, ids.size)
    }
}
