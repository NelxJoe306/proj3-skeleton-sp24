# CONTEXT — proj3 游戏世界（BYOW 基座）

骨架来自 Berkeley CS61B sp24 proj3（BYOW）；目标是把 3A 世界生成长成一个「找目标闯关」的 Java + web 游戏客户端 demo。3A 交付物必须通过课程 autograder / 人工评审。

## Glossary

- **world** — 从一个 seed 生成的整个可玩区域：一张固定尺寸的矩形 tile 网格（`TETile[][]`）。结构完全由 seed 决定（同 seed ⇒ 同 world）。
- **seed string** — 传给 `AutograderBuddy.getWorldFromInput` 的 `"N<数字>S"` 输入，是结构随机性的**唯一**来源；解析不区分大小写。
- **room** — 一块被墙围起来的矩形地板区，是可导航空间的基本单元。
- **corridor / hallway** — 连接房间/区域的 1 格宽通道；可以转弯；**不允许死胡同**。
- **coverage** — 网格中被地板填充的比例；3A 要求 >50%。
- **connectivity** — 所有 room 之间经由 corridor 互相可达；3A 要求无缺口、无死胡同、边界上无地板。
- **structure layer** — seed 确定性地产出的东西：room、corridor、wall/floor/void。**3A 的范围**。
- **presentation / theme layer** — 结构被*如何呈现*：墙/地板选用哪个 tile、装饰、昼夜、天气。叠在 structure 之上；**不属于 3A**。
- **entity / mechanism seam** — 未来铺在网格之上的**运行时对象**层：玩家、目标物、机关（门/踏板…）。不是 structure。
- **runtime state** — 不由 seed 推出的可变状态：玩家位置、被触发的门、时刻、天气、进度。**属于存档**。
- **save slot** — `seed` + 一份 runtime state 快照。world 从不整图存储，而是由 seed 重建。
- **objective** — 闯关玩法里玩家要寻找的目标/出口（未来）。

## 关键区分

- **结构**（seed → 完全确定）vs **运行时状态**（可变，随存档走）——两者永不混写。
- **structure layer**（3A，规范 tile）vs **presentation layer**（主题/氛围，后加）——theme 永远不反向影响结构。
