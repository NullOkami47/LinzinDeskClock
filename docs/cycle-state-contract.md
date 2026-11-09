# 鎖定週期狀態契約

ClockCycleRecord 與 ClockCycleStore 管理鎖定週期、啟動資格及退出抑制；Activity／Service 共用此契約。

## 公開 API

保留 Context 建構子，主要操作如下：

- noteLock(): Unit
- remainingWindowMs(): Long
- claimAttempt(): Boolean
- suppressed: Boolean（唯讀屬性）
- onUnlock(): Unit
- observeUnlocked(): Unit（被動觀察不清除退出抑制）
- onExplicitUserEntry(interactive: Boolean, keyguardLocked: Boolean): Unit（僅明確、已解鎖的互動入口恢復監看）
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

**非空 token 是整個啟動流程的必要條件。** offer 回傳空字串時，通知與 host.enterLockedClock 都應跳過，避免留下未授權的啟動路徑。

## 同步寫入失敗

commit 回傳 false 或拋出 RuntimeException 時，共用此 SharedPreferences 物件的 store wrapper 全部進入程序內封鎖。此時 suppressed=true、remainingWindowMs=0、claimAttempt=false、offer 為空字串、consume=false。一般寫入成功不會解除封鎖；只有一次成功的 onUnlock 才能解除。

測試同時涵蓋失敗發生在記憶體資料更新之前與之後，包含 false 及例外四種情況。這避免只靠讀回記憶體資料，或換一個 wrapper，就把失敗的持久化決策當成成功。

封鎖依賴同一程序內共用的 SharedPreferences 物件，不提供跨程序協調。commit 失敗時無法保證磁碟內容，程序死亡後也無法保證這個記憶體封鎖仍存在；成功 commit 後的持久化與「寫入失敗後跨程序仍安全」是不同保證。本測試未宣稱後者，也未模擬真實裝置斷電。

## 驗證

ClockCycleStoreTest 使用真實儲存 adapter 與可注入的時鐘、boot 及 token 來源，驗證期限邊界、單次消費、跨 wrapper、跨 boot、持久化失敗及明確使用者入口。整合驗證另外確認 host 清理及同週期抑制，詳見 [模擬器驗證](emulator-testing.md)。
