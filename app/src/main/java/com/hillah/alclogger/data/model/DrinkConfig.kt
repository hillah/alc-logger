package com.hillah.alclogger.data.model

import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.roundToInt

@Serializable
data class DrinkConfig(
    val slot: Int,
    val name: String,
    val volumeMl: Double,
    val abvPercent: Double,
    val caloriesKcal: Double,
    val alcoholGramsOverride: Double? = null
) {
    /**
     * 純アルコール量(g)の算出: 容量(ml) × (度数 / 100) × 0.8 (アルコール比重)
     * overrideが設定されている場合はそれを優先
     */
    val alcoholGrams: Double
        get() = alcoholGramsOverride ?: ((volumeMl * (abvPercent / 100.0) * 0.8 * 10.0).roundToInt() / 10.0)

    companion object {
        fun defaultSlots(): List<DrinkConfig> = listOf(
            DrinkConfig(
                slot = 1,
                name = "アサヒスーパードライ",
                volumeMl = 350.0,
                abvPercent = 5.0,
                caloriesKcal = 147.0
            ),
            DrinkConfig(
                slot = 2,
                name = "KIRIN 本搾り レモン",
                volumeMl = 350.0,
                abvPercent = 6.0,
                caloriesKcal = 133.0
            ),
            DrinkConfig(
                slot = 3,
                name = "こだわり酒場のレモンサワーの素",
                volumeMl = 80.0,
                abvPercent = 25.0,
                caloriesKcal = 123.0
            )
        )
    }
}

data class DrinkRecordItem(
    val id: String,
    val name: String,
    val timestamp: Instant,
    val alcoholGrams: Double,
    val caloriesKcal: Double
)
