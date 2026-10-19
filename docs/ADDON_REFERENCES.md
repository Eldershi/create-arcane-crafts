# 铁魔法附属的接入参考

核对日期：2026-10-20。下列结论来自项目原作者仓库；不是对其所有代码和素材的全面许可审查。

## Farmer’s Spell ’n Spellbook

检查 `1.20.1-Forge` 分支提交 `819bfd335dec8ee46f2407e9477c05e1ae01b126`，该源码快照标记为1.0.3，不能当作本机1.0.6_2发布包的逐文件证明。

- [WheatSpellBook.java](https://github.com/GLDYM/Farmers-Spell-n-Spellbook/blob/819bfd335dec8ee46f2407e9477c05e1ae01b126/src/main/java/com/chenjdy/farmers_spell/item/curios/WheatSpellBook.java#L14) 继承 Iron 的 `SpellBook`。
- [wheat_book.json](https://github.com/GLDYM/Farmers-Spell-n-Spellbook/blob/819bfd335dec8ee46f2407e9477c05e1ae01b126/src/main/resources/assets/farmers_spell/models/item/wheat_book.json#L9) 引用 `irons_spellbooks:item/template_spell_book_model` 等父模型，纹理槽引用自身 `farmers_spell` 命名空间。
- [build.gradle](https://github.com/GLDYM/Farmers-Spell-n-Spellbook/blob/819bfd335dec8ee46f2407e9477c05e1ae01b126/build.gradle#L89) 将 Iron 作为独立依赖。
- 该快照 LICENSE / README 写 ARR，Gradle 属性却残留 MIT；不能仅按加载器显示的 MIT 处理其源码与资源。

这说明可以运行时继承现有模型，再配置自身纹理，无需将原父模型几何复制到附属内。命名空间属于该附属，并不单独证明纹理的原创性或授权情况。

## Iron’s Spells KubeJS

检查作者公开的 `2001` 分支：

- [CustomSpellBook.java](https://github.com/sentwayfarer/irons_spells_js/blob/2001/src/main/java/com/squoshi/irons_spells_js/item/CustomSpellBook.java) 继承 Iron 的 `SpellBook`，调用 `ISpellContainer`、`SpellRegistry`、`AffinityData` 等接口。
- [build.gradle](https://github.com/sentwayfarer/irons_spells_js/blob/2001/build.gradle) 从 Iron Maven 获取 API 与运行依赖；[mods.toml](https://github.com/sentwayfarer/irons_spells_js/blob/2001/src/main/resources/META-INF/mods.toml) 声明必需的 `irons_spellbooks`。
- 该附属自身采用 MIT；它的许可不会改变 Iron 原内容的权利。

可参考其依赖、继承和接口调用结构，不把该例子当作复制全部原实现的许可。

## 有单独发布许可声明的实例

[Iron’s Restrictions 作者 README](https://github.com/Relimer/Irons-Restrictions/blob/4879aeac7478658029ff1eb763f252cf8ea1547f/README.md#L59) 表示 Iron 允许其发布该附属。已查页面没有附授权原文、具体素材名单或转授权范围，因此仅能确认作者作出了这个声明。

## 本项目的后续处理方向

Iron [开发文档](https://iron.wiki/developers/) 介绍 API 和完整模组依赖；[3.16.3 许可](https://github.com/iron431/irons-spells-n-spellbooks/blob/v1.20.1-3.16.3/LICENSE.md) 允许自行编写依赖式附属。应分别处理依赖引用和源码／素材复制：

1. 保留依赖、注册ID、继承、公开API及运行时原模型引用。
2. 改装外观使用自身资源，或取得涉及文件的明确授权。
3. 将本地搬入的净化粒子实现改为直接调用原生Provider，可减少重复实现。
4. 法术书需保留现有翻页效果时，评估从已安装原模组运行时读取模型，避免内嵌派生几何。
5. 铁砧操作评估原菜单结果计算的薄适配，保留本项目的原液费用与事务校验，并验证副作用。

上述方向已用于本轮依赖式迁移；实现与离线验证见 MIGRATION.md。本地原始素材历史单独保留，不推送到公开仓库。
