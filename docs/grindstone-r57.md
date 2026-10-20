# r57 奥术砂轮

用户定稿为“凸齿大块明暗＋内侧宽倒角＋低对比粗颗粒”，噪声开启。

砂轮保持原轴与石盖，外轮分为石材、秘银、石材三段。秘银中段厚 3.5 模型像素，固定绕同轴错开约 7.218211587890697°；按原轮最大半径计算，外缘最大顶点弦位移为 1 模型像素。三段使用同一个动态模型、转速和相位，原生相邻轮反转与磨削逻辑继续生效。

`RuntimeGrindstoneModels` 从玩家安装的 CEI 轮体读取轮廓，计算并集与差集，生成外轮和新露出的肩面，再选取原生轴、盖及物品形态的池体。世界静态池体保持原生父模型。两份 OBJ 使用 direct V，Forge OBJ 子模型设为 `flip_v:false`，材质图集与其 UV 成套生成。

`RuntimeGrindstoneTextures` 从安装的 Create、Iron 材质提取有限色阶，按本项目的粗格规则生成四张纹理；不复制依赖贴图的像素布局。模型、纹理均通过现有内存资源包提供，不向 JAR 加入派生 PNG、OBJ 或上游原生几何表。

普通 BER 与 Flywheel 使用同一个 `arcane_industry:block/arcane_grindstone` 模型。该模型在烘焙时显式注册；两个奥术方块实体共用自有 `PartialModel`。分液池 visual 使用 `neverSkipVanillaRender()`，保留 CEI 的物品与液面渲染；Create 父 renderer 在 Flywheel 启用时跳过轮体，所以不会重复绘制。关闭 Flywheel 时，现有分液池 renderer 的 `getRenderedBlockState()` 提供相同奥术轮体。

`tools/VerifyRuntimeGrindstone.java` 离线执行实际生成器，比较四张定稿纹理的 34,304 个 RGBA 像素、两份 composite JSON、两份 OBJ 共 752 个有序面与 MTL。原有纹理和模型生成器另按既有参考摘要回归检查。该 r57 接入批次按当时要求没有启动客户端、服务端或 GameTests；后续卷轴实测也不能替代砂轮在不同后端、轴向、物品及流体状态下的专项画面验收。最终发布构建的检查范围见 [0.1.0 发布验收](releases/0.1.0.md)。
