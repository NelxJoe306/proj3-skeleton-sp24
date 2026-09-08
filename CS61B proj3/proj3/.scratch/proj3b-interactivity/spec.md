Status: ready-for-agent
Feature: proj3b-interactivity

# proj3B 交互输入（BYOW runtime world）spec

> 术语遵循 `CONTEXT.md`；遵守 ADR-0001（结构/表现分离）、ADR-0002（存档 = seed + 运行时快照）、ADR-0003（runtime world 零随机）。

## Problem Statement

用户在完成 Berkeley CS61B sp24 proj3（BYOW）的 3B 部分。3A 已交付 seed 确定性的 structure layer，判分网全绿。3B 的验收在课程侧只有一处：**headless 的 `getWorldFromInput`** 必须把输入字符串当作一段「N…S 建世界 + 一串按键」来模拟——同 seed + 同按键必须复现同世界、异 seed/异按键必须产生异世界、**中间穿插存档(:Q)与读档(L)仍必须回到同一个世界**；且 spec 要求存档连 RNG 状态一起还原、只写 `.txt`、不 `System.exit`。3A 时代的 `getWorldFromInput` 只认 `N…S` 前缀、把 S 之后全忽略——现在要把 S 之后的字符真正解释成一轮一轮的操作。本 spec 覆盖这条交互内核及其唯一公开缝，不涉及主菜单/美术/表现层（那些在 web 里程碑）。

## Solution

3B 在 structure layer 之上引入 **runtime world 层**（CONTEXT 的 entity seam）：`getWorldFromInput` 收到整条输入串后，交给一个**逐 turn 消费 command stream** 的交互内核执行，最后返回当前 runtime world 的网格快照。

- 输入串：`N<seed>S` 或 `L` 开头（大小写均可）。`N…S` 用 seed 重建结构世界；`L` 从存档读档（seed + 快照重建）。`N…S` 或 `L` 之后的字符是 command stream，每个可识别字符 = 一个 turn。
- 移动：`W/A/S/D` = 上/左/下/右（`TETile[x][y]` 中 W:+y、A:−x、S:−y、D:+x——与 `TERenderer` 屏幕 y 向上一致）。撞上 WALL 或世界边缘外 NOTHING：**不动、不报错**。
- avatar：S 一结束（或读档完成）即出现在**第一个生成房间的中心**；`@` 压入返回网格，移走后该格还原为脚下 tile（FLOOR）。出生点与一切移动都是纯确定函数（ADR-0003：零随机）。
- 存档/读档：`:Q`（大小写均可）**立即存档并终止**，忽略其后的所有字符；存档写固定 `.txt`，`V1` 格式（见 Implementation Decisions）；读档时跳过未知行、遇到版本号高于当前支持 ⇒ 视为无档。`L` 但无档：返回**全 NOTHING 的 80×80 空网格**，不报错。
- 文法宽容：未知字符（含 `:` 后非 Q 的字母）**静默忽略**，不报错；坏 seed 前缀仍按 3A 抛 IAE。
- 3A 回归是硬门槛：`@` 落在房间中心 ⇒ 不切断连通、不制造死胡同、coverage 损失 ≤ 1 格，3A 判分套件必须保持全绿。
- 目检：`Main` 提供脚本步进渲染（逐命令画一帧），只用于人眼确认，不进 autograder 路径。

## User Stories

