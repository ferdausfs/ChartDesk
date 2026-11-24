package com.chartdesk.app.data.scripts

import com.chartdesk.app.data.db.ScriptDao
import com.chartdesk.app.data.db.ScriptEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ScriptRepository @Inject constructor(
    private val dao: ScriptDao,
) {

    val scripts: Flow<List<ScriptEntity>> = dao.observeAll()

    suspend fun ensureDefaults() {
        if (dao.count() > 0) return
        val now = System.currentTimeMillis()
        SAMPLE_SCRIPTS.forEachIndexed { index, sample ->
            dao.upsert(
                ScriptEntity(
                    id = "sample-${index + 1}",
                    name = sample.first,
                    code = sample.second,
                    updatedAt = now - index,
                    builtIn = true,
                ),
            )
        }
    }

    suspend fun save(id: String?, name: String, code: String): String {
        val scriptId = id ?: UUID.randomUUID().toString()
        dao.upsert(
            ScriptEntity(
                id = scriptId,
                name = name.trim().ifEmpty { "Untitled" },
                code = code,
                updatedAt = System.currentTimeMillis(),
                builtIn = false,
            ),
        )
        return scriptId
    }

    suspend fun delete(id: String) {
        dao.delete(id)
    }

    companion object {
        val SAMPLE_SCRIPTS = listOf(
            "EMA 20/50" to """
                // ChartDesk Pine subset sample
                indicator("EMA 20/50", overlay=true)
                fast = ta.ema(close, 20)
                slow = ta.ema(close, 50)
                plot(fast, title="EMA 20", color=color.green)
                plot(slow, title="EMA 50", color=color.red)
            """.trimIndent(),
            "Donchian 20" to """
                indicator("Donchian 20", overlay=true)
                upper = ta.highest(high, 20)
                lower = ta.lowest(low, 20)
                mid = (upper + lower) / 2
                plot(upper, title="Upper", color=color.green)
                plot(mid, title="Mid", color=color.blue)
                plot(lower, title="Lower", color=color.red)
            """.trimIndent(),
        )
    }
}
