# 04 — StdDraw 目检渲染入口

**What to build:** 提供一个人工目检入口：给定一个 seed，用骨架现成的渲染机制（`TERenderer`/StdDraw）把 `getWorldFromInput` 的结果渲染出来，让 05–08 每张票做完后都能**用眼睛看到**世界长什么样。它只做"渲染 seam 的结果"，不含任何玩法逻辑。

**Blocked by:** 02 — 确定性最小管道

**Status:** ready-for-agent

- [ ] 对任一合法 seed，运行入口可弹出/生成一帧世界画面，不崩溃
- [ ] 同一 seed 渲染出的画面与 `getWorldFromInput` 返回的网格一致
- [ ] 该入口不进入 autograder 路径（`getWorldFromInput` 本身仍 headless）
