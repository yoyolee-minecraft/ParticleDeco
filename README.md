# Particle Deco

Minecraft Java 26.2 的 Fabric 伺服端模組。玩家在生存模式用原版物品當材料，把持續播放的粒子效果綁在建築方塊上。只需要裝在伺服器，原版客戶端可以直接加入並完成所有操作。單人遊戲（整合伺服器）也能使用。

完整規格見 [docs/SPEC.md](docs/SPEC.md)。

## 安裝

- Minecraft 26.2 以上、Fabric Loader 0.19.5 以上、Fabric API、Java 25
- 把 `particledeco-<版本>.jar` 放進伺服器的 `mods/`。伺服端 GUI 函式庫 sgui 已內嵌在 jar 裡，不需要另外安裝。

## 玩法

| 步驟 | 操作 |
| --- | --- |
| 取得粒子核心 | 在鐵砧把紫水晶碎片命名為「粒子核心」。取出的物品帶有光澤，即為核心。 |
| 綁定 | 手持核心右鍵方塊，消耗 1 個核心，方塊出現黃色發光框線（等待填入）。 |
| 填入 | 拿材料對照表內的物品右鍵等待填入的方塊，扣 1 個物品並開始播放。此時不會放下營火，也不會打開箱子。 |
| 調整 | 手持核心右鍵已綁定的方塊，開啟設定介面（形狀、大小、數量、間隔、擴散、紅石模式、染料顏色、1/16 格偏移）。 |
| 刷除 | 刷子右鍵播放中的方塊，存入的材料掉在玩家面前，回到等待填入。 |
| 解除 | 挖掉方塊。方塊照原版掉落，另外掉落存入的材料；核心不歸還，創造模式不額外掉落。 |

方塊被爆炸、活塞、火燒、流水等非玩家因素移除時，排程器會在下次播放前發現方塊種類不同，自動解除並把材料掉在原位。只比對方塊種類，因此開門、熔爐點燃、作物成長都不會解除。

框線：等待填入的方塊一律顯示黃色框線；播放中的方塊只在玩家手持核心或刷子時顯示青色框線。框線是只送給該玩家的假 `block_display` 封包，伺服器沒有實體。RenderShape 不是 MODEL 的方塊改用 `item_display`，沒有對應物品的方塊改用 8 個角落的粒子標示。

## 指令

| 指令 | 權限 | 說明 |
| --- | --- | --- |
| `/pdeco toggle` | 所有人 | 個人開關，關閉後不再收到裝飾粒子 |
| `/pdeco near [radius]` | 所有人 | 列出附近發射點的座標、狀態與粒子 |
| `/pdeco remove <x y z>` | OP 2 | 解除指定方塊的綁定，存入物品掉落 |
| `/pdeco purge <radius>` | OP 2 | 批次解除 |
| `/pdeco give core [player] [count]` | OP 2 | 直接給予核心 |
| `/pdeco reload` | OP 2 | 重新載入設定檔與材料對照表 |
| `/pdeco stats` | OP 2 | 發射點數量、每 tick 封包數、延後數量、耗時 |

## 設定檔

第一次啟動會在 `config/particledeco/` 產生兩個檔案。格式錯誤時會記錄警告並改用預設值，不會讓伺服器崩潰。

`config.json`

```json
{
  "core": { "item": "minecraft:amethyst_shard", "anvilName": "粒子核心", "recipeMode": false },
  "brushDurabilityCost": 0,
  "consumeMaterial": true,
  "bindBlacklist": ["minecraft:suspicious_sand", "minecraft:suspicious_gravel"],
  "outline": { "waitingColor": "#FFD54A", "playingColor": "#4AD9FF", "showDistance": 16 },
  "viewDistance": 32,
  "limits": { "perChunk": 32, "maxCountPerEmit": 8, "globalPacketsPerTick": 400, "perPlayerPacketsPerTick": 60 },
  "language": "zh_tw"
}
```

- `recipeMode`: 設為 `true` 時停用鐵砧轉換，改用合成配方（紫水晶碎片 + 螢石粉 + 紅石粉，無序合成）。配方以資料包條件載入，修改後需要執行原版 `/reload`。
- `language`: `zh_tw` 或 `en_us`。所有訊息由伺服器直接送出文字，客戶端不需要語言檔。

`materials.json` 是材料對照表，每筆可以用 `items` 列出物品 ID 或 `#標籤`，`particle` 指定原版粒子。需要參數的粒子（例如 `geyser_base`）可加上 `options`，內容與原版粒子指令的參數相同：

```json
{ "items": ["minecraft:magma_block"], "particle": "minecraft:geyser_base", "rarity": "medium",
  "options": { "water_blocks": 3, "burst_impulse_base": 0.5 } }
```

## 與規格書不同或需要說明的地方

- 發射點原點：粒子從方塊頂面中心（方塊座標 + 0.5, 1.0, 0.5）往外算偏移。規格寫「預設偏移 0」，若以方塊中心為原點，實心方塊會把粒子擋住，所以改用頂面。
- 假實體 ID：從原版的實體計數器取號（與 Polymer 的做法相同），不會和真實實體衝突。規格建議從 `Integer.MAX_VALUE` 往下配發，但取用原版計數器更安全。
- 26.2 中箱子的 RenderShape 是 MODEL，因此箱子依規格規則使用 `block_display` 框線；`item_display` 只用在仍由方塊實體渲染、RenderShape 不是 MODEL 的方塊。
- 鐵砧轉換在結果欄產生時寫入標記，玩家在取出前就能看到光澤，取出時仍需支付原版的經驗等級。
- spiral 形狀與 26.3 支援屬於 M6 選配，這個版本沒有實作。

## 開發

```bash
./gradlew build          # 建置 jar（build/libs/）
./gradlew runGameTest    # 執行 GameTest
./gradlew runServer      # 開發用伺服器
```

GameTest 位於 `src/gametest/`，涵蓋綁定、填入、刷除、挖除與掉落、創造模式、非玩家移除、方塊狀態改變、鐵砧轉換、Codec 存檔往返、框線可見性、封包預算延後、設定檔錯誤回退、指令，以及 500 個發射點 + 5 名玩家的耗時檢查。

## 授權

MIT
