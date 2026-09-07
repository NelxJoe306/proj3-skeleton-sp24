# 02 — WASD 移动 + 足迹还原

**What to build:** `N…S` 之后的 W/A/S/D（大小写均可）逐 turn 移动 avatar，方向语义 W:+y、A:−x、S:−y、D:+x（屏幕"上"= 数组 y 增大，与 `TERenderer` 一致）；avatar 离开的格子还原为脚下 tile（FLOOR）；返回网格反映新位置；同 seed + 同按键序列的轨迹确定。

**Blocked by:** 01 — Avatar 出生

**Status:** ready-for-agent

- [ ] `getWorldFromInput("n<seed>sW")` 相对 `"n<seed>s"`：`@` 在 y+1 处；A/S/D 同理（x−1 / y−1 / x+1）
- [ ] 移动键大小写等价（`w` 与 `W` 结果逐格相同）
- [ ] avatar 移过的格子恢复 FLOOR（折返后旧位无 `@` 残留、是 floor）
- [ ] 多步序列（如 `"n<seed>swwdd"`）逐格正确到达终点
- [ ] 同 seed + 同按键串重复调用逐格全等（确定性）
- [ ] 返回网格含**恰一个** `@`（不复制、不丢失）
