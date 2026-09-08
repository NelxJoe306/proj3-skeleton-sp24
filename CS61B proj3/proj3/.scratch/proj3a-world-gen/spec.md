Status: ready-for-agent
Feature: proj3a-world-gen

# proj3A 世界生成（BYOW 基座）spec

> 术语遵循 `CONTEXT.md`；本 spec 遵守 ADR-0001（结构层/表现层分离）与 ADR-0002（存档 = seed + 运行时快照）。

## Problem Statement

用户在完成 Berkeley CS61B sp24 proj3（BYOW）的 3A 部分，交付物必须通过课程 autograder 与人工评审。长线目标是把这个世界生成器长成一个「找目标闯关」的 Java + web 游戏客户端 demo（要好看的主界面与游戏内美术，并加入机关、昼夜/天气、多存档槽等独特设计）——但那些都属于后续里程碑。眼下要解决的是：**先写出一份正确的、seed 确定性的 structure layer**，它既是 3A 的验收对象，又是后续所有玩法/美术长上去时不会被推翻的地基。

## Solution

3A 交付一个结构层世界生成器：

- 解析 `seed string`（`N<数字>S`，不区分大小写）→ 用它驱动全部结构随机性 → 返回一张 `TETile[][]`（即 world）。
- 布局用 **BSP 递归分区**：把网格递归切成子区域，每个叶子放一个矩形 room，相邻区域之间用 1 格宽 corridor 相连；刻意包含转弯走廊。
- 只产出 **structure layer**，用规范 `Tileset`（WALL / FLOOR / NOTHING…），守死 3A 判分点：全连通、coverage >50%、≥20% 世界含转弯走廊、无死胡同、边界无地板。
- **确定性是不变量**：同 seed ⇒ 同 world，永不掺入时间/表现状态（ADR-0002 的基石）。
- 暴露给外界的只有一个缝 `AutograderBuddy.getWorldFromInput`；生成逻辑内部拆分，但都是私有实现。
- 提供一个 StdDraw 目检入口（渲染一帧给人看），仅用于开发确认，不进 autograder 路径。

## User Stories

1. 作为学生，我希望 `getWorldFromInput` 能解析规范 seed 字符串（`N####S`，大小写均可），这样 autograder 能调它拿到世界。
2. 作为学生，我希望同一个 seed 每次生成**完全相同**的 world，这样 autograder 的确定性测试能过，也为将来「存档 = seed」打底。
3. 作为学生，我希望不同 seed 生成**明显不同**的世界（房间数量/位置/尺寸随机），这样人工评审的随机性标准能过。
4. 作为玩家（未来），我希望世界是 room 与 corridor 组成的可导航结构，这样我能在其中走动寻找 objective。
5. 作为人工评审，我希望世界包含若干**矩形房间**，且 floor 与 wall 与 void 一眼可辨，这样我可以确认生成质量。
6. 作为人工评审，我希望至少 20% 的世界里存在**带转弯的 corridor**，这样走廊不会只是横平竖直的直线。
7. 作为人工评审，我希望 corridor 宽 1 格、且**没有死胡同**，这样不会有走进去出不来的死路。
8. 作为玩家（未来），我希望所有 room 通过 corridor 全连通，这样世界没有走不到的孤立区域。
9. 作为人工评审，我希望覆盖的地板面积 >50%，这样世界足够饱满、不像半成品。
10. 作为人工评审，我希望世界边界上没有 floor，这样生成的地图边缘是干净的。
11. 作为学生，我希望 `getWorldFromInput` 只算结构、**不渲染**（headless 可跑），这样 autograder 调用不会被图形环境卡住。
12. 作为学生，我希望内部结构生成与 tile 表现解耦（ADR-0001），这样 3A 用规范 tile 过审，将来换主题美术不影响结构。
13. 作为开发（未来），我希望生成器把 room / corridor 结构能追溯出来，这样机关（实体层）与 objective 能落在有意义的房间里。
14. 作为玩家（未来），我希望同一个 seed 在"再开一局"时能重现同一张图，这样存档/换局体验是连贯的。
15. 作为学生，我希望世界尺寸固定（seed 只改内容不改尺寸），这样后续 web 端逻辑网格 + 相机最容易对接。
16. 作为学生，我希望本地有一个能**用眼睛看到**生成结果的入口，这样我能快速判断生成质量，而不是只靠断言。
17. 作为学生，我希望不把昼夜/天气/主题这些**表现层**状态混进 seed 结构，这样它们以后加在渲染侧也不会破坏确定性。
18. 作为学生，我希望 3A 代码不依赖任何 web/Spark 组件，这样核心生成在纯 Java 里可独立验证、可测试。

