# 06: 怪物智能索敌追踪与绿色半月砍击特效

**What to build:** 地图中生成巡逻与追踪怪物。怪物利用 A* 寻路索敌逼近玩家，近身时挥出绿色半月形弧光砍击特效（Green Slash VFX），攻击玩家扣减血量，并触发受击白闪、受击红屏微震与顿帧。

**Blocked by:** 05: player-ranged-particle-combat

**Status:** ready-for-agent

- [ ] 怪物具有巡逻与视线范围内主动追踪玩家的实时 AI
- [ ] 怪物进入攻击距离挥出亮绿色半月形像素刀光斩击特效
- [ ] 玩家攻击命中怪物时触发怪物瞬间白闪（Hit Flash）与生命扣减
- [ ] 玩家受击时触发屏幕轻微受击震动（Screen Shake）与红色受挫边缘
