# 02: 60FPS 实时双时钟主循环与平滑阻尼摄像机

**What to build:** 重构客户端游戏主循环为 60FPS 双时钟模型（FixedUpdate 逻辑时钟 + Render 状态插值），支持玩家 WASD 无级平滑走位（告别单格瞬移），并构建进阶摄像机（平滑跟随、死区稳定、滚轮缩放与边界防黑边）。

**Blocked by:** 01: signalis-main-menu-and-crt-overlay

**Status:** ready-for-agent

- [ ] 实现 requestAnimationFrame 驱动的双时钟循环，逻辑计算与渲染解耦
- [ ] WASD 控制玩家在瓦片空间平滑移动并精准处理墙壁滑动碰撞
- [ ] 摄像机具备 Lerp 平滑阻尼跟随与死区（Deadzone）防抖机制
- [ ] 支持鼠标滚轮自由缩放视口（贴地特写至全图鸟瞰）且不露出地图外黑边
