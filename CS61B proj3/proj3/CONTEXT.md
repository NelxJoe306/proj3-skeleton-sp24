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
- **avatar** — 玩家在运行时世界里的标记（`@`，Tileset.AVATAR），压在可通行的地板上、随按键一格一格移动；属于 entity/mechanism 层的运行时对象，不是 structure。
- **command stream** — `getWorldFromInput` 收到的、`N<seed>S` 之后的一串按键字符；kernel 每遇到一个可识别字符走一步。W/A/S/D = 上/左/下/右（大小写均可）。
- **runtime world** — 当前可玩状态：结构层网格 + 叠在其上的运行时对象（现只有 avatar）。`getWorldFromInput` 返回的网格就是它的快照。
- **turn** — 一个可识别按键对 kernel 触发的一步移动/动作；系统禁绝依赖真实时间的机制（回合制）。
- **size（尺寸档）** — 世界网格的宽×高；3C 起由玩家开局时选 小/中/大（40×40 / 80×80 / 120×120）。遗留的 `N…S` 输入（不带尺寸）恒产出中档 80×80；**尺寸是会话级参数、不由 seed 决定**，因此必须随存档记录。
- **goal / objective** — 玩家要找到并让 avatar 踏上的运行时对象：确定性盖在**最晚生成房间中心**的 FLOOR 上（entity 层，永不经由结构生成）；位置是 (seed, size) 的纯函数，故无需存进存档，读档重建即得。
- **win / 过关（胜利冻结）** — avatar 踏上 goal 即赢；此后移动键被忽略、avatar 停在 goal 上。赢态可从快照推出：avatar 所在格 = goal 格。
- **HUD（客户端叠加）** — 浏览器画出的辅助层：到 goal 的直线距离（冷热）、移动步数、胜利横幅。
- **viewport / 镜头** — 浏览器 Canvas 的视口变换：以 avatar 为中心的镜头跟随 + 滚轮缩放（全图一屏 ↔ 贴地）；纯客户端呈现，不影响游戏状态；窗口缩放只是 canvas 响应式重排。
- **stateless replay server** — 3C 的 web 后端形态：服务端不持有会话，每次请求由「会话串」（size+seed+已按按键，或 读档+已按按键）纯函数式重建 runtime world 快照返回；浏览器每按一键全量重放。引擎仍是唯一真相源、输入来源无关。
- **session string / 会话串** — 浏览器持有的、完整决定当前一局的表示；stateless replay 服务与本地 headless 测试共用同一重放语义，故两者永不分叉。

- **game loop（双时钟主循环）** — 客户端 60FPS 游戏时钟：FixedUpdate 负责逻辑帧（物理碰撞、弹道、AI 状态机），Render 负责插值平滑渲染，确保各显示器刷新率下的丝滑手感。
- **fog of war / field of view（战争迷雾与视线）** — 迷雾渲染层：未探索区域全黑、已探索不在视线内为暗灰记忆区、视线内全亮。
- **torch（火把实体）** — 地图中可探索拾取的道具实体；拾取后显著扩大玩家自身的移动动态光晕半径。
- **signalis ui & crt post-processing** — 工业复古科幻美学：暗红/亮橙单色高反差框线排版、机械巨眼开场像素动画、全局像素 CRT 扫描线与准星后处理。
- **pixel combat & juice** — 纯像素打击反馈系统：主角白黄能量光束粒子弹道、怪物绿色月牙挥砍（Slash VFX）、受击白闪（Hit Flash）、屏幕震动（Screen Shake）、伤害抛物线飘字。
- **pixel hud & hearts** — 经典像素爱心血量容器 + 状态仪表盘。
- **rain weather mode（雨天模式）** — 可选的全屏像素雨滴粒子系统 + 落地溅射水花。

## 关键区分

- **结构**（seed → 完全确定）vs **运行时状态**（可变，随存档走）——两者永不混写。
- **structure layer**（3A，规范 tile）vs **presentation layer**（主题/氛围，后加）——theme 永远不反向影响结构。
- **输入来源无关**：kernel 只认 command stream 语义，不关心按键来自 autograder 字符串、StdDraw 还是未来 web——三者共用同一套 move/save/load 规则。
- **会话参数 vs 按键文法**：地图尺寸（及未来的存档槽）是"开一局"便固定的会话级参数，经会话/存档携带；command stream 的字母永远只表达单回合动作——两者正交，尺寸不进按键语义。
