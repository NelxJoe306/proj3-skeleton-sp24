# 02 — 引擎 B：goal 与胜利冻结

**What to build:** 3C 世界里立起闯关目标：replay 建世界时把 GOAL 盖在**最晚生成房间中心**（(seed, size) 纯函数；与出生点重合则按确定性规则取最近内地 FLOOR）。GOAL 可通行可踏入；avatar 踏上 ⇒ 快照 `won=true`，此后移动键被忽略（冻结），`:Q` 仍可保存。快照新增 goal 坐标与 won。GOAL 属运行时 entity，永不经由结构生成、不进遗留 seam。

**Blocked by:** 01 — 引擎 A：三档尺寸世界 + 3C 重放 seam

**Status:** ready-for-agent

- [ ] 三档 × ≥12 seeds：goal 与出生点不同格、均为内地 FLOOR；goal 可达（连通性推论显式断言）；同 (size,seed) 重复 ⇒ goal 同格
- [ ] 存在一串 WASD 让 avatar 踏上 goal ⇒ `won=true`，goal 坐标未变
- [ ] 冻结：won 后继续追加移动键，快照不再变化（avatar 停在 goal）；won 后 `:Q` 仍触发存档
- [ ] 中档（3C 路径）goal 存在、遗留 `getWorldFromInput` 网格无任何 GOAL（两 seam 互不污染）
- [ ] 3A+3B 回归全绿
