# docpilot-block

`docpilot-block` 是 DocPilot 的文档结构基础库。它负责把 Markdown 解析成 DocPilot 自己的可变 Block 树，也负责把 Block 树重新渲染成 Markdown，并转换成前端编辑器需要的 ProseMirror JSON DTO。

## 模块职责

- 使用 flexmark-java 解析 Markdown。
- 维护 DocPilot 自己的 Block 模型，作为后端内部稳定文档结构契约。
- 保留 Markdown 源码位置，方便后续 AI patch、局部替换、冲突检测。
- 将 DocPilot Block 转换为 ProseMirror JSON DTO。
- 将 DocPilot Block 渲染回规范化 Markdown。
- 保留原始 HTML，但不执行、不渲染、不清洗。

## 包结构

- `io.docpilot.block.model`：DocPilot Block 领域模型，使用可变 JavaBean。
- `io.docpilot.block.processing`：解析、渲染、ID 生成、ProseMirror JSON 转换等处理逻辑。
- `io.docpilot.block.prosemirror`：ProseMirror JSON 输出 DTO。
- `io.docpilot.block.typed`：Java 内部使用的 typed block view、属性读取、校验问题模型和统一转换入口。
- `io.docpilot.block.typed.adapter`：各类 `BlockType` 和 typed node 之间的转换、默认值、兼容和校验逻辑。

## 核心模型

- `BlockDocument`
  - 文档根对象。
  - 包含 `schemaVersion`、顶层 `blocks`、文档级 `metadata`。
- `BlockNode`
  - 块级节点，比如段落、标题、列表、表格、HTML block。
  - 每个 block 都有一个 UUID 生成的 `id`，不包含横线。
  - `attrs` 用来保存不同 block 的扩展字段，比如标题级别、代码语言、表格对齐、HTML 源码。
- `InlineNode`
  - 文本类 block 内部的行内节点。
  - 支持普通文本、行内代码、链接、图片、换行、HTML inline、未支持节点兜底。
- `SourceRange`
  - 原始 Markdown 中的位置范围。
  - 后续用于 AI 编辑、局部替换、patch 定位等场景。

这些模型都使用 Lombok `@Getter` / `@Setter` 的 JavaBean 形式，因为后续需要替换或更新某个 block 的内容。

## Typed Block View

`BlockDocument` / `BlockNode` 仍然是唯一 canonical model，用于数据库、REST、前端 JSON、历史兼容和未知 block 保真。它的 JSON 结构不变，也不会因为 typed view 升级 `docpilot-block/2`。

`TypedBlockDocument` / `TypedBlockNode` 是 Java 内部处理逻辑使用的类型化视图，主要服务于 renderer、ProseMirror converter、validator 和后续 AI patch。它不替代 canonical model，也不直接暴露给 REST 或前端 wire format。

转换入口集中在 `BlockNodeConverter`：

```java
TypedBlockDocument typed = BlockNodeConverter.toTyped(document);
BlockDocument canonical = BlockNodeConverter.toBlockDocument(typed);
List<ValidationIssue> issues = BlockNodeConverter.validate(document);
```

设计边界：

- parser 仍输出 `BlockNode`，保持 canonical 数据结构稳定。
- renderer 和 ProseMirror converter 先转 typed，再根据 typed node 处理业务逻辑。
- adapter 负责 `fromBlockNode`、`toBlockNode` 和 `validate`，把默认值、旧值兼容、类型转换和校验收口到一个地方。
- 业务逻辑不再裸读 `block.getAttrs().get(...)`，避免字段名散落、类型转换重复和默认值不一致。
- `BlockAttrs` 是 attr key enum，代码里通过 `BlockAttrs.LEVEL.key()` 取得 canonical JSON key。
- 可以类型化的字段尽量使用 enum，例如 HTML 的 `HtmlDisplayMode`、表格单元格的 `TableCellAlignment`。
- 未覆盖的 block 类型走 `GenericTypedBlock`，未知 attrs 会进入 `extraAttrs`，转回 `BlockNode` 时合并回去，避免迁移期间丢内容。
- `toTyped` 默认容错 normalize；单个 attr 类型错误不会中断文档渲染，但 `validate` 会返回结构化问题。

`ValidationIssue` 包含 `path`、`blockType`、`attrKey`、`severity` 和 `message`，用于后续在编辑器、日志或 AI 修复流程里定位问题。

## 当前支持的 Markdown

第一版支持：

- 段落和标题。
- 引用块。
- 无序列表和有序列表。
- GFM 任务列表。
- fenced code block 和缩进代码块。
- 分割线。
- GFM 表格。
- 加粗、斜体、删除线。
- 链接和图片。
- 软换行和硬换行。
- HTML block 和 HTML inline。

如果 flexmark 解析出了当前模型还没有覆盖的节点，会落到 `UNSUPPORTED_BLOCK` 或 `UNSUPPORTED_INLINE`，避免解析流程中断。

## HTML 策略

HTML 只作为源码数据保留。后端不渲染 HTML、不执行脚本、不做安全清洗，也不提取纯文本。

HTML block attrs：

- `id`：和 `BlockNode.id` 相同。
- `title`：默认 `HTML`。
- `source`：原始 HTML 源码。
- `displayMode`：默认 `fixed`，canonical 输出只使用 `fixed` 或 `auto`；旧值 `FIT`、`fit`、`fixed`、`auto` 都能兼容读取。
- `fixedHeightPx`：固定高度模式下的预览高度，默认 `320`。
- `allowScripts`：默认 `false`。

ProseMirror 输出示例：

```json
{
  "type": "docpilotHtmlBlock",
  "attrs": {
    "id": "1234567890abcdef1234567890abcdef",
    "title": "HTML",
    "source": "<div>hello</div>",
    "displayMode": "fixed",
    "fixedHeightPx": 320,
    "allowScripts": false
  }
}
```

搜索、索引、AI 上下文需要的纯文本提取，后续由检索或索引模块决定。

## 主要入口

```java
MarkdownBlockParser parser = new MarkdownBlockParser();
BlockDocument document = parser.parse(markdown);

ProseMirrorJsonConverter converter = new ProseMirrorJsonConverter();
ProseMirrorNode prosemirrorDoc = converter.toProseMirror(document);

MarkdownBlockRenderer renderer = new MarkdownBlockRenderer();
String normalizedMarkdown = renderer.render(document);

TypedBlockDocument typed = BlockNodeConverter.toTyped(document);
```

## ProseMirror 转换

`ProseMirrorJsonConverter` 将 DocPilot Block 转换为 ProseMirror 风格 DTO：

- `PARAGRAPH` -> `paragraph`
- `HEADING` -> `heading`
- `BULLET_LIST` -> `bulletList`
- `ORDERED_LIST` -> `orderedList`
- `TASK_LIST_ITEM` -> `taskItem`
- `CODE_BLOCK` -> `codeBlock`
- `TABLE` -> `table`
- `HTML_BLOCK` -> `docpilotHtmlBlock`

转换器内部使用字符串常量维护 node name、mark name 和 attr key，避免魔法字符串散落。

## 测试

从项目根目录运行：

```bash
mvn test
```

当前测试覆盖 Markdown 解析、GFM 表格、任务列表、HTML 保留、ProseMirror 转换、Markdown 回写。
