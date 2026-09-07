# 01 — Avatar 出生（runtime world 层诞生）

**What to build:** `getWorldFromInput("n<seed>s")` 返回的网格盖**恰一个 `@`**，落在第一个生成房间的 interior floor 中心；同 seed 两次调用逐格全等；3A 坏入参校验语义不变。这是 3B 的第一片——`@` 首次落地，正好验证 3A 判分网不破。

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

- [ ] `getWorldFromInput("n<seed>s")` 返回 80×80 全非空网格，内含**恰一个** `Tileset.AVATAR`
- [ ] `@` 位于第一个生成房间中心的 interior floor 上（房间 ≥5×5，中心被 floor 环绕）
- [ ] 同 seed 两次调用逐格全等（复用 `DeterminismSkeletonTest.gridsEqual` 语义）
- [ ] 纯 seed 输入（无按键）语义：返回网格 = 建完世界那一刻的运行时快照（含出生 `@`）
- [ ] `InputValidationTest`（坏 N…S 入参抛 IAE）保持全绿
- [ ] 3A 全套回归仍绿（coverage/连通/无死胡同/边界/转弯）
