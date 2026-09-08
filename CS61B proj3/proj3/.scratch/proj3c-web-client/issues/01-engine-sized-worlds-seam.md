# 01 — 引擎 A：三档尺寸世界 + 3C 重放 seam

**What to build:** 把 3C 的结构地基立起来，并让"给定 (尺寸档, seed, 按键)"能重建/重放**任一档**世界。
- TETile 补颜色只读访问器（供未来 web 图例）；Tileset 增 GOAL 瓦片（下一空 id）。
- 世界生成**按尺寸档取参**：小 40×40 / 中 80×80 / 大 120×120 都有多房间结构且地板单连通；**中档生成的网格与今天逐格一致**（参数不变）。
- 新会话描述符 Session 与快照 GameSnapshot（宽/高/seed/avatar/rows=瓦片 id 网格/legend=id→名字与 fg/bg 色；goal、won、saveMissing 字段预留为 null/false）。
- 新 seam `replay(new, 尺寸, seed, 按键)`：建档 → 出生在首房间中心 → 逐键重放（WASD 移动、撞墙/越界停住、未知字符忽略）→ 返回快照。3C headless 测试一律经它（ADR-0004）。

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

- [ ] TETile 新增公开颜色读取；Tileset.GOAL 存在且描述/色/id 正确
- [ ] `replay(new, 三档任一, 若干 seeds, "")` 返回尺寸匹配、有结构、avatar 在首房间中心、goal 为 null 的快照
- [ ] 中档：`replay(new, medium, seed, "")` 网格 == `getWorldFromInput("n<seed>s")`（逐格含 avatar 位置一致）
- [ ] 小/大档 × ≥12 seeds：重放两次逐格全等（确定性）；地板单连通；房间数 ≥ 档位下限（调参后定死：小≥3 / 大≥8）；无 FLOOR 落在边界
- [ ] 按键重放：WASD 各自一格、撞墙/越界不动、未知字符忽略、足迹恢复正确
- [ ] 3A+3B 回归全绿（本票不改遗留 seam）
