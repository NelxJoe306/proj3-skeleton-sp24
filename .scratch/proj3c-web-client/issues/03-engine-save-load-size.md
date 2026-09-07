# 03 — 引擎 C：存档带尺寸行；读档续局

**What to build:** 3C 的存档/读档：`replay(load, …)` 读服务端进程 CWD 存档，按其中 (seed, size) 重建并**重新盖 goal**、把 avatar teleport 到记录坐标，再重放按键。存档格式：文件头恒 `V1`；**仅当尺寸 ≠ 80×80** 追加一行 `size=宽x高`（缺省 ⇒ 中档）；goal 不落盘（ADR-0005）。无档/坏档 ⇒ 快照 `saveMissing=true`、默认中档空世界、无 avatar。legacy 的 `L`/`:Q` 语义与文件字节逐字节不变。

**Blocked by:** 02 — 引擎 B：goal 与胜利冻结

**Status:** ready-for-agent

- [ ] `replay(load)` 无档 ⇒ `saveMissing=true` 且空世界（无 avatar、无 goal）
- [ ] 大/小档往返：`new(size,seed) 走 keys :Q` 后 `load` 续走，终点快照与不中断重放一致
- [ ] 存档文件：非 80×80 才含 `size=WxH` 行；中档文件与 3B 时期逐字节相同
- [ ] 赢了 `:Q` 后 `load` ⇒ 快照仍 `won=true`、avatar==goal
- [ ] 读档重建的世界仍盖 goal 于正确格（同 (seed,size) 推导，与存档前一致）
- [ ] 3A+3B（含 SaveQuitTest/LoadEquivTest）回归全绿
