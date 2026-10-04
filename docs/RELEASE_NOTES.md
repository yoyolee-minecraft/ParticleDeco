Particle Deco 1.0.0：Minecraft 26.2 Fabric 伺服端粒子裝飾模組的第一個版本。

需求：Minecraft 26.2 以上、Fabric Loader 0.19.5 以上、Fabric API、Java 25。只需安裝在伺服器（或單人遊戲），原版客戶端可直接加入。sgui 已內嵌。

內容（規格書里程碑 M0 到 M5）：

- 粒子核心：鐵砧把紫水晶碎片命名為「粒子核心」，或在設定檔改用合成配方
- 綁定、填入、設定介面、刷子刷除、挖掉解除，以及爆炸、活塞等非玩家移除的自動解除
- 預設 56 種粒子材料對照（共 73 個物品與標籤），可在 `config/particledeco/materials.json` 修改並以 `/pdeco reload` 套用
- 形狀 point、column、ring、area，1/16 格偏移，紅石模式，染料顏色
- 只送給特定玩家的發光框線（block_display，方塊實體類方塊改用 item_display）
- 區塊上限、全域與每位玩家的封包預算（超出延後到下一 tick），`/pdeco stats`
- 個人開關 `/pdeco toggle` 與管理指令

測試：20 個 GameTest 在 CI 中全部通過，包含 500 個發射點加 5 名玩家時排程器每 tick 耗時低於 1 ms 的檢查。

尚未在真實的原版客戶端上手動遊玩驗證，框線顏色與距離等手感參數請依實際遊玩調整。
