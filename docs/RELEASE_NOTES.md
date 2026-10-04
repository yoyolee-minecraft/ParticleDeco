Particle Deco 1.0.6

- 黃色框線不再永久顯示：方塊剛綁定或被刷子刷回等待填入後，60 秒內不拿工具也看得到黃色框線，之後只在手持粒子核心或刷子時顯示。
- 新設定 `outline.waitingShowSeconds`（預設 60）。`0` 代表只在手持工具時顯示，`-1` 代表永遠顯示（舊版行為）。
- 舊設定檔不用改：沒有這個欄位時用預設 60 秒；原本寫 `alwaysShowWaiting: false` 的會當成 `0`。
- 計時只存在記憶體，伺服器重開後等待中的方塊只在手持工具時顯示框線。

需求：Minecraft 26.2 以上、Fabric Loader 0.18.4 以上、Fabric API、Java 25。
