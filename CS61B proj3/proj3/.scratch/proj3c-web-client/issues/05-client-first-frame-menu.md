# 05 — Web 客户端：主菜单与首帧渲染

**What to build:** 浏览器 vanilla JS 的第一屏：主菜单（尺寸 小/中/大 默认中 + seed 可填可留空随机）→ 按下开始 → POST `new` 会话 → 拿到快照 → 在 Canvas 上**全图一屏**画出来（按 legend 上色：bg 色块 + fg 字形；avatar `@` 可见）。seed 留空用 crypto 随机取一个合法数字。窗口像素与瓦片格解耦（之后镜头/缩放票复用同一绘制函数）。

**Blocked by:** 04 — Web 服务端：Spark 静态托管 + POST 重放端点

**Status:** ready-for-agent

- [ ] 打开页面见主菜单；选 小/中/大 + 填/空 seed → 开始后画面出现对应尺寸世界（三档各验一次）
- [ ] 空 seed 每次新局世界不同；同 seed 重开画面一致
- [ ] 绘制按 legend 色值与字形，结构可辨识（墙/地/空有别）；avatar 与出生位置一致
- [ ] 刷新后再开是新局（本票不承诺 Continue；那是 09）
