package com.chartdesk.app.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncPayload(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val watchlists: List<WatchlistDto> = emptyList(),
    val items: List<WatchlistItemDto> = emptyList(),
    val layouts: List<LayoutDto> = emptyList(),
    val scripts: List<ScriptDto> = emptyList(),
    val drawings: List<DrawingDtoSync> = emptyList(),
    val indicators: List<IndicatorDtoSync> = emptyList(),
    val alerts: List<AlertDtoSync> = emptyList(),
)

@Serializable
data class WatchlistDto(val id: String, val name: String, val sortOrder: Int, val createdAt: Long)

@Serializable
data class WatchlistItemDto(val listId: String, val symbol: String, val sortOrder: Int)

@Serializable
data class LayoutDto(
    val id: String,
    val name: String,
    val symbol: String,
    val timeframe: String,
    val chartType: String,
    val grid: Int,
    val createdAt: Long,
)

@Serializable
data class ScriptDto(val id: String, val name: String, val code: String, val updatedAt: Long, val builtIn: Boolean)

@Serializable
data class DrawingDtoSync(
    val symbol: String,
    val id: String,
    val type: String,
    val payloadJson: String,
    val locked: Boolean,
    val sortOrder: Int,
    val updatedAt: Long,
)

@Serializable
data class IndicatorDtoSync(
    val symbol: String,
    val uid: String,
    val type: String,
    val paramsJson: String,
    val visible: Boolean,
    val sortOrder: Int,
)

@Serializable
data class AlertDtoSync(
    val id: String,
    val symbol: String,
    val direction: String,
    val price: Double,
    val active: Boolean,
    val createdAt: Long,
    val triggeredAt: Long? = null,
)