1. 作为 autograder，我希望 `getWorldFromInput` 能把 `N…S` 之后的 W/A/S/D 当作移动逐格执行，这样"同 seed + 同按键 ⇒ 同世界"能成立。
2. 作为 autograder，我希望 `getWorldFromInput` 对同一输入串重复调用返回**逐格全等**的网格，这样 3B 的确定性测试能过。
3. 作为 autograder，我希望异 seed / 异按键序列产生**不同**的最终网格（avatar 落点或结构不同），这样可变性测试能过。
4. 作为 autograder，我希望 `N<seed>SDDD:Q` 与先 `:Q` 存档再 `L` + 剩余按键能得到**同一个最终网格**，这样"存档不影响世界"的等价测试能过（如 `N999SDDD:Q` → `LWWWDDD` 应等于 `N999SDDDWWWDDD`）。
5. 作为 autograder，我希望重复 `L:Q / L:Q` 循环往返读档仍回到同一世界，这样往返存档测试能过。
6. 作为玩家，我希望一个可见的 avatar（`@`）能被 WASD 在世界里移动，这样游戏可玩。
7. 作为玩家，我希望 avatar 撞墙或撞界**停住但不崩溃**，这样手感是"被挡住"而不是报错。
8. 作为玩家，我希望 avatar 走过之后脚下恢复原 tile（地板），这样不会在地上拖出痕迹。
9. 作为玩家，我希望同一个 seed 新开一局 avatar 总在同一处出生，这样重玩体验稳定。
10. 作为学生，我希望 `:Q` 存档、重启按 `L` 读档后 avatar 回到**存档时的位置**，这样进度不丢。
11. 作为学生，我希望存档只含 `seed + avatar 快照`且为 `.txt`，这样满足课程"只写 .txt、结构由 seed 重建"（ADR-0002）。
12. 作为学生，我希望读档连"随机数状态"一起还原——由于运行时零随机（ADR-0003），这一步被平凡满足，不用存 RNG 内部状态。
13. 作为学生，我希望 `L` 但没有任何存档时安静返回空世界、不抛错，这样 spec 的"缺档直接退出"语义成立。
14. 作为学生，我希望输入串里出现我不认识的字符时**静默忽略**、不中断，这样对 autograder 只会喂合法串的假设是安全的。
15. 作为学生，我希望 `N…S` 前缀解析与 3A 完全一致（坏 seed 仍抛 IAE、大小写等价、超大数字拒收），这样 3A 的入参测试继续过。
16. 作为学生，我希望纯 seed 输入（`N<seed>S` 无按键）也返回**盖着出生点 @ 的网格**，这样"返回世界 = 建完世界那一刻的运行时快照"语义统一。
17. 作为开发者（未来），我希望 3B 只动 runtime 层与公开缝的语义，`WorldGenerator` / 结构不变量不改，这样 3A 判分网可原样复用。
18. 作为开发者（未来），我希望未来加一个新键只碰「字符分发 → 一行动作」，不动内核其它部分，这样扩展有缝可循（Q12 契约）。
19. 作为开发者（未来），我希望存档格式带版本头且**跳过未知行**，这样未来加字段（机关/进度）不破坏旧档。
20. 作为开发者（未来），我希望 3B 代码 headless、不依赖 StdDraw，这样同一内核将来能喂给 web/Spark 客户端（输入来源无关）。
21. 作为人工目检者，我希望 `Main` 能用一串输入**逐帧演一遍**移动/存档，这样我能肉眼确认而不是只靠断言。
22. 作为学生，我希望 3B **不引入 win/lose、门/机关、多槽**，这样本里程碑保持"最小可判分"，那些留给已预留的 seam。

## Implementation Decisions

- **公开缝不变（唯一）**：`AutograderBuddy.getWorldFromInput(String)`。它的契约扩展为「`N<seed>S…` 或 `L…` 整条串 → runtime world 快照网格」。`isGroundTile` / `isBoundaryTile` 沿用 3A（AVATAR 已算 ground，无需改）。测试一律只经此缝。
- **交互内核（内部新模块，非公开缝）**：在 `core` 内新增一个消费 command stream 的运行时类型：持有「结构网格（seed 重建）+ avatar 当前位置」；每 turn 依据字符改变 avatar；提供 `snapshot()` 返回盖好 `@` 的网格。公开缝只做「解析前缀 → 建/读 → 逐字符喂内核 → 返回 snapshot」的薄编排，路径上无渲染、无 `System.exit`。
- **出生点**：取**第一个生成的房间中心**（房间列表的顺序与 seed 唯一对应，故纯确定）。房间 ≥5×5 ⇒ 中心是 interior floor、被 floor 环绕 ⇒ 挪一格做 `@` 不切断房间连通。
- **移动语义（TETile[x][y]）**：W 令 y+1、A 令 x−1、S 令 y−1、D 令 x+1；目标格为 FLOOR ⇒ 移动并盖 `@`（旧格还原 FLOOR）；目标为 WALL 或越界 ⇒ 原地不动（越界视同 NOTHING）。FLOOR 之外一律不可走（本里程碑只有 FLOOR/WALL/NOTHING/AVATAR 四种 tile 出现在返回网格）。
- **Grammar（最小集）**：前缀 `N`+数字+`S` 或 `L`；随后 `W/A/S/D`（大小写均可）移动、`:Q`/`:q` 存档并截断后续、其它任何字符**静默忽略**（`:`+非 Q 也忽略）。L 前缀但无档 ⇒ 全 NOTHING 空网格。坏 N…S 前缀沿用 3A `parseSeed` 抛 IAE（`L` 之外必须以 N 开头，`InputValidationTest` 语义不变）。
- **存档格式（`.txt`，固定文件名如 `save.txt`，进程 CWD 相对，经 `FileUtils`）**：
  ```
  V1
  seed=<long>
  avatar=<x>,<y>
  ```
  读档按行解析：`seed=` 重建结构世界、`avatar=` 放回位置；**跳过不认识的键**；`V1` 之外或版本号更高 ⇒ 视为无档（空世界）。多存档槽是未来上层「选文件名」的事，不碰此格式。
- **确定性不变量**：同 seed + 同 command stream ⇒ 同结构、同 avatar 轨迹、同存档。运行时**零随机**（ADR-0003）⇒ 无需存储 RNG 状态，spec 的还原要求被平凡满足；`:Q` 是纯副作用——返回网格与不存档的等长输入完全相同。
- **坐标/朝向锚点**：屏幕"上" = 数组第二维增大（`TERenderer.draw(x, y)` 配 StdDraw y 向上）⇒ W 对应 y+1；已在 spec 期锁定，目检与判分共用同一定义。
- **目检入口**：`Main` 扩展为可吃**完整输入串**并逐命令渲染一帧（脚本步进，非实时键盘）；纯 seed 渲染旧行为保留。属开发用，不进 autograder 路径。

