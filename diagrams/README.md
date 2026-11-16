# UML 模型与图

按课程阶段分别保存图表、可编辑源文件和配套工具。

| 目录 | 内容 | 阅读入口 |
| --- | --- | --- |
| `phase1/` | 一阶段 RBAC0：StarUML 模型、用例总图、领域模型、ER 图及生成校验工具 | [一期说明](phase1/README.md) |
| `phase2/` | 二阶段 RBAC1：总体用例、十五表 ER、OpenAPI 解读、运行时与工程架构 | [离线项目总览](phase2/index.html) / [二期说明](phase2/README.md) |

```text
diagrams/
├── phase1/
│   ├── rbac.mdj
│   ├── src/
│   ├── export/
│   ├── tools/
│   └── README.md
├── phase2/
│   ├── index.html
│   ├── assets/
│   ├── src/
│   ├── data/
│   ├── tools/
│   └── README.md
└── README.md
```

一期模型和导出图保留历史内容，不代表当前 RBAC1 实现。二阶段详细设计另见 [docs/final-design.adoc](../docs/final-design.adoc)。

从仓库根目录校验一期模型：

```powershell
node diagrams/phase1/tools/gen-mdj.mjs --check-only
node --test diagrams/phase1/tools/gen-mdj.test.mjs
```

`gen-mdj.mjs` 不带 `--check-only` 或 `--out` 会覆盖一期模型；日常核对使用上述只读校验命令。
