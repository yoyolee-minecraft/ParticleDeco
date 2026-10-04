Particle Deco 1.0.2

修正與改進：

- 營火炊煙（campfire_cosy_smoke）與狼煙（campfire_signal_smoke）現在會像原版營火一樣往上飄。原因是原版營火生成煙時帶有 0.07 的上升速度，之前的封包沒有給速度，煙就停在原地。materials.json 新增選填欄位 `motion`，這兩種煙沒寫時自動使用原版速度，舊的設定檔不必修改。
- 花、草、竹子等有隨機模型偏移的植物，框線與粒子起點現在會跟著偏移，不再和方塊錯開。
- 新增設定 `outline.alwaysShowWaiting`（預設 true）。設為 false 時，等待填入的黃色框線也只在手持核心或刷子時顯示。

需求：Minecraft 26.2 以上、Fabric Loader 0.18.4 以上、Fabric API、Java 25。
