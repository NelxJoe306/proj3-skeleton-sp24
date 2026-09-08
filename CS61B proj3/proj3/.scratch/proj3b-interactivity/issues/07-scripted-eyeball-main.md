# 07 — 目检：脚本步进渲染

**What to build:** `Main` 扩展为可吃**完整输入串**并逐命令渲染一帧（每步短暂停留），让人眼确认移动/存档/读档流程——纯 seed 的旧渲染行为保留。仅开发目检用，不进 autograder 路径。

**Blocked by:** 06 — 读档 `L` + 存档等价 / 缺档

**Status:** ready-for-agent

- [ ] `Main` 接受完整输入串参数（如 `n<seed>swwdd:q`），逐命令渲染一帧，肉眼可见 avatar 逐格移动
- [ ] `:Q` 存档步骤后不报错、干净结束（`System.exit` 仅在目检入口退出，头 AWT 线程）
- [ ] 纯 seed 输入（`n<seed>s`）的旧渲染行为保留（渲一帧供人工看）
- [ ] 目检入口不参与 `getWorldFromInput` 行为判定（autograder 路径无渲染）
