# 06 — 读档 `L` + 存档等价 / 缺档

**What to build:** 输入串以 `L` 开头时从存档读档：解析 `seed=` 重建结构世界、`avatar=` 放回 avatar，其后按键照常执行。核心等价是 sp24 3B grader 的判分形状——`n<seed>sDDD:Q` 存档后 `LWWWDDD` 必须等于从未存过档的整串 `n<seed>sDDDWWWDDD`；多次 `L:Q / L:Q` 往返仍回到同一世界。裸 `L`（无档）或版本高于 V1 的档 → 返回全 NOTHING 的 80×80 空网格，不报错。

**Blocked by:** 05 — 存档 `:Q`（纯副作用 + 截断）

**Status:** ready-for-agent

- [ ] 等价：`getWorldFromInput("n<seed>sDDD:Q")` 后再 `getWorldFromInput("LWWWDDD")` == `getWorldFromInput("n<seed>sDDDWWWDDD")`
- [ ] `L` 后跟合法移动键照常执行（读档后 avatar 从存档位继续走）
- [ ] 往返稳定性：多次 `L:Q / L:Q` 循环后仍回到同一网格（与首次存档后的继续等价）
- [ ] 读档重建的结构与同 seed 新局一致（结构来自 seed，非存档整图）
- [ ] 裸 `L` 且无档 → 全 NOTHING 空网格、不抛错、不创建文件
- [ ] 版本号高于 V1 的档 → 视同无档，返回空网格
- [ ] 读档过程 headless、不渲染、不 `System.exit`
- [ ] 测试卫生：用例间删除/隔离存档文件，避免顺序污染
