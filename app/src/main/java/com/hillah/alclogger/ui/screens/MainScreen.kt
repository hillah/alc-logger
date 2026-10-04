package com.hillah.alclogger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hillah.alclogger.data.model.DrinkConfig
import com.hillah.alclogger.data.model.DrinkRecordItem
import com.hillah.alclogger.ui.theme.AccentBeer
import com.hillah.alclogger.ui.theme.AccentDanger
import com.hillah.alclogger.ui.theme.AccentHighball
import com.hillah.alclogger.ui.theme.AccentSake
import com.hillah.alclogger.ui.theme.AmberContainer
import com.hillah.alclogger.ui.theme.AmberOnContainer
import com.hillah.alclogger.ui.theme.AmberPrimary
import com.hillah.alclogger.viewmodel.MainViewModel
import com.hillah.alclogger.viewmodel.UiState
import kotlinx.coroutines.flow.collectLatest
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showNfcWriteDialog by remember { mutableStateOf(false) }
    var showCustomDrinkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🍺 alc-logger",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshTodayRecords() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "更新")
                    }
                    IconButton(onClick = { showNfcWriteDialog = true }) {
                        Icon(Icons.Default.Nfc, contentDescription = "NFCタグ初期化")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 1. 権限・状態バナー
            if (!uiState.isHealthConnectAvailable) {
                item {
                    UnavailableBanner(message = "Health Connect が利用できません。Google Playよりインストールしてください。")
                }
            } else if (!uiState.hasPermissions && !uiState.isCheckingPermissions) {
                item {
                    PermissionBanner(onRequestPermissions = onRequestPermissions)
                }
            }

            // 2. 本日の杯数サマリカード
            item {
                TodaySummaryCard(uiState = uiState)
            }

            // 3. クイック手動追加ボタン（3スロット）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "クイック記録（手動追加）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { showCustomDrinkDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("自由入力", fontSize = 13.sp)
                    }
                }
            }

            item {
                DrinkSlotRow(
                    slots = uiState.drinkSlots,
                    onAddDrink = { slotIndex -> viewModel.onManualAdd(slotIndex) }
                )
            }

            // 4. アクションボタン（直前取り消し & 自由入力）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onUndoLastDrink() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("直前を取り消す", maxLines = 1, fontSize = 13.sp)
                    }

                    FilledTonalButton(
                        onClick = { showCustomDrinkDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AmberContainer,
                            contentColor = AmberOnContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("自由入力で追加", maxLines = 1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 5. 本日の飲酒履歴ヘッダー
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "本日の記録タイムライン",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (uiState.isLoadingRecords) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }

            // 履歴リストアイテム
            if (uiState.todayRecords.isEmpty()) {
                item {
                    EmptyRecordsCard()
                }
            } else {
                items(uiState.todayRecords, key = { it.id }) { record ->
                    DrinkRecordRow(
                        record = record,
                        onDelete = { viewModel.onDeleteRecord(record.id) }
                    )
                }
            }
        }
    }

    // NFCタグ書き込みダイアログ
    if (showNfcWriteDialog || uiState.pendingWriteSlot != null) {
        NfcWriteDialog(
            slots = uiState.drinkSlots,
            pendingSlot = uiState.pendingWriteSlot,
            onSelectSlot = { slot -> viewModel.startNfcWriteMode(slot) },
            onDismiss = {
                viewModel.cancelNfcWriteMode()
                showNfcWriteDialog = false
            }
        )
    }

    // スロット設定ダイアログ
    if (showSettingsDialog) {
        DrinkSettingsDialog(
            slots = uiState.drinkSlots,
            onSave = { config -> viewModel.onSaveSlotConfig(config) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // 自由入力記録ダイアログ
    if (showCustomDrinkDialog) {
        CustomDrinkDialog(
            onAdd = { name, alcoholGrams, caloriesKcal ->
                viewModel.onAddCustomDrink(name, alcoholGrams, caloriesKcal)
                showCustomDrinkDialog = false
            },
            onDismiss = { showCustomDrinkDialog = false }
        )
    }
}

/**
 * 権限要求バナー
 */
@Composable
private fun PermissionBanner(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AmberContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = AmberOnContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Health Connect 連携が必要です",
                    fontWeight = FontWeight.Bold,
                    color = AmberOnContainer,
                    fontSize = 14.sp
                )
                Text(
                    text = "飲酒データを自動記録するため、栄養データの読み取り・書き込み権限を許可してください。",
                    fontSize = 12.sp,
                    color = AmberOnContainer.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
            ) {
                Text("許可", color = Color.White)
            }
        }
    }
}

@Composable
private fun UnavailableBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AccentDanger.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = AccentDanger)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                color = AccentDanger,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 本日の杯数サマリカード
 */
