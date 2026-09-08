# 06 — 走廊与连通：1 格宽打通全部 room

**What to build:** 把 05 产出的房间用 **corridor** 接成一个整体：corridor 宽恒为 1，连接不同 room/区域，使**所有 floor 全连通**（任一 floor 出发可达全部）。每条 corridor 两端都接住 room/区域，**不产生死胡同 spur**（不存在走进走不出的 1 格尾端）。

**Blocked by:** 05 — BSP 房间

**Status:** ready-for-agent

- [ ] 从任一 floor flood-fill 能达全部 floor（全连通）
- [ ] corridor 宽恒为 1
- [ ] 无死胡同 corridor（无地板盲端）
- [ ] 同 seed 复现（确定性保持）
