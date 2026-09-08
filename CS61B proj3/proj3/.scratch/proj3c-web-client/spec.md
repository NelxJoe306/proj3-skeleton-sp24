Status: ready-for-agent
Feature: proj3c-web-client

# proj3C 找目标闯关 web 客户端（sized worlds + stateless replay + canvas demo）spec

> 术语遵循 `CONTEXT.md`；遵守 ADR-0001（结构/表现分离）、ADR-0002（存档 = seed + 运行时快照）、ADR-0003（运行时零随机）、ADR-0004（stateless replay server）、ADR-0005（存档带尺寸行；goal 推导不存档）。

## Problem Statement

3A/3B 交付了一个 headless 的确定性引擎——structure layer 由 seed 生成、runtime world 按 command stream 重放，判分与本地 59 回归全绿，但玩家看不到也玩不起来：`Main` 只是开发目检壳。北向目标是把同一内核变成一个「找目标闯关」的、**浏览器里真人可玩**的 demo。3C 是一条端到端 tracer bullet：主菜单 → 选尺寸/seed 建世界或读档 → 浏览器 Canvas 渲染 → WASD 移动 → HUD 冷热距离 → 踏上目标过关。课程 autograder 不再约束本里程碑（grill Q2=ii）；本地 3A+3B 的 **59 回归当安全网**，遗留 seam 一行不改。

## Solution

3C 把 3B 的引擎扩成**三档尺寸 + 过关目标**，再用一条**新的单一 seam**（stateless replay）同时喂给 Spark 后端和 headless 测试；浏览器只是「持会话描述符 → 每键全量重放 → 拿到世界信封 → 画」的客户端。

- **尺寸档**：小 40×40 / 中 80×80 / 大 120×120。中档 = 遗留默认（80×80，生成布局与今天逐格相同）；尺寸是**会话级参数**（不进按键文法，ADR-0005 因此成立）。
- **goal**：运行时 entity（新 tile，不进 structure，ADR-0001），确定性盖在**最晚生成房间中心**的 FLOOR 上；位置 = (seed, size) 纯函数 ⇒ 无需存档（ADR-0005）。goal 与出生点重合时按确定性规则偏移。
- **赢 = 冻结**：avatar 踏上 goal 即赢；此后移动键被忽略（`:Q` 保存仍可用）。赢态 = avatar 格 == goal 格，可由快照推出。
- **stateless replay server（ADR-0004）**：服务端无会话，`replay(session) → GameSnapshot` 是 3C 世界唯一的行为面——Spark 路由与 headless 测试都只调它；浏览器持 session 每键全量重放。localStorage 存 session ⇒ 主菜单可 Continue。

## User Stories

1. 作为玩家，我希望在主菜单选**地图尺寸（小/中/大）**，这样我控制世界的规模。
2. 作为玩家，我希望（选了尺寸后）输入一个数字 seed 或留空随机，这样新开局可复现也可即兴。
3. 作为玩家，我希望「读档」能继续上次 `:Q` 存档的那一局（含当时尺寸与位置），这样进度不丢。
4. 作为玩家，我希望浏览器窗口里能**看到整个 80/120 世界并放大**，鼠标滚轮在「全图一屏」与「贴地」之间缩放，这样我能远观也能细看。
5. 作为玩家，我希望放大后**镜头始终跟着我的 `@`**，这样走路时地图自然滚动、我不用手动平移。
6. 作为玩家，我希望**调整窗口大小/全屏**时画面响应式重排、游戏操作与状态不受影响，这样我可以随意拖窗。
7. 作为玩家，我希望 WASD 让 `@` 一格一格移动（上/左/下/右），撞墙或撞界停住不报错，这样手感是"被挡住"。
8. 作为玩家，我希望 HUD 显示我到 goal 的**直线距离（冷热）**和已走步数，这样"找"不靠瞎逛。
9. 作为玩家，我希望踏上一个特殊的目标格时看到**胜利横幅**，之后我不能再移动（这一局已结束），这样"过关"有明确的终点。
10. 作为玩家，我希望游戏里能「保存并退出」（写服务端档）也能直接「退出」（不存），回主菜单，这样我可以随时离开。
11. 作为玩家，我希望我玩了一半、刷新/关页后回到主菜单能 **Continue** 续上同一局，这样误关不丢进度。
12. 作为玩家，我希望点到地面/墙等瓦片时（可选）能认出它是什么，这样界面友好——**标注：v1 不做，见 Out of Scope**。
13. 作为玩家，我希望三档尺寸里 goal 都**确实可达**（同一连通地板区域），这样不会出现无法过关的死局。
14. 作为玩家，我希望同 (seed, size) 重开总得到同一个世界与同一个 goal 位置，这样重玩可复现。
15. 作为学生/开发者，我希望 web 后端**无会话、无状态漂移**——每次请求由 session 重放重建世界，这样调试/回放与 headless 测试完全同构（ADR-0004）。
16. 作为开发者，我希望 goal/尺寸/冻结等**全部只在新的 3C seam** 上，遗留 `getWorldFromInput`（N…S 文法、默认 80、无 goal）**字节级不动**，这样 59 回归不需任何修改。
17. 作为开发者，我希望 3C 引擎改动 headless 可测、不依赖 StdDraw/浏览器，这样坏行为先在测试里现形。
18. 作为开发者，我希望服务器把世界序列化成「紧凑字符/图例网格」而非整对象树，这样 80~120 网格每键重放也够快。
19. 作为开发者，我希望存档文件在服务端进程 CWD 单槽（与 3B 相同），多存档槽留给未来（选文件名）。
20. 作为开发者，我希望当无档「读档」时安静返回空世界并带 `saveMissing` 标记、不报错，这样 UI 能给出友好提示。

