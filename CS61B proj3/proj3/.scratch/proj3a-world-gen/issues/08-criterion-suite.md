# 08 — 覆盖率收口 + 全套判分点套件

**What to build:** 把 3A 的判分标准收口成一套**可常跑的自动化不变量测试**，并调参使世界在整套标准下全绿：floor coverage >50%、全连通、转弯走廊 ≥20%、corridor 宽 1 且无死胡同、边界无地板、房间可辨（wall/floor/void 三类一眼可分）——跨一批 seed 抽样验证，同时复核确定性（同 seed ⇒ 同网格）。这本地代理了人工评审与 autograder 的判定。

**Blocked by:** 07 — 转弯走廊

**Status:** ready-for-agent

- [ ] 跨抽样 seed：coverage >50%、全连通、≥20% 含转弯、无死胡同、边界无地板 全部成立
- [ ] wall / floor / void 三类 tile 可区分，room 状地板块存在
- [ ] 同 seed 确定性在整套套件下复核通过
- [ ] 套件只测外部行为（读 `getWorldFromInput` 返回的网格），不依赖生成器内部细节
