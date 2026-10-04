package com.hillah.alclogger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hillah.alclogger.data.healthconnect.HealthConnectManager
import com.hillah.alclogger.data.model.DrinkConfig
import com.hillah.alclogger.data.model.DrinkRecordItem
import com.hillah.alclogger.data.preferences.DrinkPreferencesRepository
import com.hillah.alclogger.nfc.NfcHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class UiState(
    val isHealthConnectAvailable: Boolean = true,
    val hasPermissions: Boolean = false,
    val isCheckingPermissions: Boolean = true,
    val isLoadingRecords: Boolean = false,
    val drinkingDateLabel: String = "",
    val todayRecords: List<DrinkRecordItem> = emptyList(),
    val drinkSlots: List<DrinkConfig> = DrinkConfig.defaultSlots(),
    val pendingWriteSlot: Int? = null, // NFC書き込みモード中のスロット番号 (1..3)
    val errorMessage: String? = null
) {
    val totalDrinks: Int get() = todayRecords.size
    val totalAlcoholGrams: Double
        get() = ((todayRecords.sumOf { it.alcoholGrams } * 10.0).roundToInt() / 10.0)
    val totalCaloriesKcal: Double
        get() = ((todayRecords.sumOf { it.caloriesKcal } * 10.0).roundToInt() / 10.0)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val healthConnectManager = HealthConnectManager(application)
    private val preferencesRepository = DrinkPreferencesRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    init {
        // スロット設定の購読
        viewModelScope.launch {
            preferencesRepository.drinkSlotsFlow.collect { slots ->
                _uiState.update { it.copy(drinkSlots = slots) }
            }
        }

        checkHealthConnectStatus()
    }

    fun checkHealthConnectStatus() {
        viewModelScope.launch {
            val available = healthConnectManager.isHealthConnectAvailable()
            if (!available) {
                _uiState.update {
                    it.copy(
                        isHealthConnectAvailable = false,
                        hasPermissions = false,
                        isCheckingPermissions = false
                    )
                }
                return@launch
            }

            val hasPerms = healthConnectManager.hasPermissions()
            _uiState.update {
                it.copy(
                    isHealthConnectAvailable = true,
                    hasPermissions = hasPerms,
                    isCheckingPermissions = false
                )
            }

            if (hasPerms) {
                refreshTodayRecords()
            }
        }
    }

    fun refreshTodayRecords() {
        val drinkingDate = healthConnectManager.getDrinkingDate()
        val dateLabel = drinkingDate.format(DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPANESE))

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRecords = true, drinkingDateLabel = dateLabel) }
            val result = healthConnectManager.readTodayDrinkRecords()
            result.onSuccess { records ->
                _uiState.update {
                    it.copy(
                        todayRecords = records,
                        isLoadingRecords = false,
                        drinkingDateLabel = dateLabel,
                        errorMessage = null
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoadingRecords = false,
                        drinkingDateLabel = dateLabel,
                        errorMessage = "記録の取得に失敗しました: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * NFCタグ検出時の処理 (0.1秒記録 & 触覚フィードバック)
     */
    fun onNfcTagScanned(slotIndex: Int) {
        val slotConfig = _uiState.value.drinkSlots.find { it.slot == slotIndex }
            ?: DrinkConfig.defaultSlots().find { it.slot == slotIndex }
            ?: return

        // 即座にバイブレーションを実行（最速フィードバック）
        NfcHelper.triggerSuccessVibration(getApplication())

        viewModelScope.launch {
            val result = healthConnectManager.recordDrink(slotConfig)
            result.onSuccess {
                _eventFlow.emit("🍺 ${slotConfig.name} を1杯記録しました！")
                refreshTodayRecords()
            }.onFailure { e ->
                _eventFlow.emit("記録に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 手動追加ボタンタップ
     */
    fun onManualAdd(slotIndex: Int) {
        val slotConfig = _uiState.value.drinkSlots.find { it.slot == slotIndex }
            ?: DrinkConfig.defaultSlots().find { it.slot == slotIndex }
            ?: return

        NfcHelper.triggerSuccessVibration(getApplication())

        viewModelScope.launch {
            val result = healthConnectManager.recordDrink(slotConfig)
            result.onSuccess {
                _eventFlow.emit("🍺 ${slotConfig.name} を1杯記録しました！")
                refreshTodayRecords()
            }.onFailure { e ->
                _eventFlow.emit("記録に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 自由入力によるカスタムお酒の記録
     */
    fun onAddCustomDrink(name: String, alcoholGrams: Double, caloriesKcal: Double) {
        val drinkName = name.ifBlank { "カスタムのお酒" }
        NfcHelper.triggerSuccessVibration(getApplication())

        viewModelScope.launch {
            val result = healthConnectManager.recordCustomDrink(drinkName, alcoholGrams, caloriesKcal)
            result.onSuccess {
                _eventFlow.emit("🥃 $drinkName を記録しました！(${alcoholGrams}g)")
                refreshTodayRecords()
            }.onFailure { e ->
                _eventFlow.emit("記録に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 直前の1杯を取り消す
     */
    fun onUndoLastDrink() {
        viewModelScope.launch {
            val result = healthConnectManager.deleteLastDrinkRecord()
            result.onSuccess { deletedItem ->
                if (deletedItem != null) {
                    _eventFlow.emit("↩️ 直前の「${deletedItem.name}」を取り消しました")
                    refreshTodayRecords()
                } else {
                    _eventFlow.emit("取り消す記録がありません")
                }
            }.onFailure { e ->
                _eventFlow.emit("取り消しに失敗しました: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 特定レコードの削除
     */
    fun onDeleteRecord(recordId: String) {
        viewModelScope.launch {
            val result = healthConnectManager.deleteDrinkRecord(recordId)
            result.onSuccess {
                _eventFlow.emit("🗑️ 記録を削除しました")
                refreshTodayRecords()
            }.onFailure { e ->
                _eventFlow.emit("削除に失敗しました: ${e.localizedMessage}")
            }
        }
    }

    /**
     * スロット設定の更新
     */
    fun onSaveSlotConfig(config: DrinkConfig) {
        viewModelScope.launch {
            preferencesRepository.updateSlot(config)
            _eventFlow.emit("💾 スロット${config.slot}の設定を保存しました")
        }
    }

    /**
     * NFCタグ書き込みモードの開始
     */
    fun startNfcWriteMode(slotIndex: Int) {
        _uiState.update { it.copy(pendingWriteSlot = slotIndex) }
    }

    /**
     * NFCタグ書き込みモードのキャンセル
     */
    fun cancelNfcWriteMode() {
        _uiState.update { it.copy(pendingWriteSlot = null) }
    }

    /**
     * NFCタグ書き込み完了時のコールバック
     */
    fun onTagWriteCompleted(success: Boolean, message: String) {
        _uiState.update { it.copy(pendingWriteSlot = null) }
        viewModelScope.launch {
            _eventFlow.emit(message)
        }
    }
}
