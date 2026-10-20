# 机械动力：奥术工艺

Create: Arcane Crafts · Minecraft Java Edition 1.20.1 · Forge 47.4.10

**非 Minecraft 官方产品，未经 Mojang 或 Microsoft 批准，也与其无关联。**

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

将机械动力与铁魔法连接起来的独立附属 Mod。维护者：[Eldershi](https://github.com/Eldershi)；反馈入口：[GitHub Issues](https://github.com/Eldershi/create-arcane-crafts/issues)。本项目也不是 Create 或其他依赖模组开发团队的官方作品。

## 功能

- **奥术升华室**：手动／动力卷轴升级、书内选定法术升级、超越升级，以及六类原生奥术铁砧操作。
- **随机研习**：每次消耗空白卷轴、学派引导物各 1 件及 500 mB 原液，生成真实 1 级卷轴；约 8 秒结晶揭示，史诗／传说有延迟涡流，传说增加卢恩字符。
- **工业加工**：卷轴打印与注墨、奥术／猩红原液、墨水、源质及压缩块。
- **奥术砂轮与分液池**：保留 CEI 原生行为，叠加法术／法球干磨和湿磨回收。
- **奥术舱口与补给**：瓶桶流体交易、背包奥术补给、JEI 配方展示。

当前版本为 **[0.1.0](https://github.com/Eldershi/create-arcane-crafts/releases/tag/v0.1.0)**，功能与数值仍会继续迭代。内部命名空间保持 `arcane_industry`，原有方块、物品和存档标识保持。

当前工作区包含发行后的交互修复，详见 [未发布更新](CHANGELOG.md)；不代表已发布的 0.1.0 二进制已变更。升华室由锻造台消耗升华模板、烈焰人燃烧室和奥术铁砧合成，开工无需另装模板。主副物品各限 1 件，空手右键取下准星所指的物品；法术书滚轮选术、中键确认。

本轮构建、服务端四套检查、原生书本连续升级及 BSL 对照结果见 [后续修复验收](docs/verification/post-0.1.0-2026-10-20.md)。交互协议为 2，客户端与专服需要同步更新。

## 构建

需要 JDK 17 和首次构建下载依赖的网络连接。仓库包含官方 Gradle 8.8 Wrapper，使用 ForgeGradle 6.0.42：

```sh
./gradlew clean build
```

Windows 使用 `gradlew.bat clean build`。生产 JAR 位于 `build/libs/create-arcane-crafts-0.1.0.jar`；源码 JAR 单独生成。`build` 自动检查动作／音效／抽取不变量、Mixin/refmap、生产映射和发行内容，不会启动游戏。正式 JAR 不包含原型测试指令；隔离验收包使用 `./gradlew prototypeJar` 单独构建。

GitHub Actions 在每次推送和拉取请求中从干净检出执行构建，并保存生产包、源码包及验收报告。安装时将生产 JAR 放入 Forge 实例的 `mods` 目录，并另行安装下列固定依赖及其必需依赖。

主要固定依赖：

| 组件 | 版本 |
| --- | --- |
| Minecraft / Forge | 1.20.1 / 47.4.10 |
| Create | 6.0.8（开发制品 6.0.8-289） |
| Create Enchantment Industry | 2.5.4b |
| Create: Dragons Plus | 1.11.9 |
| Iron’s Spells ’n Spellbooks | 1.20.1-3.16.3 |
| Sophisticated Backpacks | 1.20.1-3.26.9.2194 |
| Sophisticated Core | 1.20.1-1.5.6.2383 |
| JEI | 15.59.0.212 |

完整开发依赖及其固定发布 ID 见 `gradle.properties`、`build.gradle`。运行时需要另行安装这些模组及其依赖；本项目不内嵌它们的 JAR。铁魔法 3.16.3 被明确固定，因为铁砧适配使用该版本的接口与私有字段；升级该依赖须同步复核。

`build.py` 保留为原研发工作区的离线构建入口，依赖已有映射缓存和本机启动元数据；普通克隆应使用 Gradle。

## 附属接入与外观

- 待机净化粒子沿用铁魔法的 Provider；卷轴揭示的结晶、亮剪影和完成粒子使用同步的方块实体渲染。
- 原生铁砧操作通过隔离菜单调用原 Mod 计算结果，本项目保留原液费用、输入锁定与原子交付；额外校验写槽结果，防止重复法术导致吞材料。
- 法术书在运行时读取已安装铁魔法的模型，生成现有开合、翻页和厚度所需的关节网格。
- 79 张派生／程序贴图、16 份动画声明和 13 份模型资源由内存资源包从玩家已安装依赖读取并变换；仓库和 JAR 中不带这些上游素材副本。
- 保留 8 张本项目原创纹理；眉毛为独立程序笔画，不需要额外的 Minecraft 1.21 游戏资源。

纹理 ID、几何、UV、动画参数与现有外观对应；运行时资源包默认处于较低优先级，用户启用的外观资源包仍可覆盖它。

奥术砂轮采用已定稿的 r57 三段错齿模型：秘银中段厚 3.5 模型像素，绕同轴固定错开约 7.218°，外缘最大位移为 1 模型像素。普通渲染与 Flywheel 共用轮体；分液池保留原生物品和液面渲染。

## 验证与研发状态

本次迁移完成 Java 17 编译、标准生产构建、接口／注入目标检查和离线视觉数据比对：75 张纹理的 218,112 个 RGBA 像素一致；13 册书的 26,592 个浮点值逐位一致；6 份模型 JSON 及库存 98 个四边形的材质、顶点、UV、法线一致。

此前资源迁移阶段没有启动游戏；后续卷轴揭示实装已完成隔离服务器测试和无光影客户端画面核对，包括配色、遮挡、资源重载与完整归位。记录见下方卷轴接入文档。原型功能与美术仍会继续迭代。

- [运行时资源实现和离线验证](docs/runtime-assets.md)
- [开发经验、维护约束与验收命令](docs/DEVELOPMENT.md)
- [发布验收清单](docs/RELEASE_CHECKLIST.md)
- [全部功能逐项测试清单（操作与预期）](docs/FUNCTIONAL_ACCEPTANCE.md)
- [0.1.0 验收记录](docs/releases/0.1.0.md)与[更新记录](CHANGELOG.md)
- [r57 奥术砂轮定稿与渲染接入](docs/grindstone-r57.md)
- [卷轴揭示、待机浮动与原生光效接入](docs/scroll-reveal-integration.md)
- [迁移与验证记录](docs/MIGRATION.md)
- [其他铁魔法附属的接入参考](docs/ADDON_REFERENCES.md)

## 来源和许可

本项目遵循依赖式附属的组织方式；自有代码、上游代码适配与素材引用分别说明，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。[LICENSE.md](LICENSE.md) 保留自有部分的现有权利声明，标记的 CEI 派生文件适用 LGPL-3.0-or-later，Create 代码片段保留 MIT；上游完整许可在 `LICENSES/`。

Minecraft 官方要求见 [EULA](https://www.minecraft.net/en-us/eula) 与 [Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)。本仓库不分发游戏客户端、服务器、依赖模组 JAR、存档、账号数据或原研发目录的受限历史。
