# 第三方来源与许可记录

项目：机械动力：奥术工艺（Create: Arcane Crafts）  
核对日期：2026-10-20

本项目为非 Minecraft 官方产品，未经 Mojang 或 Microsoft 批准，也与其无关联。上游模组的名称仅用于说明依赖、兼容与来源；本项目不代表这些模组的开发团队。

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

## Minecraft 官方规则

参见 [Minecraft EULA](https://www.minecraft.net/en-us/eula) 的 USING mods，以及 [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) 的 Essential guidelines 与 Extended functionality and modifications。

公开分发范围应为独立 Mod；不包含游戏客户端、服务端、修改后的游戏整体或其他模组的依赖 JAR。本项目维护者为 Eldershi，项目反馈入口为 [GitHub Issues](https://github.com/Eldershi/create-arcane-crafts/issues)。Minecraft 官方的非官方声明不替代各上游作品的许可证要求。

## 运行依赖和许可来源

| 上游 | 当前研发版本 | 许可与来源 |
| --- | --- | --- |
| Create | 6.0.8 | [代码 MIT，assets 目录 ARR](https://github.com/Creators-of-Create/Create/blob/mc1.20.1-6.0.8/LICENSE.md)，版权 The Create Team / The Creators of Create |
| Create Enchantment Industry | 2.5.4b | [LGPL-3.0-or-later](https://github.com/DragonsPlusMinecraft/CreateEnchantmentIndustry/blob/ee0c8f521255f51b94d893a2410329d28f1de525/LICENSE.txt)，DragonsPlusMinecraft 及贡献者 |
| Iron’s Spells ’n Spellbooks | 1.20.1-3.16.3 | [All Rights Reserved；明确允许自己编写依赖它的 addon](https://github.com/iron431/irons-spells-n-spellbooks/blob/v1.20.1-3.16.3/LICENSE.md)，iron431 / Redspace 及贡献者 |
| Sophisticated Backpacks | 1.20.1-3.26.9.2194 | [1.20.x 官方许可，All Rights Reserved](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/1.20.x/LICENSE.md)，P3pp3rF1y 及贡献者 |
| Sophisticated Core | 1.20.1-1.5.6.2383 | [1.20.x 官方许可，All Rights Reserved](https://github.com/P3pp3rF1y/SophisticatedCore/blob/1.20.x/LICENSE.md)，P3pp3rF1y 及贡献者 |
| MinecraftForge | 1.20.1-47.4.10 | [1.20.x LGPL 2.1 与附加说明](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/LICENSE.txt)；普通 Java Mod 依赖不使本项目全部代码自动采用 LGPL |
| Minecraft Java Edition | 1.20.1 | Mojang / Microsoft，适用官方 EULA 和 Usage Guidelines |
| Minecraft Breeze 外观参考 | 1.21.1 | 本地眉毛 alpha 轮廓参考；适用 Mojang / Microsoft 规则 |

完整上游许可文本与来源校验在 `LICENSES/`。Sophisticated 和 Forge 收录的是官方对应维护分支许可，不把它们冒称为精确发布标签的快照。收录许可与来源声明本身不构成 ARR 内容的再分发授权。

## 源码接入方式

大多数接入使用依赖、继承、公开 API、注册表、标签及 Mixin：砂轮沿用 CEI 的处理实现，打印注册自有 provider，舱口实现独立能力代理，背包补给通过依赖提供的接口工作。仓库没有复制上游原包名下的 API 类，也不内嵌依赖 JAR。

以下本地实现另有明确来源，不能全部描述为纯 API 引用：

| 本地文件（`src/main/java/dev/expedition/arcane/` 下） | 来源与修改 |
| --- | --- |
| `client/AscensionChamberRenderer.java` | 参考并改造 Create 6.0.8 的 `BlazeBurnerRenderer.renderShared`、`BlazeBurnerBlockEntity.tickAnimation` 相位、位移与追踪；保留 Create MIT 版权与许可，新增自有外观、书本与加工动画 |
| `client/AscensionCleanseParticle.java` | 改写 Iron 3.16.3 `CleanseParticle` 的运动、逐帧、亮度与尺寸实现；公开分发处理需另行核对，未取得特别授权 |
| `chamber/ManualUpgradeRecipes.java` | 对照 Iron 3.16.3 `ArcaneAnvilMenu.createResult` 实现六类原生操作；新增事务、校验和原液费用；需区分依赖调用与原实现移植，未取得特别授权 |
| `client/AnimatedSpellBookMeshes.java` | 包含由 Iron 法术书 JSON 转换的 13 组模型几何与 UV；源贴图通过原命名空间引用；派生几何仍需作为资源来源处理 |
| `client/AnimatedSpellBook.java` | 自定义书本动画渲染，铰链布局参考 Minecraft 1.20.1 `BookModel`；资源 ID 指向原模组 |
| `grinding/ArcaneGrindstoneDrainEntity.java` | 局部容量检查和槽号 3000 协议适配 CEI `ProcessingInventory`；原生私有加工仍委托上游 |
| `grinding/ArcaneGrindstoneItem.java` | 放置方向与替换行为适配 CEI `MechanicalGrindStoneItem` |

## 当前本地资源的来源

以下为完整本地原型的来源记录，不表示这些文件均已获准公开分发。

| 资源家族 | 来源及处理 |
| --- | --- |
| 升华室烈焰人、眉毛、容器、模板、水动画 | Minecraft 原生材质或轮廓改造；部分眉毛 alpha 参考 1.21.1 Breeze |
| 铜／黄铜笼架与底座 | Create 6.0.8 原材质的像素取样、映射与模型接入 |
| 两色源质块、舱口、余烬蛋糕 | CEI 2.5.4b `super_experience_block`、`experience_hatch`、`experience_cake` 等改色；动画元数据沿用来源；适用上游许可 |
| 猩红源质、卷轴、净化粒子、血色／秘银配色 | Iron 3.16.3 原质、卷轴、净化粒子等纹理的轮廓或像素改造；Iron 许可不授予这些 assets 的再分发权限 |
| 背包奥术补给升级图标 | Sophisticated Backpacks 3.26.9.2194 `upgrade_base` 边框与 alpha 派生 |
| 五档墨水动画 | Forge 47.4.10 `milk_still` / `.mcmeta` 改色 |
| 砂轮与分液池模型 | 当前通过 parent 引用 CEI 原生模型，未采用已否决的秘银外轮候选 |
| 音效 | `sounds.json` 引用 Minecraft / Iron 原生资源 ID，未复制音频文件 |

本地源项目 `art/texture-manifest.json`、视觉选择记录与生成／安装脚本保留了资源出处。公开副本的实际包含范围应以其 README 和发布清单为准；本地完整 Git 历史不直接推送到公开仓库。
