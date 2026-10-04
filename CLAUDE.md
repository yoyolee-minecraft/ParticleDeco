# CLAUDE.md

## 專案
Particle Deco：Minecraft 26.2+ Fabric 伺服端模組，規格見 docs/SPEC.md。

## 硬性規則
- 原版客戶端必須能加入：不註冊方塊、物品、實體、資料元件、粒子類型、自訂封包。
- 自訂物品資料只用 minecraft:custom_data。
- 不引用任何 net.minecraft.client 套件的類別。
- fabric.mod.json 的 environment 為 "*"，只有 main 入口。

## 工作方式
- 使用任何 Minecraft 或 Fabric API 前，先執行 ./gradlew genSources 並在反編譯原始碼中確認類別、方法簽章，不要憑記憶。
  若開發環境無法連到 maven.fabricmc.net 或 Mojang，改用 GitHub Actions 的 api-probe 工作流程（.github/workflows/probe.yml）列印 javap 簽章。
- 依 docs/SPEC.md 第 7 節的里程碑順序開發。
- 每個里程碑結束時執行 ./gradlew build 與 ./gradlew runGameTest，全部通過才算完成。
- 新增相依前先確認該函式庫有對應 26.2 的版本，沒有就停下來回報。
- 資料類別使用 Codec 序列化，加上 dataVersion 欄位以利日後遷移。

## 常用指令
- ./gradlew build
- ./gradlew runServer
- ./gradlew runGameTest
