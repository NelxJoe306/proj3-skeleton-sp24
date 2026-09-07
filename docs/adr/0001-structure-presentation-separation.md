# ADR-0001: 结构层与表现层分离

3A 交付物必须通过课程 autograder / 人工评审，而项目后续要为游戏 demo 加自定义主题、昼夜、天气等视觉。我们决定：seed 确定性地产出的 **structure layer**（room/corridor/wall/floor/void）只用规范 `Tileset`，主题/装饰/天气作为 **presentation layer** 叠在其上，永不反向影响 seed 结构。取舍：放弃"生成时直接贴主题"的捷径，换取 autograder 确定性永远成立、以及美术可以后加的余地。
