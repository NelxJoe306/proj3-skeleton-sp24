# 05 — 存档 `:Q`（纯副作用 + 截断）

**What to build:** command stream 中出现 `:Q`/`:q` 时**立即存档并终止**：把当前 runtime state 写成进程 CWD 下固定文件名的 `.txt`（V1 格式：`seed=<long>` + `avatar=<x>,<y>`，经 `FileUtils`），并**忽略其后的所有字符**。存档是纯副作用——`:Q` 版的返回网格必须与去掉 `:Q` 的等长输入完全相同（spec 的 `N999SDDD:Q` vs `N999SDDD` 等价）。

**Blocked by:** 02 — WASD 移动 + 足迹还原

**Status:** ready-for-agent

- [ ] `getWorldFromInput("n<seed>sDDD:Q")` 返回网格 == `getWorldFromInput("n<seed>sDDD")`（`:Q` 纯副作用）
- [ ] `:Q` 后紧跟的字符被截断忽略（如 `"…:QW"` 与 `"…:Q"` 网格一致）
- [ ] `:q` 小写同样生效；`:Q` 出现在流中任意位置都触发存档终止
- [ ] 存档写成一个 `.txt` 文件（CWD 下固定名），内容为 V1 格式：`V1` / `seed=<long>` / `avatar=<x>,<y>`
- [ ] 存档后文件可被读回（内容含正确的 seed 与 avatar 坐标；坐标与返回网格中 `@` 位置一致）
- [ ] 测试卫生：用例用后删除存档文件（`@AfterEach`），不污染后续用例
