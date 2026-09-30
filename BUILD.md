# ビルド手順（PC または Codespaces が必要）

このリポジトリにはソースはあるが、まだ Gradle Wrapper（gradlew）が無い。

## 用意するもの

- Android Studio Hedgehog 以降、または JDK 17
- Android SDK (compileSdk 34)
- 実機（USBデバッグ）またはエミュレータ

## 手順

1. リポジトリを clone する
2. Android Studio でフォルダを Open する
3. Wrapper が無ければ Studio が生成を提案する。または:

```bash
gradle wrapper --gradle-version 8.7
実機を接続して Run
アプリで通知・オーバーレイ・内部音声・画面収録を許可してから「開始」
数秒待って CLIP、または「直近30秒を保存」
保存先: 端末の Movies/InstantGameClipper/
よくある失敗
画面収録の許可前にサービスを起こす → Android 14 で落ちる
起動してすぐ保存する → バッファが空
オーバーレイ未許可 → CLIP ボタンが出ない
RECORD_AUDIO 未許可 → 映像のみ（音声なし）でも保存はできる