## Implementation Decisions

- **入口/职责**：`World` 负责从 seed 构建出结构层 world；`AutograderBuddy.getWorldFromInput` 只做「解析 seed 字符串 → 交给 World → 返回 `TETile[][]`」，路径上不做任何渲染。
- **随机源**：所有结构随机性由一个以 seed 为种子的 RNG（用骨架提供的 `RandomUtils`）驱动；无其它随机源。seed 解析对大小写不敏感，数字部分为种子。
- **布局算法（BSP）**：矩形画布按随机分割点递归二分，直到叶子尺寸落在目标区间；每片叶子生成一个矩形 room；对相邻区域树做连接，corridor 宽恒为 1；**刻意让部分连接走 L 形（含一个转弯）**以满足转弯占比；每条 corridor 都两端接住 room/区域，杜绝死胡同 spur。
- **边界与覆盖率**：room 与画布边界之间保留墙/void 边距，杜绝边界地板；分区/填充比例调参以满足 coverage >50%。
- **世界规模**：固定尺寸（默认 80×80，常量可调）；seed 只决定内容。world 坐标语义以 `TERenderer` 的渲染约定为准（实现期从代码确认，不臆测）。
- **表现**：结构层只使用规范 `Tileset` 常量；不加自定义 tile、不加装饰、不加主题（ADR-0001）。
- **内部结构（不新增外部缝）**：可拆 room/hallway 值对象与 BSP 生成逻辑为独立类，但都是 `World` 之后的私有实现，autograder/测试一律只经 `getWorldFromInput`。
- **目检入口**：`Main.main` 渲染一帧生成结果供人工看（复用 StdDraw/TERenderer 现成机制）。

## Testing Decisions

好的测试只测**外部行为**：缝是唯一的 `getWorldFromInput`，读返回的 `TETile[][]` 断言，不探内部生成器细节。

- **确定性**：同一输入调用两次，逐格比较两个 world，必须全等。
- **可变性**：不同 seed 的 world 彼此不同（且随机性体现在房间数量/布局，而非只差一格）。
- **结构不变量**（对返回网格做分析，本地代理人工评审判分点）：
  - floor 覆盖率 >50%；
  - 所有 floor 从任一 floor 出发 flood-fill 全连通；
  - 边界一圈无 floor；
  - 存在 room 状地板块、wall 与 floor 与 void 三者可区分；
  - 抽样 seed 中 ≥20% 的世界含转弯 corridor、无死胡同 corridor（通过网格拓扑启发式断言，若启发式对 corridor 判定不稳，退而在实现层用一次性校验脚本核对，不放进常跑单测）。
- **无渲染副作用**：`getWorldFromInput` 在无图形上下文下可被调用且只返回网格。
- **视觉 smoke check**：`Main.main` / StdDraw 渲染，人工目检（对照现有 `WorldGenTests` 的渲染模式）。

测试先例：骨架自带 `proj3/tests/WorldGenTests.java`（对固定 seed 调 `getWorldFromInput` 并渲染）是唯一现成参照；本 spec 的断言测试为新增，测试代码不打包进交付。

## Out of Scope

- **3B**：玩家移动/交互、菜单输入流、键盘控制。
- **表现层与美术**：自定义 tile 画风、更好看的主界面、游戏内美术、昼夜/天气渲染。
- **独特设计系统**：机关/实体层、昼夜与天气状态机、多存档槽与持久化（本 spec 只落实"seed ⇒ 同结构"这一地基）。
- **web 客户端**：Spark 后端、浏览器 Canvas、gson 序列化等一切 web 接线。
- **非 3A 的世界变体**：seed 改变尺寸、多世界、大地图分块。
- 课程设计文档 / 报告撰写。

## Further Notes

- 北向愿景（本 spec 不实现）：「找目标闯关」玩法 + Java 引擎（Spark）出数据 + 浏览器当游戏客户端 + 更好看的主界面与游戏内美术 + 机关 / 昼夜天气 / 多存档槽。届时 structure layer 原样复用，theme 全部走表现层叠加。
- 骨架现状：`core.World`、`core.Main` 为空壳；`AutograderBuddy.getWorldFromInput` 抛异常待填；`Tileset`/`RandomUtils`/`TERenderer`/`FileUtils` 已提供；参考测试 `WorldGenTests` 渲染固定 seed。
- 验收锚点：`AutograderBuddy.getWorldFromInput("n<seed>s")`（小写 `n…s` 与 spec 大写 `N…S` 都必须可用）。
