package com.hillah.alclogger

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.hillah.alclogger.data.healthconnect.HealthConnectManager
import com.hillah.alclogger.nfc.NfcHelper
import com.hillah.alclogger.ui.screens.MainScreen
import com.hillah.alclogger.ui.theme.AlcLoggerTheme
import com.hillah.alclogger.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private val viewModel: MainViewModel by viewModels()
    private val healthConnectManager by lazy { HealthConnectManager(this) }
    private var nfcAdapter: NfcAdapter? = null

    // Health Connect 権限リクエストランチャー
    private val requestPermissionActivityContract =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { grantedPermissions ->
            if (grantedPermissions.containsAll(healthConnectManager.permissions)) {
                viewModel.checkHealthConnectStatus()
            } else {
                Toast.makeText(this, "Health Connect の権限が許可されませんでした。", Toast.LENGTH_SHORT).show()
                viewModel.checkHealthConnectStatus()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            AlcLoggerTheme {
                MainScreen(
                    viewModel = viewModel,
                    onRequestPermissions = { requestHealthPermissions() }
                )
            }
        }

        // 起動時のIntent（タグかざしによるディスパッチ等）を処理
        handleNfcIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkHealthConnectStatus()
        enableNfcReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableNfcReaderMode()
    }

    private fun requestHealthPermissions() {
        requestPermissionActivityContract.launch(healthConnectManager.permissions)
    }

    /**
     * アプリ起動中のNFCリーダーモードを有効化
     */
    private fun enableNfcReaderMode() {
        val adapter = nfcAdapter ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        adapter.enableReaderMode(this, this, flags, null)
    }

    private fun disableNfcReaderMode() {
        nfcAdapter?.disableReaderMode(this)
    }

    /**
     * NfcAdapter.ReaderCallback: フォアグラウンドでNFCタグが検出されたとき
     */
    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return

        val pendingWriteSlot = viewModel.uiState.value.pendingWriteSlot
        if (pendingWriteSlot != null) {
            // NFCタグ書き込みモード
            lifecycleScope.launch {
                val result = NfcHelper.writeTag(tag, pendingWriteSlot)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        NfcHelper.triggerSuccessVibration(this@MainActivity)
                        viewModel.onTagWriteCompleted(true, "✨ スロット $pendingWriteSlot のタグ書き込みが完了しました！")
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "書き込みエラー"
                        viewModel.onTagWriteCompleted(false, "❌ 書き込み失敗: $error")
                    }
                }
            }
        } else {
            // NFCタグ読み取りモード
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                try {
                    ndef.connect()
                    val ndefMessage = ndef.ndefMessage
                    ndef.close()

                    var detectedSlot: Int? = null
                    if (ndefMessage != null) {
                        for (record in ndefMessage.records) {
                            val uri = record.toUri()
                            if (uri != null && uri.scheme == NfcHelper.URI_SCHEME && uri.host == NfcHelper.URI_HOST) {
                                detectedSlot = uri.getQueryParameter(NfcHelper.PARAM_SLOT)?.toIntOrNull()
                                if (detectedSlot != null) break
                            }
                        }
                    }

                    if (detectedSlot != null) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            viewModel.onNfcTagScanned(detectedSlot)
                        }
                    }
                } catch (e: Exception) {
                    // タグ読み取りエラー
                }
            }
        }
    }

    /**
     * IntentからのNFCディスパッチ処理 (アプリ未起動状態等からの起動)
     */
    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return

        val slot = NfcHelper.parseSlotFromIntent(intent)
        if (slot != null) {
            viewModel.onNfcTagScanned(slot)
        }
    }
}