@Composable
private fun TodaySummaryCard(uiState: UiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val titleText = if (uiState.drinkingDateLabel.isNotBlank()) {
                "本日のお酒 (${uiState.drinkingDateLabel}分 ※朝4時切替)"
            } else {
                "本日のお酒 (※朝4時切替)"
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 杯数の超大型表示
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🍺",
                    fontSize = 36.sp,
                    modifier = Modifier.padding(bottom = 6.dp, end = 8.dp)
                )
                Text(
                    text = "${uiState.totalDrinks}",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = " 杯",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 純アルコール量 & カロリー
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "純アルコール量",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${uiState.totalAlcoholGrams} g",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.totalAlcoholGrams > 40.0) AccentDanger else MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "推定カロリー",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${uiState.totalCaloriesKcal.toInt()} kcal",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 厚労省ガイドラインの参考情報
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "※節度ある適度な飲酒目安: 純アルコール 約20g/日",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 3スロットの手動追加ボタン行
 */
@Composable
private fun DrinkSlotRow(
    slots: List<DrinkConfig>,
    onAddDrink: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slots.sortedBy { it.slot }.take(3).forEach { slotConfig ->
            val (icon, badgeColor) = when {
                slotConfig.name.contains("ビール") || slotConfig.name.contains("ドライ") || slotConfig.slot == 1 -> "🍺" to AccentBeer
                slotConfig.name.contains("本搾り") || slotConfig.name.contains("レモン") -> "🍋" to AccentHighball
                slotConfig.name.contains("ハイボール") -> "🥃" to AccentHighball
                slotConfig.name.contains("サワー") -> "🍹" to AccentHighball
                slotConfig.name.contains("酒") || slotConfig.name.contains("ワイン") -> "🍶" to AccentSake
                else -> "🍻" to AccentBeer
            }

            FilledTonalButton(
                onClick = { onAddDrink(slotConfig.slot) },
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = icon, fontSize = 22.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = slotConfig.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "+1杯 (${slotConfig.alcoholGrams}g)",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 記録履歴アイテム
 */
@Composable
private fun DrinkRecordRow(
    record: DrinkRecordItem,
    onDelete: () -> Unit
) {
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = timeFormatter.format(record.timestamp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "純アルコール: ${record.alcoholGrams}g / ${record.caloriesKcal.toInt()}kcal",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyRecordsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🍻", fontSize = 32.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "本日の飲酒記録はまだありません",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "NFCタグにかざすか、上のボタンから記録してください",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * NFCタグ書き込みダイアログ
 */
@Composable
private fun NfcWriteDialog(
    slots: List<DrinkConfig>,
    pendingSlot: Int?,
    onSelectSlot: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Nfc, contentDescription = null, tint = AmberPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("NFCタグの初期化・書き込み")
            }
        },
        text = {
            Column {
                if (pendingSlot == null) {
                    Text(
                        text = "書き込みたいお酒のスロットを選択してください。選択後、新品のNFCタグを端末の背面にピタッとかざすと書き込まれます。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    slots.sortedBy { it.slot }.forEach { slotConfig ->
                        OutlinedButton(
                            onClick = { onSelectSlot(slotConfig.slot) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("スロット ${slotConfig.slot}: ${slotConfig.name}")
                        }
                    }
                } else {
                    val target = slots.find { it.slot == pendingSlot }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = AmberPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "スロット ${pendingSlot}: ${target?.name ?: ""} を書き込み中...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "NFCタグをスマートフォンのNFC読み取り部（通常は背面中央〜上部）にかざしてください。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (pendingSlot != null) {
                TextButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
            }
        },
        dismissButton = {
            if (pendingSlot == null) {
                TextButton(onClick = onDismiss) {
                    Text("閉じる")
                }
            }
        }
    )
}

/**
 * 3スロット設定ダイアログ
 */
@Composable
private fun DrinkSettingsDialog(
    slots: List<DrinkConfig>,
    onSave: (DrinkConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val currentSlot = slots.find { it.slot == selectedTabIndex + 1 }
        ?: DrinkConfig(slot = selectedTabIndex + 1, name = "", volumeMl = 350.0, abvPercent = 5.0, caloriesKcal = 140.0)

    var name by remember(selectedTabIndex, currentSlot) { mutableStateOf(currentSlot.name) }
    var volumeStr by remember(selectedTabIndex, currentSlot) { mutableStateOf(currentSlot.volumeMl.toInt().toString()) }
    var abvStr by remember(selectedTabIndex, currentSlot) { mutableStateOf(currentSlot.abvPercent.toString()) }
    var caloriesStr by remember(selectedTabIndex, currentSlot) { mutableStateOf(currentSlot.caloriesKcal.toInt().toString()) }

    // 自動計算された純アルコール量のプレビュー
    val volume = volumeStr.toDoubleOrNull() ?: 0.0
    val abv = abvStr.toDoubleOrNull() ?: 0.0
    val calculatedAlcohol = ((volume * (abv / 100.0) * 0.8 * 10.0).roundToInt() / 10.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("お酒プリセット設定", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("スロット1") }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("スロット2") }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text("スロット3") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("お酒の名前 (例: ビール)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = volumeStr,
                        onValueChange = { volumeStr = it },
                        label = { Text("容量 (ml)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = abvStr,
                        onValueChange = { abvStr = it },
                        label = { Text("度数 (%)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = caloriesStr,
                    onValueChange = { caloriesStr = it },
                    label = { Text("推定カロリー (kcal)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 純アルコール量自動計算表示
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("算出純アルコール量:", fontSize = 12.sp)
                        Text(
                            "$calculatedAlcohol g",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AmberPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = DrinkConfig(
                        slot = selectedTabIndex + 1,
                        name = name.ifBlank { "お酒 ${selectedTabIndex + 1}" },
                        volumeMl = volume,
                        abvPercent = abv,
                        caloriesKcal = caloriesStr.toDoubleOrNull() ?: 140.0
                    )
                    onSave(updated)
                    onDismiss()
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

/**
 * スロット外のお酒を自由に入力して記録するダイアログ
 */
@Composable
private fun CustomDrinkDialog(
    onAdd: (name: String, alcoholGrams: Double, caloriesKcal: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var alcoholStr by remember { mutableStateOf("") }
    var volumeStr by remember { mutableStateOf("") }
    var abvStr by remember { mutableStateOf("") }
    var caloriesStr by remember { mutableStateOf("") }

    // 度数と容量から純アルコール量を自動再計算するヘルパー
    fun recalculateAlcoholFromVolumeAbv() {
        val v = volumeStr.toDoubleOrNull() ?: return
        val a = abvStr.toDoubleOrNull() ?: return
        val calculated = ((v * (a / 100.0) * 0.8 * 10.0).roundToInt() / 10.0)
        alcoholStr = calculated.toString()
        val defaultCal = (calculated * 7.1).roundToInt()
        caloriesStr = defaultCal.toString()
    }

    // クイック入力プリセット
    val presets = remember {
        listOf(
            Triple("赤ワイン", 12.0, 85.0),
            Triple("白ワイン", 11.5, 80.0),
            Triple("ウイスキー", 10.0, 70.0),
            Triple("日本酒 1合", 21.6, 185.0),
            Triple("生中 500ml", 20.0, 200.0),
            Triple("焼酎水割り", 12.0, 95.0)
        )
    }

    val alcoholVal = alcoholStr.toDoubleOrNull() ?: 0.0
    val canSubmit = alcoholVal > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🥃", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("お酒を自由入力で記録", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(
                        text = "スロットにないお酒の品名と純アルコール量を入力して記録できます。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // クイック入力チップ
                item {
                    Text(
                        text = "クイック選択:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.take(3).forEach { (pName, pAlc, pCal) ->
                            FilledTonalButton(
                                onClick = {
                                    name = pName
                                    alcoholStr = pAlc.toString()
                                    caloriesStr = pCal.toInt().toString()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(pName, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.drop(3).take(3).forEach { (pName, pAlc, pCal) ->
                            FilledTonalButton(
                                onClick = {
                                    name = pName
                                    alcoholStr = pAlc.toString()
                                    caloriesStr = pCal.toInt().toString()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(pName, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }

                // 品名入力欄
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("お酒の品名 (例: グラス赤ワイン)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // 純アルコール量入力欄
                item {
                    OutlinedTextField(
                        value = alcoholStr,
                        onValueChange = {
                            alcoholStr = it
                            val alc = it.toDoubleOrNull()
                            if (alc != null && caloriesStr.isBlank()) {
                                caloriesStr = (alc * 7.1).roundToInt().toString()
                            }
                        },
                        label = { Text("純アルコール量 (g) *必須") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }

                // 容量 & 度数からの計算補助
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "💡 容量と度数から自動計算:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = volumeStr,
                                    onValueChange = {
                                        volumeStr = it
                                        recalculateAlcoholFromVolumeAbv()
                                    },
                                    label = { Text("容量 (ml)") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = abvStr,
                                    onValueChange = {
                                        abvStr = it
                                        recalculateAlcoholFromVolumeAbv()
                                    },
                                    label = { Text("度数 (%)") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                // 推定カロリー入力欄
                item {
                    OutlinedTextField(
                        value = caloriesStr,
                        onValueChange = { caloriesStr = it },
                        label = { Text("推定カロリー (kcal)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.ifBlank { "カスタムのお酒" }
                    val finalCalories = caloriesStr.toDoubleOrNull() ?: ((alcoholVal * 7.1).roundToInt().toDouble())
                    onAdd(finalName, alcoholVal, finalCalories)
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
            ) {
                Text("記録する", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

