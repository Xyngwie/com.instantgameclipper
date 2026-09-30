# InstantGameClipper

Android 向けのインスタントリプレイ（直近クリップ）アプリ。

ゲーム画面と内部音声をバックグラウンドで最大40秒分メモリに保持し、ボタン一つで直近30秒を MP4 として保存する。

- パッケージ: `com.instantgameclipper`
- 言語: Kotlin
- minSdk 29 / targetSdk 34
- リポジトリ: https://github.com/Xyngwie/com.instantgameclipper

## 今の状態

Phase 1〜5 のソースは入っている。ランチャーアイコンも設定済み。

まだ **APK はビルドできない**。Gradle Wrapper と実機ビルド用の PC（または GitHub Codespaces）が必要。

## 何をするアプリか

1. 画面収録の許可を取る
2. Foreground Service が裏で動き続ける
3. 画面は H.264、内部音声は AAC でエンコードする
4. エンコード済みパケットはディスクに書かず、RAM のリングバッファに最大40秒置く
5. CLIP ボタン（または設定画面の保存）で直近30秒をキーフレームから切って MP4 にする
6. 保存先は `Movies/InstantGameClipper/`
7. 保存後に共有シートを出す

再エンコードはしない。MediaMuxer でパケットを容器に入れるだけ。

## 権限

| 権限 | 用途 |
| --- | --- |
| 画面収録 (MediaProjection) | 画面キャプチャ |
| 通知 (POST_NOTIFICATIONS) | 常駐通知 |
| オーバーレイ (SYSTEM_ALERT_WINDOW) | ゲーム上の CLIP ボタン |
| マイク (RECORD_AUDIO) | 内部音声（AudioPlaybackCapture） |

Android 14 では、画面収録の同意を取ってから `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` のサービスを起動する。逆は不可。

## フェーズ

| Phase | 内容 | 状態 |
| --- | --- | --- |
| 1 | 権限と Foreground Service | 完了 |
| 2 | MediaCodec + 40秒メモリバッファ | 完了 |
| 3 | 30秒を MP4 に mux | 完了 |
| 4 | フローティング CLIP ボタン | 完了 |
| 5 | 内部音声 AAC | 完了 |

## 主なファイル
app/src/main/java/com/instantgameclipper/
ui/MainActivity.kt              権限チェックと開始/停止
service/ClipperForegroundService.kt
overlay/ClipOverlay.kt          浮かぶ CLIP ボタン
capture/ScreenCaptureSession.kt VirtualDisplay
capture/InternalAudioCapture.kt 内部音声
codec/VideoEncoder.kt           H.264
codec/AudioEncoder.kt           AAC
codec/MemoryRingBuffer.kt       RAM 40秒
exporter/ClipExporter.kt        MediaMuxer → MediaStore
## まだ無いもの

- `gradlew` / Gradle Wrapper
- 実機テスト
- Play ストア用の署名
- 画面回転や解像度変更への追従

## あとでビルドするとき

PC または Codespaces で Android Studio / Gradle が使えるようになったら:

1. このリポジトリを clone する
2. Gradle Wrapper を生成する
3. 実機またはエミュレータで Run

起動直後に保存するとバッファが空なので失敗する。数秒待ってから CLIP する。

## 注意

このアプリは画面とゲーム音を録る。使う側の端末で許可した範囲だけが対象。他人の画面を無断で撮る用途ではない。
