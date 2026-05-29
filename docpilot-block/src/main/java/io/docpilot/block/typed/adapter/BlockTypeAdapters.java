package io.docpilot.block.typed.adapter;

import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.HtmlDisplayMode;
import io.docpilot.block.typed.AttrReader;
import io.docpilot.block.typed.BlockAttrs;
import io.docpilot.block.typed.BlockNodeConverter;
import io.docpilot.block.typed.CalloutBlock;
import io.docpilot.block.typed.CodeBlock;
import io.docpilot.block.typed.DiagramBlock;
import io.docpilot.block.typed.FootnoteDefinitionBlock;
import io.docpilot.block.typed.FrontMatterBlock;
import io.docpilot.block.typed.GenericTypedBlock;
import io.docpilot.block.typed.HeadingBlock;
import io.docpilot.block.typed.HtmlBlock;
import io.docpilot.block.typed.LinkReferenceDefinitionBlock;
import io.docpilot.block.typed.MathBlock;
import io.docpilot.block.typed.OrderedListBlock;
import io.docpilot.block.typed.ParagraphBlock;
import io.docpilot.block.typed.RawBlock;
import io.docpilot.block.typed.TableCellBlock;
import io.docpilot.block.typed.TableCellAlignment;
import io.docpilot.block.typed.TaskListItemBlock;
import io.docpilot.block.typed.TocBlock;
import io.docpilot.block.typed.TypedBlockNode;
import io.docpilot.block.typed.ValidationIssue;
import io.docpilot.block.typed.ValidationSeverity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Factory for the default block type adapter registry.
 */
public enum BlockTypeAdapters {
    ;

    public static Map<BlockType, BlockTypeAdapter<? extends TypedBlockNode>> createDefaultAdapters() {
        Map<BlockType, BlockTypeAdapter<? extends TypedBlockNode>> adapters = new EnumMap<>(BlockType.class);
        register(adapters, new ParagraphAdapter());
        register(adapters, new HeadingAdapter());
        register(adapters, new OrderedListAdapter());
        register(adapters, new TaskListItemAdapter());
        register(adapters, new CodeBlockAdapter());
        register(adapters, new HtmlBlockAdapter());
        register(adapters, new TableCellAdapter());
        register(adapters, new CalloutAdapter());
        register(adapters, new MathBlockAdapter());
        register(adapters, new DiagramBlockAdapter());
        register(adapters, new FrontMatterAdapter());
        register(adapters, new FootnoteDefinitionAdapter());
        register(adapters, new LinkReferenceDefinitionAdapter());
        register(adapters, new TocAdapter());
        register(adapters, new RawAdapter(BlockType.EXTENSION_BLOCK));
        register(adapters, new RawAdapter(BlockType.UNSUPPORTED_BLOCK));

        for (BlockType type : BlockType.values()) {
            adapters.putIfAbsent(type, new GenericAdapter(type));
        }
        return adapters;
    }

    private static void register(Map<BlockType, BlockTypeAdapter<? extends TypedBlockNode>> adapters,
                                 BlockTypeAdapter<? extends TypedBlockNode> adapter) {
        adapters.put(adapter.type(), adapter);
    }

    private record ParagraphAdapter() implements BlockTypeAdapter<ParagraphBlock> {
        @Override
        public BlockType type() {
            return BlockType.PARAGRAPH;
        }

        @Override
        public ParagraphBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            return new ParagraphBlock(node.getId(), node.getInlines(), node.getSourceRange(), node.getAttrs());
        }

        @Override
        public BlockNode toBlockNode(ParagraphBlock node, BlockNodeConverter converter) {
            return BlockNode.of(node.id(), node.type(), node.extraAttrs(), node.inlines(), List.of(), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            return List.of();
        }
    }

    private record HeadingAdapter() implements BlockTypeAdapter<HeadingBlock> {
        @Override
        public BlockType type() {
            return BlockType.HEADING;
        }