## Implementation Decisions

- **Seam（两个，一旧一新）**：
  - Legacy `AutograderBuddy.getWorldFromInput(String)`——**不动**。仍是默认 80×80、只有 avatar、无 goal、`L`/`:Q` 语义不变；59 回归专用。
  - **3C 唯一 seam**（Java 方法，非 HTTP）：`replay(session) → GameSnapshot`。Spark 路由只是它的薄适配；3C headless 测试直接构造 `session` 调它。这是 3C 世界的唯一行为面（"单一 seam" 原则在此特指 3C 这一侧，不含历史冻结面）。
- **Session 描述符**（浏览器持有、每键更新、存 localStorage）：
  ```
  { kind: "new"|"load",
    size: "small"|"medium"|"large",   // kind=new 时
    seed: <long>,                     // kind=new 时
    keys: "<已按字符串>" }             // WASD 与 : / Q
  ```
  `replay`：`new` → 按 (size,seed) 建世界盖 goal，重放 keys；`load` → 读服务端档（含 size）重建盖 goal，重放 keys；无档 ⇒ 默认中档全 NOTHING 空世界 + `saveMissing=true`。
- **世界信封 GameSnapshot**：`{ width, height, seed, won, avatar, goal, rows, legend }`。`rows` = 每格 tile **id** 的二维数组（紧凑）；`legend` = id → `{ name, fg "#rrggbb", bg "#rrggbb" }`（本局会出现的那几种瓦片）；`avatar`/`goal` 为坐标或 null（空世界）。`won` = avatar 格 == goal 格，由引擎算。
- **尺寸档 ↔ 生成**：中档沿用现生成常量（布局逐格不变）。**小/大档需把 BSP 分割密度按内区面积取参**——现 `MIN_LEAF=20/SPLIT_DIM=41` 下 40×40 内区 36×36 永不分割、会退化成单房间（grill 已确认要调）。规则：除中档外的尺寸由 (seed, size) 确定性派生参数；中档参数不变以保证 80 布局与今天一致。
- **goal / 冻结（3C seam 内）**：新增一枚运行时 tile（非 structure）；中心 = 最晚生成房间中心，若与出生点重合则取确定性最近的内地 FLOOR。goal 格可踏入；踏入 ⇒ `won`，此后移动键被忽略（`:Q` 仍可保存）。布局/出生/goal 全为 (seed, size) 纯函数（ADR-0003）。
- **存档（ADR-0005）**：文件头保持 `V1`；**仅当尺寸 ≠ 80×80 时**追加 `size=宽x高` 行；读档缺省 ⇒ 中档。仍写进程 CWD 的固定 `save.txt`（服务端单槽）。goal 不落盘、读档重建。
- **传输 / 序列化**：GET `/` 静态 SPA；一个 POST 重放端点收 Session 回 GameSnapshot（c1 stateless：每键整包重放）。`TETile` 需补 `textColor()/backgroundColor()` 只读访问器（现只有 character/description/id），并新增 goal tile（取下一个空 id）。
- **客户端**：vanilla JS + Canvas2D + fetch，无框架、无构建。视图状态机：主菜单（New Game: 尺寸→seed→Start；Load；Continue 有则亮；无 New/Load 时 Quit）⇄ 游戏（HUD + canvas）。渲染：按 legend 上色画瓦片（bg 色块 + fg 字形，无字形安全时用几何标记）；**镜头**以 avatar 为中心，滚轮在 [全图一屏, 贴地] 缩放；全图档镜头锁定世界中心。**resize/DPR** 响应式重排，纯客户端、不影响状态。HUD：尺寸+seed、步数、到 goal 直线距离、胜利横幅。
- **依赖（风险标注）**：首选 **Spark + gson**（与本项目既定路线一致）；若无法离线取 jar，回退 JDK 内置 `com.sun.net.httpserver` + 手写极简 JSON——接口/session/信封不变，只换适配层。

