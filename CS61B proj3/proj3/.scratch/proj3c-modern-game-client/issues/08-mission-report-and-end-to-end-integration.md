# 08: SIGNALIS 战绩结算报告单与全链路联调

**What to build:** 玩家击败怪物、探索地牢到达终点目标格（★）后，游戏进入胜利态并弹出极具工业美感的 SIGNALIS 任务数据报告单（MISSION REPORT：用时、步数、击杀数、评级 S/A/B），并支持返回主菜单开启新局。

**Blocked by:** 03: dynamic-fog-lighting-and-torch, 04: pixel-rain-weather-mode, 07: items-pixel-hud-and-floating-text

**Status:** ready-for-agent

- [ ] 踏上终点格（★）触发胜利冻结，弹出暗红黑高反差战绩结算报告单
- [ ] 结算单精确展示本局生存用时、移动步数、怪物击杀数与综合评分
- [ ] 结算单提供一键返回 SIGNALIS 主菜单按钮，状态可干净重置
- [ ] 全套 84 个 Java 后端测试与客户端核心逻辑全链路通畅，无报错与内存泄漏
