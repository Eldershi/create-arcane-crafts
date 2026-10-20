# 开发与验收

本文说明 0.1.0 的实现边界、维护经验和复核方法。功能入口见 [README](../README.md)，版本变化见 [CHANGELOG](../CHANGELOG.md)。[原型研发记录](DEVELOPMENT_HISTORY.md) 保留历次方案，涉及当前行为时以源码与本指南为准。

## 构建环境

使用 JDK 17、仓库内的 Gradle 8.8 Wrapper 和固定的 ForgeGradle 6.0.42。依赖版本与发布 ID 在 `gradle.properties`，仓库在 `build.gradle`；首次构建需要下载依赖。

```sh
java -version
./gradlew clean build
```

Windows 使用 `gradlew.bat clean build`。生产产物为 `build/libs/create-arcane-crafts-0.1.0.jar`，源码包单独生成。`check` 同时执行 `verifyReleaseInvariants` 和 `verifyReleaseJar`：前者运行纯 Java 动作、音效与研究抽取不变量，后者检查 Mixin 清单、refmap、原生铁砧桥接的生产映射及发行内容；编译通过并不能代替真实服务器和客户端验收。

可以单独执行 `./gradlew verifyReleaseInvariants`。构建报告写入 `build/reports/release-invariants.json` 与 `build/reports/release-jar.json`；它们是当前构建生成的证据，不作为源码静态文件伪造 PASS。

生产 JAR 排除原型验收夹具和截图请求入口。需要运行下文的游戏内原型检查时，另行构建带 `prototype` classifier 的测试包：

```sh
./gradlew prototypeJar
```

正式包和原型包使用同一 mod ID，隔离实例只能安装其中一份。原型包与 `-Darcane.prototype=true` 仅用于验收，不能替代正式包发布。

`build.py`、测试实例安装及启动脚本来自原型工作区，依赖本机已有映射、游戏资源与启动元数据。它们用于维护原型证据，不是普通克隆的标准构建前提。不要把开发机缓存目录、游戏安装路径或账号配置写入发布资源。

## 服务端事务与客户端时钟

`chamber/AscensionChamberBlockEntity.java` 持有物品、流体、加工计划和结果。客户端只能呈现已同步的状态，不抽取法术、不扣费、不提交配方。

普通手动工艺保留 70 tick。随机研习单独使用 161 tick：开工时一次抽取结果，同时支付一张空白卷轴和 500 mB 实体原液，把输入、引导物、结果、品级、原液类型及视觉种子保存到事务中。引导物可以复用。抽取失败不能先扣输入，暂停或读档不能再次抽取、再次支付。

结果的持有关系只有一条路径：待交付事务 → 结果槽 → 玩家或自动化。完成后保留的输入／输出快照只供渲染，不能被作为第二件可掉落物。修改拆机、提取或存档逻辑时，需要同时检查这三个状态。

研究进度按实际付费工作 tick 前进。缺模板、工作盆占用或输出堵塞时停止；同步的 `researchVisualRunning` 和采样时刻控制客户端插值，暂停时不能继续按墙钟推演。恢复、区块重载和晚加入必须沿用同一个结果与种子。不要仅靠 `gameTime - startedAt` 假设加工一直运行。

书内选术包需要由服务端复核距离、视线、真实槽位、输入版本与锁定状态。客户端浮层的选中值不是提交依据。使用原生奥术铁砧时，`compat/NativeAnvilBridge.java` 在隔离菜单和物品副本上计算结果，再验证真实写槽效果，最后由升华室统一扣费和交付；不要调用原生 `onTake` 去重复操作玩家物品。

## JSON 配方与网络配方有不同表示

曾经出现“离线构建成功，客户端登录断线”的问题。Create 的 `FluidTagIngredient` 在服务端 JSON 阶段持有 TagKey；经过原生网络解码后，它只保留展开的流体列表，TagKey 可以为空。对这个客户端对象调用 JSON `serialize()` 会失败。

维护 `compat/ScrollInkingRecipe.java` 时保持两层验证：

- 读取 JSON 时验证声明的 `printing_inks` 标签或原生墨水，以及 250 mB 用量；标签尚未绑定时不能要求它已经展开。
- 读取网络时验证用量和网络表示，保留服务端认可的标签展开结果，包括空标签和数据包新增成员。直接流体仍须属于原生墨水，不能放宽为任意流体。

