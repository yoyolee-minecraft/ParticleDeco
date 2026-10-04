Particle Deco 1.0.5

新增材料：

| 材料 | 粒子 |
| --- | --- |
| 旋風桿 | gust_emitter_small（小型風爆） |
| 史萊姆球 | item_slime |
| 石斧 | crit（暴擊） |
| 青金石 | enchanted_hit（附魔暴擊） |
| 石劍 | damage_indicator（傷害愛心） |
| 裝水的玻璃瓶 | falling_water（落下的水滴） |
| 白色羊毛 | cloud（雲） |
| 竹子 | sneeze（貓熊噴嚏） |
| 玻璃瓶 | witch（女巫的紫色粒子） |

- 裝水的玻璃瓶在原版是「藥水」物品，materials.json 新增 `potion` 條件，只有水瓶會對應到水滴，其他藥水維持藥水漩渦。
- 已存在的 materials.json 會在第一次啟動時自動補上這批新材料，已經被你設定過的物品不會被覆蓋。

需求：Minecraft 26.2 以上、Fabric Loader 0.18.4 以上、Fabric API、Java 25。