## Testing Decisions

好测试只测**外部行为**。3C 的外部面 = `replay(session)` 返回的 `GameSnapshot`；3C 测试一律只经它（浏览器行为 == 测试行为，ADR-0004）。Legacy 59 回归**不改一行**，作为"遗留 seam 冻结"的护栏。

- **尺寸档不变量套件**（每档 × ≥12 seeds 横断面）：同 (size,seed,keys) 重复 ⇒ 逐格全等；地板单连通（BFS 从一 FLOOR 出发可达所有 FLOOR）；房间数 ≥ 档位下限（小 ≥3 / 中 ≥5 / 大 ≥8——若 tuning 达不到则调参后再固定该数）；无 FLOOR 落在外边界。
- **goal 可达/可赢**：goal 与出生点不同格且均为内地 FLOOR；从出生点 BFS 必达 goal（连通性推论，显式断言）；存在一串 WASD 让 avatar 踏上 goal ⇒ `won=true`，且此后继续加移动键快照不再变化（冻结）；冻结期 `:Q` 仍写档。
- **replay 等价**：`kind=new` 直接走 DDD 与"走 DDD 后 :Q 再 kind=load 续走"得到同一终点快照（中档 & 大档各验）；`kind=load` 无档 ⇒ `saveMissing=true` 且为默认中档空世界。
- **medium 与遗留同构**：`replay(medium, seed, "")` 的结构格 == `getWorldFromInput("n<seed>s")`（忽略 goal/avatar 覆盖），锁死"中档布局 = 今天的 80"。
- **headless 无渲染副作用**：所有 3C 测试不碰 StdDraw/浏览器；服务端适配层（HTTP）单独以进程级冒烟验证（起服务 → POST → 断言信封）或手工目检，不进单元面。
- 测试先例：沿用 `proj3/tests/proj3a|3b` 的网格比较 / 存档卫生（`@AfterEach` 删 `save.txt`）风格；3C 测试放 `proj3/tests/proj3c/`。

## Out of Scope

- **悬停瓦片说明 / 更好的美术**（可选小花，不进 v1 验收）。
- **机关 / 门 / 昼夜 / 多存档槽 / 世界变体**——既定预留 seam，后续里程碑。
- **有状态会话 / WebSocket / 多人**——ADR-0004 已否。
- **自由平移镜头 / 小地图**——相机锁定 avatar（grill Q7）。
- **课程 autograder / 桌面 StdDraw 壳改造**——遗留冻结。

## Further Notes

- 落盘格式因 `SaveQuitTest` 钉死首行 `"V1"` 而**不升 V2**（见 ADR-0005 修订）：中档存档与今天逐字节相同，尺寸只以可选 `size=` 行进入非默认档。故 spec 内一律称"存档 V1（可带尺寸行）"，不再是 grill 期口头说的"V1→V2"。
- 引擎侧需要：`RuntimeWorld` 支持 (seed, w, h) + 可选 goal 模式 + 冻结；`WorldGenerator` 尺寸感知；存档读写 size；`TETile` 新 goal tile + 颜色只读访问器。这些是全走 3C seam 的 engine slice。
- 风险最集中处：**小档 40×40 的生成调参**（房间数/连通/不可达需调参验证）——若个别 seed 退化成单房间或房间过小，须回本 spec 修生成规则再固定测试常数。