## Testing Decisions

好的测试只测**外部行为**：唯一公开缝是 `getWorldFromInput`，读返回的 `TETile[][]` 断言；测试**绝不**触碰交互内核内部（缝 = A：单一黑盒）。这是本 spec 与 3A 共同的地基，也保证判分与自测读同一个行为面、零漂移。

- **确定性与可变性**（对照 `DeterminismSkeletonTest` 既有先例）：同 seed+同按键串重复调用逐格相等；异 seed 或异按键串结果不同（avatar 落点不同）。
- **移动**：`n<seed>swww` 相对 `n<seed>s` 的返回网格里，avatar 出现在起点上方 3 格（向上=数组第二维增大）；撞墙/撞界输入后 avatar 原地不动、不抛错、网格只差在后续未移动的按键上。
- **出生与还原**：`n<seed>s` 网格含恰一个 `@`，且它在某个 interior floor 上；`n<seed>swd`（折返）后 avatar 移过的格恢复 FLOOR。
- **存档/读档等价**（sp24 3B grader 的判分形状，本地代理）：
  - `getWorldFromInput("n<seed>sDDD:Q")` 返回网格 == `getWorldFromInput("n<seed>sDDD")`（`:Q` 纯副作用）；
  - `L`+剩余按键 等价于 从未存过档的整串（`n999sDDD:Q` → `LWWWDDD` == `n999sDDDWWWDDD`）；
  - 往返 `L:Q / L:Q` 多次后仍回到同一网格。
  - **测试卫生**：存档测试会写真实的 `save.txt` 到 CWD——每个用例在 `@AfterEach`/前置删除该文件，避免用例间污染与误留；"裸 L 无档"用例先确保文件不存在再断言空世界。
- **文法**：未知字符（`b`、`:`、`:x`）静默忽略、结果与去掉它们的串一致；`:Q` 后跟的按键被截断忽略。
- **坏入参回归**：`InputValidationTest`（null/空/缺 N/缺 S/非数字/空数字段）在 3B 后**保持全绿**——`L` 是新增合法前缀，不削弱既有 N…S 校验。
- **3A 回归网不破**：`@` 落地后重跑 3A 全套（coverage/连通/无死胡同/边界/转弯/确定性）必须全绿；断言按 FLOOR 字符计数、出生点取房间中心，已分析不会破坏不变式，实现期实测确认。
- **无渲染副作用**：所有行为测试 headless（无 StdDraw 上下文）下可跑。

测试先例：`proj3/tests/proj3a/` 的 `DeterminismSkeletonTest`（网格全等比较）、`InputValidationTest`（异常语义）、`CriterionSuiteTest`（结构不变量收口）为 3B 测试的参照；新增 3B 测试类放 `proj3/tests/proj3b/`，风格一致，不打包进交付。

## Out of Scope

- **主菜单 / HUD / 实时键盘交互壳**：N/L/Q 菜单、鼠标悬停描述、实时键盘循环、更好看的界面与游戏内美术——全部属于 web/表现层里程碑（Q1=b 已把菜单/HUD/美术划出 3B）。
- **win/lose / objective**：找目标闯关规则、游戏结束判定（sp24 视作可选机制；本 spec 不含）。
- **门 / 机关 / 实体层**：LOCKED/UNLOCKED_DOOR、钥匙、踏板等 mechanism seam 内容。
- **多存档槽**：上层"选文件名"的事，不碰 V1 格式。
- **表现层与主题**：昼夜、天气、自定义 tile 画风（ADR-0001 归 presentation）。
- **web 客户端**：Spark 后端、浏览器 Canvas、gson。
- **文法扩展**：除最小集外的新字母语义（interact/objectives…）——按 Q12 契约留缝，不在此实现。
- **世界变体**：seed 改变尺寸、多世界、大地图分块。

## Further Notes

- 北向愿景（不在此实现）：闯关玩法 + Java 引擎出数据 + 浏览器当客户端 + 机关/昼夜/多存档槽。structure layer 原样复用，runtime world 内核将来喂给 web，只换输入来源。
- 与 sp24 spec 对齐点：`N<seed>SWWWWAA` 的 W=上/A=左 语义、`:Q` 立即存档无需再按、只写 `.txt`、`getWorldFromInput` 永不 `System.exit`、运行时零随机使"RNG 还原"平凡成立。
- 骨架现状：`AutograderBuddy.SEED_FORM` 已是 `^[nN](\d+)[sS].*$`（S 后字符现全忽略）；`Tileset.AVATAR`、`FileUtils`、`TERenderer` 现成；`Main` 现只渲一帧。
- 验收锚点：`getWorldFromInput` 对 `n<seed>s`、`n<seed>s<按键>`、`L`（有/无档）三类输入都返回 80×80 全非空网格；3A 套件与 `InputValidationTest` 全绿。
