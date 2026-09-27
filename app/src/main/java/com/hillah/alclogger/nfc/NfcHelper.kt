package com.hillah.alclogger.nfc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NfcHelper {

    const val URI_SCHEME = "alc-logger"
    const val URI_HOST = "drink"
    const val PARAM_SLOT = "slot"
    const val PACKAGE_NAME = "com.hillah.alclogger"

    /**
     * 指定スロット向けのURI文字列を生成 (例: alc-logger://drink?slot=1)
     */
    fun createSlotUri(slot: Int): String {
        return "$URI_SCHEME://$URI_HOST?$PARAM_SLOT=$slot"
    }

    /**
     * Intentからスロット番号 (1, 2, 3) をパース
     */
    fun parseSlotFromIntent(intent: Intent): Int? {
        // 1. Data URI からのパース (NDEF_DISCOVERED や Deep Link)
        val data: Uri? = intent.data
        if (data != null && data.scheme == URI_SCHEME && data.host == URI_HOST) {
            val slotParam = data.getQueryParameter(PARAM_SLOT)
            val slot = slotParam?.toIntOrNull()
            if (slot != null) return slot
        }

        // 2. NDEF Messages からのパース
        val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }

        rawMessages?.filterIsInstance<NdefMessage>()?.forEach { message ->
            for (record in message.records) {
                val uri = record.toUri()
                if (uri != null && uri.scheme == URI_SCHEME && uri.host == URI_HOST) {
                    val slot = uri.getQueryParameter(PARAM_SLOT)?.toIntOrNull()
                    if (slot != null) return slot
                }
            }
        }

        return null
    }

    /**
     * NFCタグにスロット用のURIレコードおよびAARを書き込む
     */
    suspend fun writeTag(tag: Tag, slot: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uriRecord = NdefRecord.createUri(Uri.parse(createSlotUri(slot)))
            val aarRecord = NdefRecord.createApplicationRecord(PACKAGE_NAME)
            val message = NdefMessage(arrayOf(uriRecord, aarRecord))

            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    return@withContext Result.failure(IllegalStateException("このNFCタグは書き込み禁止になっています。"))
                }
                if (ndef.maxSize < message.byteArrayLength) {
                    ndef.close()
                    return@withContext Result.failure(IllegalStateException("NFCタグの容量が不足しています。"))
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                Result.success(Unit)
            } else {
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(message)
                    formatable.close()
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("このタグはNDEF形式をサポートしていません。"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 成功時の触覚フィードバック（「ブルッ」と0.1秒振動）
     */
    fun triggerSuccessVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(100)
                }
            }
        } catch (e: Exception) {
            // 振動非対応端末などでのクラッシュを防止
        }
    }
}