JEI 也会在真实配方同步后构造显示副本，因此不能用 JSON 往返克隆网络配方。`client/jei/NativeAssemblyDisplays.java` 用 Create 原生 `toNetwork` / `fromNetwork` 深克隆装配，再修改显示副本。每个副本都要独立拥有可变步骤、物品与流体原料列表；结果及中间物直接复制 ItemStack，保留精确 NBT 数字类型。不要为显示某个品级修改 RecipeManager 内的实际配方。

网络回归应从**已经网络解码的真实配方管理器**开始，第二次克隆后比较原管理器字节、各副本隔离、严格 NBT 和目标墨水。只检查服务端 JSON 或单个手工配方不足以发现这类问题。

## 特效渲染与光效

卷轴揭示由 `client/ChamberScrollRevealRenderer.java` 在方块实体渲染中完成，使用同步进度和固定种子。动画数学在 `ScrollRevealMotion.java`，普通工艺归位与待机在 `ChamberItemMotion.java`，音效游标在 `ChamberResearchSoundPlan.java`。

保留以下约束：

- 晶体布局、大小、色差和粒子轨迹由事务种子决定；不在逐帧绘制中调用随机数。晶体沿物品透明轮廓从下往上蔓延，不额外画边线；六面明暗、晶体间色差及连续双色噪声共同表现体积。
- 输入／输出读取当前物品模型、颜色、UV 与透明覆盖。几何只在缓存首次建立时采样，资源重载后重建，不逐帧读回贴图。
- 主体与材料的结晶、粉尘、亮剪影、完成涡流共用时钟，避免独立粒子生命周期在卸载、暂停或连续升级时脱节。史诗开启延迟涡流，传说再增加卢恩；低品级没有这层完成特效。
- 固定数量限制每台机器的工作量；减少／最少粒子设置削减可选装饰。绘制函数只写顶点，不修改机器数据，不主动 flush 全局缓冲。
- 收尾继续缓慢翻动与微浮，亮度和摆幅逐渐减弱，再接入绝对时钟的 ±0.008 格、4.8 秒待机浮动。连续升级不能停在高处或重新抖动。

自发光、局部光晕和世界照明是三个不同机制：

| 机制 | 0.1.0 的实现 | 维护边界 |
| --- | --- | --- |
| 明亮核心 | 原版 eyes shader，不受场景光照压暗；普通 alpha 混合 | 不等于屏幕 Bloom |
| 少量局部光晕 | 深度测试的局部几何，SRC_ALPHA + ONE 混合 | 不替换全局后处理；不能穿墙 |
| 场景照明 | 升华室方块原有光照等级 | 晶体不会向光照引擎注入彩色光源 |

`ChamberGlowRenderTypes.java` 明确使用 LEQUAL 深度测试、仅写颜色及四边形排序。不要用关闭深度测试或穿透轮廓制造“发光”。原版 eyes 的 ONE + ONE 混合也不能仅靠降低 alpha 正确退光，因此局部光晕另用 SRC_ALPHA + ONE。

`LightTexture.FULL_BRIGHT` 同时指定天空光与方块光的最大 UV；单写 `240` 不是同一 packed light 值。FULL_BRIGHT 不会照亮周边方块。无光影环境下通过清晰色差和主体噪声表现晶体，不承诺预览中的屏幕模糊 Bloom。

已实测基线是 Fancy、Flywheel instancing、无第三方光影。Fabulous、第三方光影以及复杂水／玻璃透明排序仍需各自验证。高分辨率资源包保留原 UV 细节，但当前轮廓采样为 16×16；没有可用 baked quads 的自定义物品会回退原生渲染，不能获得完整像素消散。

更详细的渲染状态和原生 API 依据见 [光效审计](reveal-glow-audit.md)，具体接入结果见 [卷轴揭示接入](scroll-reveal-integration.md)。

## 运行时资源与依赖升级

依赖模组的纹理、模型和声音通过玩家安装的资源读取，不复制进仓库或 JAR。当前内存资源包生成 79 张派生／程序 PNG、16 份动画声明和 13 份模型资源；发布包保留 8 张原创 PNG。用户资源包可以覆盖生成资源，重载时重新建立贴图、模型和渲染缓存。

