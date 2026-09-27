package com.hillah.alclogger.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import com.hillah.alclogger.data.model.DrinkConfig
import com.hillah.alclogger.data.model.DrinkRecordItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HealthConnectManager(private val context: Context) {

    val healthConnectClient: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    val permissions = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class)
    )

    private val alcoholRegex = Regex("""\[(?:純)?アルコール:([0-9.]+)g\]""")

    /**
     * 飲酒日（Drinking Date）の判定
     * 午前4時（4:00 AM）を日付の切り替わり境界とし、深夜0:00〜03:59は前日の晩酌として扱う
     */
    fun getDrinkingDate(zonedDateTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): LocalDate {
        return if (zonedDateTime.hour < 4) {
            zonedDateTime.toLocalDate().minusDays(1)
        } else {
            zonedDateTime.toLocalDate()
        }
    }

    fun isHealthConnectAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /**
     * お酒の記録を Health Connect の NutritionRecord に書き込む
     * 深夜0:00〜03:59にかざされた場合、Health ConnectおよびGarmin Connectで
     * 翌日の記録とみなされるのを防ぐため、前日23:59のタイムスタンプにシフトして記録する。
     */
    suspend fun recordDrink(config: DrinkConfig): Result<String> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(
            IllegalStateException("Health Connect が利用できません。アプリがインストールされているか確認してください。")
        )
        try {
            val zoneId = ZoneId.systemDefault()
            val nowZoned = ZonedDateTime.now(zoneId)
            val drinkingDate = getDrinkingDate(nowZoned)

            // 深夜0:00〜03:59の場合は「前日の晩酌」としてGarmin/Health Connectで当日集計されるよう前日23:59にシフト
            val (startTime, endTime) = if (nowZoned.hour < 4) {
                val secOffset = ((nowZoned.minute * 60 + nowZoned.second) % 55)
                val adjustedEnd = drinkingDate.atTime(23, 59, secOffset).atZone(zoneId).toInstant()
                val adjustedStart = adjustedEnd.minusSeconds(60)
                adjustedStart to adjustedEnd
            } else {
                val nowInstant = nowZoned.toInstant()
                nowInstant.minusSeconds(60) to nowInstant
            }
            val zoneOffset = zoneId.rules.getOffset(endTime)

            val actualTimeNote = if (nowZoned.hour < 4) {
                String.format("(深夜%02d:%02d)", nowZoned.hour, nowZoned.minute)
            } else null

            val displayName = listOfNotNull(
                config.name,
                "(${config.volumeMl.toInt()}ml ${config.abvPercent}%)",
                actualTimeNote,
                "[純アルコール:${config.alcoholGrams}g]"
            ).joinToString(" ")

            val record = NutritionRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                name = displayName,
                energy = Energy.kilocalories(config.caloriesKcal),
                mealType = MealType.MEAL_TYPE_UNKNOWN
            )

            val response = client.insertRecords(listOf(record))
            val insertedId = response.recordIdsList.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("レコードIDの取得に失敗しました。"))

            Result.success(insertedId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 今日の飲酒レコードを全件読み込み（午前4時切り替え対応）
     */
    suspend fun readTodayDrinkRecords(): Result<List<DrinkRecordItem>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(
            IllegalStateException("Health Connect が利用できません。")
        )

        try {
            val zoneId = ZoneId.systemDefault()
            val nowZoned = ZonedDateTime.now(zoneId)
            val drinkingDate = getDrinkingDate(nowZoned)

            // 当日分の読み込み範囲: 飲酒日（前日夜）の 04:00:00 〜 翌日 04:00:00
            // 前日23:59台のシフト記録も、0:00〜4:00の記録も確実にカバーするため飲酒日の00:00〜翌日04:00を取得
            val startOfDay = drinkingDate.atStartOfDay(zoneId).toInstant()
            val endOfDay = drinkingDate.plusDays(1).atTime(4, 0).atZone(zoneId).toInstant()

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = NutritionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )

            // 本アプリで記録された、またはアルコールタグが付与されたレコードを抽出
            val drinkRecords = response.records
                .filter { record ->
                    val name = record.name.orEmpty()
                    name.contains("アルコール") ||
                            record.mealType == MealType.MEAL_TYPE_UNKNOWN && (name.contains("ビール") || name.contains("ハイボール") || name.contains("酒") || name.contains("ワイン") || name.contains("本搾り") || name.contains("サワー"))
                }
                .map { record ->
                    val name = record.name ?: "お酒"
                    val alcoholMatch = alcoholRegex.find(name)
                    val alcoholGrams = alcoholMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 14.0

                    DrinkRecordItem(
                        id = record.metadata.id,
                        name = name.replace(alcoholRegex, "").trim(),
                        timestamp = record.endTime,
                        alcoholGrams = alcoholGrams,
                        caloriesKcal = record.energy?.inKilocalories ?: 0.0
                    )
                }
                .sortedByDescending { it.timestamp }

            Result.success(drinkRecords)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 指定レコードの削除
     */
    suspend fun deleteDrinkRecord(recordId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext Result.failure(
            IllegalStateException("Health Connect が利用できません。")
        )

        try {
            client.deleteRecords(
                recordType = NutritionRecord::class,
                recordIdsList = listOf(recordId),
                clientRecordIdsList = emptyList()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 今日の直前の1杯を取り消す（最新レコードを削除）
     */
    suspend fun deleteLastDrinkRecord(): Result<DrinkRecordItem?> = withContext(Dispatchers.IO) {
        val readResult = readTodayDrinkRecords()
        if (readResult.isFailure) {
            return@withContext Result.failure(readResult.exceptionOrNull()!!)
        }

        val records = readResult.getOrNull() ?: emptyList()
        val latest = records.firstOrNull() ?: return@withContext Result.success(null)

        val deleteResult = deleteDrinkRecord(latest.id)
        if (deleteResult.isSuccess) {
            Result.success(latest)
        } else {
            Result.failure(deleteResult.exceptionOrNull()!!)
        }
    }
}
