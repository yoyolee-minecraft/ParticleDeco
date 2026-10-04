Particle Deco 1.0.3

新增：

- 營火炊煙與狼煙改用原版營火的隨機節奏。原版營火每 tick 有 11% 機率冒煙，每次 2 到 3 顆，位置在中心水平 ±1/3 格、往上 0 到 2 格的範圍內。現在發射點照同樣規則出煙，間隔不再固定，看起來和真的營火一樣。平均出煙量與先前相近（每 tick 約 0.275 顆）。
- materials.json 新增選填欄位 `pattern`，可以讓任何粒子使用隨機節奏，或寫 `"pattern": false` 改回固定間隔。舊的設定檔不必修改。

需求：Minecraft 26.2 以上、Fabric Loader 0.18.4 以上、Fabric API、Java 25。
