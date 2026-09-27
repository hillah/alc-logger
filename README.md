# alc-logger 🍺
> **NFC連動 お酒カウント ➔ Health Connect 記録Androidアプリ**

晩酌の際にコースターや卓上に貼った **NFCタグ** にスマホを「ピッ」とかざすだけで、1杯飲むたびに自動で **Androidのヘルスコネクト（Health Connect）** へ飲酒・栄養データ（純アルコール量/カロリー）を書き込むAndroidアプリです。

Garmin Connectなどの各種ヘルスケアツールと飲酒データを一元管理し、睡眠スコアやストレスレベルとの相関分析に役立てることができます。

---

## 🌟 主な特徴

- **0.1秒の超低摩擦記録**:
  - NFCタグにかざした瞬間に即座に触覚フィードバック（バイブレーション振動）＋Health Connect書き込み。
  - アプリ未起動時（ロック解除状態）でも `NDEF_DISCOVERED` ディスパッチによって自動起動・記録。
- **Health Connect ネイティブ連携**:
  - 自前DBに閉じず、Health Connect の `NutritionRecord` に直接記録。
  - レコード名に純アルコール量（g）メタデータを付与し、推定エネルギー（kcal）および食事タイプ（`MEAL_TYPE_UNKNOWN`）で保存。
- **よく飲むお酒に対応する3つのスロット**:
  - **スロット1**: ビール（例: 350ml 5.0% / 純アルコール 14.0g / 140kcal）
  - **スロット2**: ハイボール（例: 350ml 7.0% / 純アルコール 19.6g / 175kcal）
  - **スロット3**: 日本酒/ワイン（例: 180ml 15.0% / 純アルコール 21.6g / 185kcal）
  - スロットの内容はアプリ内設定から自由にカスタマイズ可能（純アルコール量は自動算出）。
- **タグ書き込み機能（初期設定）**:
  - 市販の新品NFCタグ（NTAG213 / NTAG215 等）を本アプリ専用タグとして初期化できる書き込み機能を標準搭載。
- **安心の取り消し・手動記録機能**:
  - 「直前の1杯を取り消す」ボタンで誤かざし時もHealth Connectから直前レコードを即削除。
  - タグがない外出先でもワンタップで記録できる手動追加ボタン。

---

## 🛠 技術スタック

- **プラットフォーム**: Android (minSdk: 28, targetSdk: 35)
- **言語**: Kotlin 2.1.0
- **UI**: Jetpack Compose + Material 3
- **ヘルスケア連携**: `androidx.health.connect:connect-client:1.1.0-alpha11`
- **NFC**: Android NFC API (`Ndef`, `NdefFormatable`, `ReaderMode`, Deep Link: `alc-logger://drink?slot=X`)
- **データ永続化**: Jetpack DataStore Preferences
- **アーキテクチャ**: MVVM (ViewModel + StateFlow + Coroutines)

---

## 📱 画面構成・使い方

1. **初回起動 & 権限許可**:
   - アプリ起動時に「Health Connect 権限が必要です」と表示されます。「許可」をタップして栄養データの読み取り・書き込みを許可します。
2. **NFCタグの初期化（コースター等への貼り付け）**:
   - 画面右上のNFCアイコン（`Nfc`）をタップ。
   - スロット1〜3から書き込みたいお酒を選択し、新品のNFCタグにスマホをかざすだけで書き込み完了。
3. **日常の記録**:
   - コースターにかざすだけで「ブルッ」と振動し、自動で1杯記録されます。
   - アプリのサマリカードに「🍺 X 杯」「純アルコール量 (g)」「推定カロリー (kcal)」が即時反映されます。
4. **設定カスタマイズ**:
   - 画面右上の歯車アイコン（`Settings`）から、お酒の名前、度数(%)、容量(ml)、カロリー(kcal)をいつでも変更できます。

---

## 🏗 ビルド方法

```bash
# デバッグAPKのビルド
./gradlew assembleDebug

# 生成されるAPK
app/build/outputs/apk/debug/app-debug.apk
```