        @Override
        public HeadingBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new HeadingBlock(
                    node.getId(),
                    AttrReader.boundedInteger(attrs, BlockAttrs.LEVEL.key(), 1, 1, 6),
                    node.getInlines(),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.LEVEL.key())
            );
        }

        @Override
        public BlockNode toBlockNode(HeadingBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.LEVEL.key(), node.level());
            return BlockNode.of(node.id(), node.type(), attrs, node.inlines(), List.of(), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidInteger(issues, node, path, BlockAttrs.LEVEL.key());
            int level = AttrReader.integer(node.getAttrs(), BlockAttrs.LEVEL.key(), 1);
            if (level < 1 || level > 6) {
                issues.add(issue(node, path, BlockAttrs.LEVEL.key(), "Heading level must be between 1 and 6"));
            }
            return issues;
        }
    }

    private record OrderedListAdapter() implements BlockTypeAdapter<OrderedListBlock> {
        @Override
        public BlockType type() {
            return BlockType.ORDERED_LIST;
        }

        @Override
        public OrderedListBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new OrderedListBlock(
                    node.getId(),
                    AttrReader.boundedInteger(attrs, BlockAttrs.START.key(), 1, 1, Integer.MAX_VALUE),
                    converter.toTypedNodes(node.getChildren()),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.START.key())
            );
        }

        @Override
        public BlockNode toBlockNode(OrderedListBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.START.key(), node.start());
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), converter.toBlockNodes(node.children()), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidInteger(issues, node, path, BlockAttrs.START.key());
            if (AttrReader.integer(node.getAttrs(), BlockAttrs.START.key(), 1) < 1) {
                issues.add(issue(node, path, BlockAttrs.START.key(), "Ordered list start must be at least 1"));
            }
            return issues;
        }
    }

    private record TaskListItemAdapter() implements BlockTypeAdapter<TaskListItemBlock> {
        @Override
        public BlockType type() {
            return BlockType.TASK_LIST_ITEM;
        }

        @Override
        public TaskListItemBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new TaskListItemBlock(
                    node.getId(),
                    AttrReader.bool(attrs, BlockAttrs.CHECKED.key(), false),
                    converter.toTypedNodes(node.getChildren()),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.CHECKED.key())
            );
        }

        @Override
        public BlockNode toBlockNode(TaskListItemBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.CHECKED.key(), node.checked());
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), converter.toBlockNodes(node.children()), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            return validateBoolean(node, path, BlockAttrs.CHECKED.key());
        }
    }

    private record CodeBlockAdapter() implements BlockTypeAdapter<CodeBlock> {
        @Override
        public BlockType type() {
            return BlockType.CODE_BLOCK;
        }

        @Override
        public CodeBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new CodeBlock(
                    node.getId(),
                    AttrReader.string(attrs, BlockAttrs.LANGUAGE.key(), ""),
                    AttrReader.string(attrs, BlockAttrs.TEXT.key(), ""),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.LANGUAGE.key(), BlockAttrs.TEXT.key())
            );
        }

        @Override
        public BlockNode toBlockNode(CodeBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.LANGUAGE.key(), node.language());
            attrs.put(BlockAttrs.TEXT.key(), node.text());
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), List.of(), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidString(issues, node, path, BlockAttrs.LANGUAGE.key());
            invalidString(issues, node, path, BlockAttrs.TEXT.key());
            return issues;
        }
    }

    private record HtmlBlockAdapter() implements BlockTypeAdapter<HtmlBlock> {
        @Override
        public BlockType type() {
            return BlockType.HTML_BLOCK;
        }

        @Override
        public HtmlBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new HtmlBlock(
                    node.getId(),
                    AttrReader.string(attrs, BlockAttrs.TITLE.key(), "HTML"),
                    AttrReader.string(attrs, BlockAttrs.SOURCE.key(), ""),
                    AttrReader.htmlDisplayMode(attrs, BlockAttrs.DISPLAY_MODE.key(), HtmlDisplayMode.FIXED),
                    AttrReader.boundedInteger(attrs, BlockAttrs.FIXED_HEIGHT_PX.key(), 320, 120, 1600),
                    AttrReader.bool(attrs, BlockAttrs.ALLOW_SCRIPTS.key(), false),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.ID.key(), BlockAttrs.TITLE.key(), BlockAttrs.SOURCE.key(),
                            BlockAttrs.DISPLAY_MODE.key(), BlockAttrs.FIXED_HEIGHT_PX.key(), BlockAttrs.ALLOW_SCRIPTS.key())
            );
        }

        @Override
        public BlockNode toBlockNode(HtmlBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.ID.key(), node.id());
            attrs.put(BlockAttrs.TITLE.key(), node.title());
            attrs.put(BlockAttrs.SOURCE.key(), node.source());
            attrs.put(BlockAttrs.DISPLAY_MODE.key(), node.displayMode().getValue());
            attrs.put(BlockAttrs.FIXED_HEIGHT_PX.key(), node.fixedHeightPx());
            attrs.put(BlockAttrs.ALLOW_SCRIPTS.key(), node.allowScripts());
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), List.of(), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidString(issues, node, path, BlockAttrs.TITLE.key());
            invalidString(issues, node, path, BlockAttrs.SOURCE.key());
            invalidInteger(issues, node, path, BlockAttrs.FIXED_HEIGHT_PX.key());
            invalidBoolean(issues, node, path, BlockAttrs.ALLOW_SCRIPTS.key());
            Object displayMode = node.getAttrs().get(BlockAttrs.DISPLAY_MODE.key());
            if (!AttrReader.isHtmlDisplayModeLike(displayMode)) {
                issues.add(issue(node, path, BlockAttrs.DISPLAY_MODE.key(), "HTML displayMode must be fixed, fit, or auto"));
            }
            return issues;
        }
    }

    private record TableCellAdapter() implements BlockTypeAdapter<TableCellBlock> {
        @Override
        public BlockType type() {
            return BlockType.TABLE_CELL;
        }

        @Override
        public TableCellBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new TableCellBlock(
                    node.getId(),
                    AttrReader.bool(attrs, BlockAttrs.HEADER.key(), false),
                    TableCellAlignment.from(AttrReader.string(attrs, BlockAttrs.ALIGNMENT.key(), "none")),
                    node.getInlines(),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.HEADER.key(), BlockAttrs.ALIGNMENT.key())
            );
        }

        @Override
        public BlockNode toBlockNode(TableCellBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.HEADER.key(), node.header());
            attrs.put(BlockAttrs.ALIGNMENT.key(), node.alignment().value());
            return BlockNode.of(node.id(), node.type(), attrs, node.inlines(), List.of(), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidBoolean(issues, node, path, BlockAttrs.HEADER.key());
            if (!AttrReader.isAlignmentLike(node.getAttrs().get(BlockAttrs.ALIGNMENT.key()))) {
                issues.add(issue(node, path, BlockAttrs.ALIGNMENT.key(), "Table cell alignment must be none, left, center, or right"));
            }
            return issues;
        }
    }

    private record CalloutAdapter() implements BlockTypeAdapter<CalloutBlock> {
        @Override
        public BlockType type() {
            return BlockType.CALLOUT;
        }

        @Override
        public CalloutBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new CalloutBlock(
                    node.getId(),
                    AttrReader.string(attrs, BlockAttrs.KIND.key(), "note"),
                    AttrReader.string(attrs, BlockAttrs.TITLE.key(), ""),
                    AttrReader.bool(attrs, BlockAttrs.COLLAPSIBLE.key(), false),
                    AttrReader.bool(attrs, BlockAttrs.OPEN.key(), true),
                    converter.toTypedNodes(node.getChildren()),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.KIND.key(), BlockAttrs.TITLE.key(), BlockAttrs.COLLAPSIBLE.key(), BlockAttrs.OPEN.key())
            );
        }

        @Override
        public BlockNode toBlockNode(CalloutBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.KIND.key(), node.kind());
            attrs.put(BlockAttrs.TITLE.key(), node.title());
            attrs.put(BlockAttrs.COLLAPSIBLE.key(), node.collapsible());
            attrs.put(BlockAttrs.OPEN.key(), node.open());
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), converter.toBlockNodes(node.children()), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidString(issues, node, path, BlockAttrs.KIND.key());
            invalidString(issues, node, path, BlockAttrs.TITLE.key());
            invalidBoolean(issues, node, path, BlockAttrs.COLLAPSIBLE.key());
            invalidBoolean(issues, node, path, BlockAttrs.OPEN.key());
            return issues;
        }
    }

    private record MathBlockAdapter() implements BlockTypeAdapter<MathBlock> {
        @Override
        public BlockType type() {
            return BlockType.MATH_BLOCK;
        }

        @Override
        public MathBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new MathBlock(
                    node.getId(),
                    AttrReader.string(attrs, BlockAttrs.NOTATION.key(), "latex"),
                    AttrReader.string(attrs, BlockAttrs.TEXT.key(), ""),
                    AttrReader.string(attrs, BlockAttrs.DELIMITER.key(), "$$"),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.NOTATION.key(), BlockAttrs.TEXT.key(), BlockAttrs.DELIMITER.key())
            );
        }

        @Override
        public BlockNode toBlockNode(MathBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.NOTATION.key(), node.notation());
            attrs.put(BlockAttrs.TEXT.key(), node.text());
            attrs.put(BlockAttrs.DELIMITER.key(), node.delimiter());
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), List.of(), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidString(issues, node, path, BlockAttrs.NOTATION.key());
            invalidString(issues, node, path, BlockAttrs.TEXT.key());
            invalidString(issues, node, path, BlockAttrs.DELIMITER.key());
            return issues;
        }
    }

    private record DiagramBlockAdapter() implements BlockTypeAdapter<DiagramBlock> {
        @Override
        public BlockType type() {
            return BlockType.DIAGRAM_BLOCK;
        }

        @Override
        public DiagramBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new DiagramBlock(
                    node.getId(),
                    AttrReader.string(attrs, BlockAttrs.ENGINE.key(), "mermaid"),
                    AttrReader.string(attrs, BlockAttrs.TEXT.key(), ""),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.ENGINE.key(), BlockAttrs.TEXT.key())
            );
        }

        @Override
        public BlockNode toBlockNode(DiagramBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.ENGINE.key(), node.engine());
            attrs.put(BlockAttrs.TEXT.key(), node.text());
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), List.of(), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidString(issues, node, path, BlockAttrs.ENGINE.key());
            invalidString(issues, node, path, BlockAttrs.TEXT.key());
            return issues;
        }
    }

    private record FrontMatterAdapter() implements BlockTypeAdapter<FrontMatterBlock> {
        @Override
        public BlockType type() {
            return BlockType.FRONT_MATTER;
        }

        @Override
        public FrontMatterBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new FrontMatterBlock(
                    node.getId(),
                    AttrReader.string(attrs, BlockAttrs.FORMAT.key(), "yaml"),
                    AttrReader.string(attrs, BlockAttrs.RAW.key(), ""),
                    AttrReader.object(attrs, BlockAttrs.DATA.key()),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.FORMAT.key(), BlockAttrs.RAW.key(), BlockAttrs.DATA.key())
            );
        }

        @Override
        public BlockNode toBlockNode(FrontMatterBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.FORMAT.key(), node.format());
            attrs.put(BlockAttrs.RAW.key(), node.raw());
            if (node.data() != null) {
                attrs.put(BlockAttrs.DATA.key(), node.data());
            }
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), List.of(), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidString(issues, node, path, BlockAttrs.FORMAT.key());
            invalidString(issues, node, path, BlockAttrs.RAW.key());
            return issues;
        }
    }

    private record FootnoteDefinitionAdapter() implements BlockTypeAdapter<FootnoteDefinitionBlock> {
        @Override
        public BlockType type() {
            return BlockType.FOOTNOTE_DEFINITION;
        }

        @Override
        public FootnoteDefinitionBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new FootnoteDefinitionBlock(
                    node.getId(),
                    AttrReader.string(attrs, BlockAttrs.LABEL.key(), ""),
                    AttrReader.string(attrs, BlockAttrs.RAW.key(), ""),
                    converter.toTypedNodes(node.getChildren()),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.LABEL.key(), BlockAttrs.RAW.key())
            );
        }

        @Override
        public BlockNode toBlockNode(FootnoteDefinitionBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.LABEL.key(), node.label());
            attrs.put(BlockAttrs.RAW.key(), node.raw());
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), converter.toBlockNodes(node.children()), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidString(issues, node, path, BlockAttrs.LABEL.key());
            invalidString(issues, node, path, BlockAttrs.RAW.key());
            return issues;
        }
    }

    private record LinkReferenceDefinitionAdapter() implements BlockTypeAdapter<LinkReferenceDefinitionBlock> {
        @Override
        public BlockType type() {
            return BlockType.LINK_REFERENCE_DEFINITION;
        }

        @Override
        public LinkReferenceDefinitionBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new LinkReferenceDefinitionBlock(
                    node.getId(),
                    AttrReader.string(attrs, BlockAttrs.LABEL.key(), ""),
                    AttrReader.string(attrs, BlockAttrs.HREF.key(), ""),
                    AttrReader.string(attrs, BlockAttrs.TITLE.key(), ""),
                    AttrReader.string(attrs, BlockAttrs.RAW.key(), ""),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.LABEL.key(), BlockAttrs.HREF.key(), BlockAttrs.TITLE.key(), BlockAttrs.RAW.key())
            );
        }

        @Override
        public BlockNode toBlockNode(LinkReferenceDefinitionBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.LABEL.key(), node.label());
            attrs.put(BlockAttrs.HREF.key(), node.href());
            attrs.put(BlockAttrs.TITLE.key(), node.title());
            attrs.put(BlockAttrs.RAW.key(), node.raw());
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), List.of(), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidString(issues, node, path, BlockAttrs.LABEL.key());
            invalidString(issues, node, path, BlockAttrs.HREF.key());
            invalidString(issues, node, path, BlockAttrs.TITLE.key());
            invalidString(issues, node, path, BlockAttrs.RAW.key());
            return issues;
        }
    }

    private record TocAdapter() implements BlockTypeAdapter<TocBlock> {
        @Override
        public BlockType type() {
            return BlockType.TOC;
        }

        @Override
        public TocBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new TocBlock(
                    node.getId(),
                    AttrReader.string(attrs, BlockAttrs.RAW.key(), "[TOC]"),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.RAW.key())
            );
        }

        @Override
        public BlockNode toBlockNode(TocBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            attrs.put(BlockAttrs.RAW.key(), node.raw());
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), List.of(), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidString(issues, node, path, BlockAttrs.RAW.key());
            return issues;
        }
    }

    private record RawAdapter(BlockType type) implements BlockTypeAdapter<RawBlock> {
        @Override
        public RawBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            Map<String, Object> attrs = node.getAttrs();
            return new RawBlock(
                    node.getId(),
                    type,
                    AttrReader.string(attrs, BlockAttrs.SOURCE.key(), ""),
                    AttrReader.string(attrs, BlockAttrs.RAW.key(), ""),
                    AttrReader.string(attrs, BlockAttrs.NODE_TYPE.key(), ""),
                    converter.toTypedNodes(node.getChildren()),
                    node.getSourceRange(),
                    AttrReader.extraAttrs(attrs, BlockAttrs.SOURCE.key(), BlockAttrs.RAW.key(), BlockAttrs.NODE_TYPE.key())
            );
        }

        @Override
        public BlockNode toBlockNode(RawBlock node, BlockNodeConverter converter) {
            Map<String, Object> attrs = attrs(node.extraAttrs());
            putIfNotBlank(attrs, BlockAttrs.SOURCE.key(), node.source());
            putIfNotBlank(attrs, BlockAttrs.RAW.key(), node.raw());
            putIfNotBlank(attrs, BlockAttrs.NODE_TYPE.key(), node.nodeType());
            return BlockNode.of(node.id(), node.type(), attrs, List.of(), converter.toBlockNodes(node.children()), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            List<ValidationIssue> issues = new ArrayList<>();
            invalidString(issues, node, path, BlockAttrs.SOURCE.key());
            invalidString(issues, node, path, BlockAttrs.RAW.key());
            invalidString(issues, node, path, BlockAttrs.NODE_TYPE.key());
            return issues;
        }
    }

    private record GenericAdapter(BlockType type) implements BlockTypeAdapter<GenericTypedBlock> {
        @Override
        public GenericTypedBlock fromBlockNode(BlockNode node, BlockNodeConverter converter) {
            return new GenericTypedBlock(
                    node.getId(),
                    type,
                    node.getInlines(),
                    converter.toTypedNodes(node.getChildren()),
                    node.getSourceRange(),
                    node.getAttrs()
            );
        }

        @Override
        public BlockNode toBlockNode(GenericTypedBlock node, BlockNodeConverter converter) {
            return BlockNode.of(node.id(), node.type(), node.extraAttrs(), node.inlines(),
                    converter.toBlockNodes(node.children()), node.sourceRange());
        }

        @Override
        public List<ValidationIssue> validate(BlockNode node, String path) {
            return List.of();
        }
    }

    private static Map<String, Object> attrs(Map<String, Object> extraAttrs) {
        return new java.util.LinkedHashMap<>(extraAttrs == null ? Map.of() : extraAttrs);
    }

    private static void putIfNotBlank(Map<String, Object> attrs, String key, String value) {
        if (value != null && !value.isBlank()) {
            attrs.put(key, value);
        }
    }

    private static List<ValidationIssue> validateBoolean(BlockNode node, String path, String key) {
        List<ValidationIssue> issues = new ArrayList<>();
        invalidBoolean(issues, node, path, key);
        return issues;
    }

    private static void invalidString(List<ValidationIssue> issues, BlockNode node, String path, String key) {
        if (!AttrReader.isStringLike(node.getAttrs().get(key))) {
            issues.add(issue(node, path, key, "Attribute must be a string"));
        }
    }

    private static void invalidInteger(List<ValidationIssue> issues, BlockNode node, String path, String key) {
        if (!AttrReader.isIntegerLike(node.getAttrs().get(key))) {
            issues.add(issue(node, path, key, "Attribute must be an integer"));
        }
    }

    private static void invalidBoolean(List<ValidationIssue> issues, BlockNode node, String path, String key) {
        if (!AttrReader.isBooleanLike(node.getAttrs().get(key))) {
            issues.add(issue(node, path, key, "Attribute must be a boolean"));
        }
    }

    private static ValidationIssue issue(BlockNode node, String path, String key, String message) {
        return new ValidationIssue(path, node.getType(), key, ValidationSeverity.WARNING, message);
    }

}
