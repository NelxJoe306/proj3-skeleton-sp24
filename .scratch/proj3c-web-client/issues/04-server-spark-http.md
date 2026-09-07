# 04 — Web 服务端：Spark 静态托管 + POST 重放端点

**What to build:** 起一个本地服务（用 `library-sp24` 现成的 Spark 2.9.4 + gson，零下载）：`GET /` 托管静态 SPA（HTML/CSS/JS）；一个 `POST` 端点收 Session JSON → 调 `replay(session)` → 回 GameSnapshot JSON。端点只做薄适配、不持会话（ADR-0004）；非法请求回 4xx + 说明；固定本机端口。外部行为 = HTTP，进程级冒烟（起服务 → POST → 断言信封字段与图例），浏览器接力在 05 起。

**Blocked by:** 01 — 引擎 A：三档尺寸世界 + 3C 重放 seam

**Status:** ready-for-agent

- [ ] 起服务后 `GET /` 返回 HTML（200），静态资源可达
- [ ] `POST` `{"kind":"new","size":"medium","seed":42,"keys":"DD"}` → 200 JSON：width/height=80、rows 含 avatar 移动后的网格、legend 覆盖出现的瓦片 id 且带 `#rrggbb` 色
- [ ] `POST` 非法 body（缺字段/坏 seed/未知 size）→ 4xx + 可读信息，不崩溃
- [ ] 存档读写发生在服务进程 CWD（与 3B 同一 `save.txt` 单槽约定）
- [ ] 冒烟脚本可一键起停服务并完成上述 POST 断言
