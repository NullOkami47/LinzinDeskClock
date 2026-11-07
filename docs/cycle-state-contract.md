# 鎖定週期狀態契約

本次範圍：ClockCycleRecord、ClockCycleStore 與 ClockCycleStoreTest。Activity、Service、manifest 與執行期啟動條件由 parent 處理。

## 公開 API

原有 Context 建構子及下列 API 均保留原名稱、參數與回傳型別，沒有新增操作方法：

- noteLock(): Unit
- remainingWindowMs(): Long
- claimAttempt(): Boolean
- suppressed: Boolean（唯讀屬性）
- onUnlock(): Unit
- suppress(): Unit
- invalidateOffer(): Unit
- offer(): String
- consume(token: String?): Boolean

另提供可直接使用真實 adapter 的建構子：ClockCycleStore(prefs: SharedPreferences, elapsedRealtime: () -> Long, bootProvider: () -> Int, tokenGenerator: () -> String)。正式 Context 建構子仍使用 clock_cycle preferences、SystemClock.elapsedRealtime、Settings.Global.BOOT_COUNT 及 UUID。boot provider 拋出 RuntimeException（包含 SecurityException）時視為未知 boot，不接受授權。

## 週期與 token

| 操作／狀態 | 契約 |
| --- | --- |
| 全新資料 | 尚未 armed；單獨 noteLock 不會產生掃描視窗。 |
| onUnlock | 同步成功後才重新 armed，清除 attempted、suppressed、鎖定時間與整組 offer。 |
| noteLock | 只消耗一次 armed，保留第一次鎖定的 elapsed 時間。未知 boot 也消耗 armed，避免稍後重送事件開出新視窗。 |
| 5 秒視窗 | elapsed 差值必須小於 5,000 ms；恰好 5,000 ms 即拒絕。未知 boot、不同 boot、未記錄時間或未來的 lock_at 都回傳 0。 |
| claimAttempt | 必須仍在視窗內、未 attempted、未 suppressed，且 attempted=true 同步成功，才回傳 true。 |
| suppress | 設定 suppressed 與 attempted，清除 offer；成功寫入後可跨 wrapper／程序重建保留，直到觀察到 unlock。 |
| offer | 使用獨立的 30,000 ms token 時效，保留既有不直接要求 claimAttempt 的介面語義；執行期呼叫端仍負責啟動資格。新增持久化欄位 offer_boot。 |
| 拒絕發出 offer | suppressed、未知 boot、負 elapsed、deadline 溢位、空 token 或同步寫入失敗時回傳空字串。 |
| consume | token 非空、相符、未 suppressed、目前及發出時 boot 均有效且相同、deadline 尚未到達且距離不超過 30,000 ms，並同步清除成功，才回傳 true。 |
| 舊版／未來 offer | 缺少 offer_boot、未知 boot、跨 boot、到期或推算發出時間在未來的 token 都拒絕。 |
| 單次使用 | 包含錯誤／null token 在內，每次 consume 都嘗試清除整組 offer。讀取與同步寫入共用同一 preferences 鎖；先建立的兩個 wrapper 依序消費時最多一次成功。 |

清除 offer 會一併移除 offer、offer_until、offer_boot。每次操作都從 preferences 讀取目前 record，wrapper 不快取週期資料。

**Parent 必須將非空 token 當作整個 activation 的必要條件。** offer 回傳空字串時，通知與 host.enterLockedClock 都應跳過；只跳過通知會留下另一條啟動路徑。本次未修改這些呼叫端。

## 同步寫入失敗

commit 回傳 false 或拋出 RuntimeException 時，共用此 SharedPreferences 物件的 store wrapper 全部進入程序內封鎖。此時 suppressed=true、remainingWindowMs=0、claimAttempt=false、offer 為空字串、consume=false。一般寫入成功不會解除封鎖；只有一次成功的 onUnlock 才能解除。

測試同時涵蓋失敗發生在記憶體資料更新之前與之後，包含 false 及例外四種情況。這避免只靠讀回記憶體資料，或換一個 wrapper，就把失敗的持久化決策當成成功。

封鎖依賴同一程序內共用的 SharedPreferences 物件，不提供跨程序協調。commit 失敗時無法保證磁碟內容，程序死亡後也無法保證這個記憶體封鎖仍存在；成功 commit 後的持久化與「寫入失敗後跨程序仍安全」是不同保證。本測試未宣稱後者，也未模擬真實裝置斷電。

## 驗證證據

依 parent 要求，未啟動整合 Gradle，以免與執行期來源碼修改競爭。使用已快取的 Kotlin 2.2.10 編譯器、JDK 25、Android SDK android.jar 和 JUnit 4.13.2，僅編譯以下來源，再執行 JUnitCore：

- autostart/ClockCycleRecord.kt（GREEN 階段）
- autostart/ClockCycleStore.kt
- autostart/LockCyclePolicy.kt（原始 store 的依賴）
- autostart/ClockCycleStoreTest.kt
- layout/InMemorySharedPreferences.kt

先建立測試，確認缺少注入建構子；接著只加入建構子接縫，保留原始判斷與持久化行為，取得 **39 tests / 18 failures** 的行為 RED。修正後相同 39 項測試 **39/39 PASS**，不是只驗證純 policy 或 mock store。

本機證據目錄（D 槽）：

```text
D:/codex-tools/linzin-android/tmp/clock-cycle-store-20261108-1794069366780/
```

檔案：ClockCycleStore.original.kt、ClockCycleStore.seam-only.kt、red-api.log、red-behaviour.log、red-tests.log、green-compile.log、green-tests.log、verification.json（驗證來源雜湊）。編譯器僅有其於 JDK 25 使用 sun.misc.Unsafe 的棄用警告；來源碼編譯 exit 0，JUnit exit 0。

Parent 完成來源碼編輯後的整合指令：

```powershell
. ./scripts/Set-AndroidEnvironment.ps1
./gradlew.bat :app:testDebugUnitTest --tests 'com.linzin.linzindeskclock.autostart.ClockCycleStoreTest' --no-daemon --console=plain
```

這條 Gradle 指令在本次工作中尚未執行。獨立 JVM 測試不等同 Android 實機或執行期 activation 驗收。