生成规则位于 `assets/arcane_industry/runtime/texture_transforms.json` 和 `client/assets/`。维护时修改取色、裁切、几何变换等规则，不嵌入压缩图片、完整 texel 布局或上游模型副本。砂轮共用同一轮体与 Flywheel partial，分液池仍由原生渲染器绘制物品和液面。

`tools/VerifyRuntimeTextures.java`、`VerifyRuntimeModels.java`、`VerifyRuntimeGrindstone.java` 调用真实生成器，需另行提供合法安装的依赖资源。引用哈希与许可见 [运行时资源说明](runtime-assets.md)、[第三方声明](../THIRD_PARTY_NOTICES.md)。验证派生像素一致不代表其上游素材可以重新分发。

铁魔法的原生铁砧适配固定 3.16.3。升级 Iron、Create 或 CEI 后需要重新核查菜单字段、Mixin 目标、配方网络表示、资源路径和模型布局，重新跑生产映射、整包登录与客户端重载检查；仅调整 `versionRange` 不足以证明兼容。

## 验收层次

| 层次 | 主要检查 | 能证明的范围 |
| --- | --- | --- |
| 标准生产构建 | JDK 17 编译、纯 Java 不变量、重混淆、Mixin/refmap、JAR 内容 | 代码与发布包结构，不证明画面或联网 |
| 无界面数学／音效 | 收尾连续性、有限数值、品级、暂停、晚观察、回退 | 轨迹与事件，不证明 GPU 外观或听感 |
| 真实服务器 | 付费一次、161 tick、暂停、90/160 tick 重载、提取与拆机 | 权属、存档与真实原生对象行为 |
| 真实配方同步／JEI | 整包编解码、空／扩展标签、独立深克隆、严格 NBT | 登录路径与显示副本隔离 |
| 原生客户端 | 日夜配色、遮挡、归位、品级差异、资源重载 | 记录过的渲染配置；不自动覆盖其他光影 |

不需要启动 Minecraft 的音效回归可直接执行：

```sh
mkdir -p build/sound-check
javac --release 17 -d build/sound-check \
  src/main/java/dev/expedition/arcane/client/ChamberResearchSoundPlan.java \
  tools/VerifyChamberSounds.java
java -cp build/sound-check dev.expedition.arcane.client.VerifyChamberSounds
```

`tools/verify-chamber-motion.cjs` 与 `tools/verify_scroll_reveal_layout.py` 对照原先批准的 WebGL 代码；它们需要本地 `art/` 基准，后者还需要原型离线编译类路径。`art/` 不随公开仓库分发，因此这两项是原型证据复核工具，不能列作普通克隆必过的前置检查。

安装 `prototypeJar` 测试包并启用服务端 JVM 参数 `-Darcane.prototype=true` 后，管理员可在**一次性测试世界**执行：

```text
arcane_recipe_network_test
arcane_research_test
arcane_chamber_test
```

这三项在游戏目录分别写出 `recipe-network-results.json`、`research-presentation-results.json` 和 `chamber-regression-results.json`。研究与升华室检查会在固定区域放置真实装置、触发原生配方和雷击等游戏逻辑，应使用隔离实例。JEI 克隆及渲染验证还需要真实客户端登录，不能由服务器命令替代。

0.1.0 的原型接入证据记录了 47 项原有工艺回归、蓝／红研究的唯一交付、3,229 个配方和 17 个装配步骤的真实登录包、108 个印刷及 1,510 个升华显示副本，以及 Fancy 无光影客户端的日夜、遮挡和重载检查。这些数量来自当时固定依赖与配置；数据包或法术配置变化后以新生成结果为准。原始截图、测试存档和依赖 JAR 均留在本地，不进入发布包。

发布时将构建结果、验收配置、明确未覆盖的项及产物 SHA-256 一起保存，每个测试结果都要注明实际使用的制品。0.1.0 的原始完整视觉检查使用了当时的集成验收构建；最终发布阶段另将 QA 类剥离到 `prototypeJar`，因此旧截图不能当作新生产包哈希的截图。原型包的服务器回归、正式包的启动烟测和早期完整视觉检查应分别记录，不能互相替代。临时观察模组、调试截图钩子和账号／启动数据不能混入发行物。

最终发布范围与制品对应关系见 [0.1.0 发布验收](releases/0.1.0.md) 和 [机器可读验收记录](releases/0.1.0-verification.json)。
