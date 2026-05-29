package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.SourceRange;

import java.util.Map;

/**
 * Common contract for typed block nodes derived from canonical BlockNode values.
 */
public sealed interface TypedBlockNode permits ParagraphBlock, HeadingBlock, CodeBlock, HtmlBlock,
        OrderedListBlock, TaskListItemBlock, TableCellBlock, CalloutBlock, MathBlock, DiagramBlock,
        FrontMatterBlock, FootnoteDefinitionBlock, LinkReferenceDefinitionBlock, TocBlock, RawBlock,
        GenericTypedBlock {

    String id();

    BlockType type();

    SourceRange sourceRange();

    Map<String, Object> extraAttrs();

}
