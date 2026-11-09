# 建置與測試

使用專案內的 Gradle wrapper，並設定 JAVA_HOME、ANDROID_HOME 或未追蹤的 local.properties。

## 工具鏈

| 元件 | 專案版本 |
| --- | --- |
| JDK | Eclipse Temurin 25.0.5+8 |
| Gradle | 9.6.0 |
| Android Gradle Plugin | 9.4.1 |
| Android 平台 | platforms;android-37.0 |
| Android Build Tools | 36.0.0 |
| minSdk / compileSdk / targetSdk | 31 / 37 / 37 |
| Kotlin Compose plugin | 2.2.10 |
| Lifecycle runtime | 2.10.0 |

## 建置

Windows PowerShell：

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
```

macOS／Linux 使用 `./gradlew` 執行相同任務。APK 位於 `app/build/outputs/apk/`，測試及 lint 報告位於 `app/build/reports/`。

Android 裝置測試須使用專用模擬器，並明確核對裝置目標，詳見 [模擬器驗證](emulator-testing.md)。`scripts/verify-background-clock.py` 是獨立的背景啟動回歸檢查，可透過 `--help` 查看必要參數。

本機環境檔、簽署金鑰、私人裝置資料與原始診斷紀錄不納入版本控制。
