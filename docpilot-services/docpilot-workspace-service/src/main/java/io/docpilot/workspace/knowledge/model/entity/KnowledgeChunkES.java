package io.docpilot.workspace.knowledge.model.entity;

import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.model.KnowledgeIndexedChunk;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch entity for inline-completion knowledge chunks.
 */
@Getter
@Setter
@Document(indexName = KnowledgeProperties.DEFAULT_INDEX_NAME, createIndex = false)
public class KnowledgeChunkES {

    /**
     * Elasticsearch field names for knowledge chunk documents.
     */
    public static final class Fields {

        /**
         * ES document id.
         */
        public static final String id = "id";

        /**
         * Workspace id used as the primary tenant filter.
         */
        public static final String workspaceId = "workspaceId";

        /**
         * Owner user id used to constrain retrieval to the workspace owner.
         */
        public static final String ownerUserId = "ownerUserId";

        /**
         * Workspace document id that produced this chunk.
         */
        public static final String documentId = "documentId";

        /**
         * Document revision id whose snapshot produced this chunk.
         */
        public static final String revisionId = "revisionId";

        /**
         * Current document title, used as auxiliary search text.
         */
        public static final String title = "title";

        /**
         * Chunk category stored as keyword, such as BLOCK or SECTION_SUMMARY.
         */
        public static final String chunkType = "chunkType";

        /**
         * Source block id in the revision snapshot.
         */
        public static final String blockId = "blockId";

        /**
         * Source block type stored as BlockType.name().
         */
        public static final String blockType = "blockType";

        /**
         * Stable index for chunks generated from the same source block.
         */
        public static final String chunkIndex = "chunkIndex";

        /**
         * Heading labels from document root to the chunk's current section.
         */
        public static final String headingPath = "headingPath";

        /**
         * Rendered chunk text used for BM25 retrieval and embedding.
         */
        public static final String content = "content";

        /**
         * Dense vector generated from content for kNN retrieval.
         */
        public static final String embedding = "embedding";

        /**
         * BaseEntity-style creation time.
         */
        public static final String createTime = "createTime";

        /**
         * BaseEntity-style creator display name.
         */
        public static final String createBy = "createBy";

        /**
         * BaseEntity-style creator user id.
         */
        public static final String creatorId = "creatorId";

        /**
         * BaseEntity-style update time.
         */
        public static final String updateTime = "updateTime";

        /**
         * BaseEntity-style updater display name.
         */
        public static final String updateBy = "updateBy";

        /**
         * BaseEntity-style updater user id.
         */
        public static final String updaterId = "updaterId";

        /**
         * BaseEntity-style soft-delete marker.
         */
        public static final String isDeleted = "isDeleted";
    }

    /**
     * ES document id, built from workspace/document/chunk/block identifiers.
     */
    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    /**
     * Workspace id used as the primary tenant filter.
     */
    @Field(type = FieldType.Long)
    private Long workspaceId;

    /**
     * Owner user id used to constrain retrieval to the workspace owner.
     */
    @Field(type = FieldType.Long)
    private Long ownerUserId;

    /**
     * Workspace document id that produced this chunk.
     */
    @Field(type = FieldType.Long)
    private Long documentId;

    /**
     * Document revision id whose snapshot produced this chunk.
     */
    @Field(type = FieldType.Long)
    private Long revisionId;

    /**
     * Current document title, used as auxiliary search text.
     */
    @Field(type = FieldType.Text)
    private String title;

    /**
     * Chunk category stored as keyword, such as BLOCK or SECTION_SUMMARY.
     */
    @Field(type = FieldType.Keyword)
    private String chunkType;

    /**
     * Source block id in the revision snapshot.
     */
    @Field(type = FieldType.Keyword)
    private String blockId;

    /**
     * Source block type stored as BlockType.name().
     */
    @Field(type = FieldType.Keyword)
    private String blockType;

    /**
     * Stable index for chunks generated from the same source block.
     */
    @Field(type = FieldType.Integer)
    private Integer chunkIndex;

    /**
     * Heading labels from document root to the chunk's current section.
     */
    @Field(type = FieldType.Keyword)
    private List<String> headingPath = new ArrayList<>();

    /**
     * Rendered chunk text used for BM25 retrieval and embedding.
     */
    @Field(type = FieldType.Text)
    private String content;

    /**
     * Dense vector generated from content for kNN retrieval.
     */
    @Field(type = FieldType.Dense_Vector)
    private List<Float> embedding;

    /**
     * BaseEntity-style creation time.
     */
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createTime;

    /**
     * BaseEntity-style creator display name.
     */
    @Field(type = FieldType.Keyword)
    private String createBy;

    /**
     * BaseEntity-style creator user id.
     */
    @Field(type = FieldType.Long)
    private Long creatorId;

    /**
     * BaseEntity-style update time.
     */
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime updateTime;

    /**
     * BaseEntity-style updater display name.
     */
    @Field(type = FieldType.Keyword)
    private String updateBy;

    /**
     * BaseEntity-style updater user id.
     */
    @Field(type = FieldType.Long)
    private Long updaterId;

    /**
     * BaseEntity-style soft-delete marker.
     */
    @Field(type = FieldType.Boolean)
    private Boolean isDeleted = false;

    public static KnowledgeChunkES fromSource(String hitId, Map<?, ?> source) {
        KnowledgeChunkES entity = new KnowledgeChunkES();
        Object id = source.containsKey(Fields.id) ? source.get(Fields.id) : hitId;
        entity.setId(string(id));
        entity.setWorkspaceId(longValue(source.get(Fields.workspaceId)));
        entity.setOwnerUserId(longValue(source.get(Fields.ownerUserId)));
        entity.setDocumentId(longValue(source.get(Fields.documentId)));
        entity.setRevisionId(longValue(source.get(Fields.revisionId)));
        entity.setTitle(string(source.get(Fields.title)));
        entity.setChunkType(string(source.get(Fields.chunkType)));
        entity.setBlockId(string(source.get(Fields.blockId)));
        entity.setBlockType(string(source.get(Fields.blockType)));
        entity.setChunkIndex(intValue(source.get(Fields.chunkIndex)));
        entity.setHeadingPath(stringList(source.get(Fields.headingPath)));
        entity.setContent(string(source.get(Fields.content)));
        entity.setEmbedding(floatList(source.get(Fields.embedding)));
        entity.setCreateTime(parse(string(source.get(Fields.createTime))));
        entity.setCreateBy(string(source.get(Fields.createBy)));
        entity.setCreatorId(longValue(source.get(Fields.creatorId)));
        entity.setUpdateTime(parse(string(source.get(Fields.updateTime))));
        entity.setUpdateBy(string(source.get(Fields.updateBy)));
        entity.setUpdaterId(longValue(source.get(Fields.updaterId)));
        entity.setIsDeleted(booleanValue(source.get(Fields.isDeleted)));
        return entity;
    }

    public static KnowledgeChunkES fromChunk(KnowledgeIndexedChunk chunk) {
        KnowledgeChunkES entity = new KnowledgeChunkES();
        entity.setId(chunk.getId());
        entity.setWorkspaceId(chunk.getWorkspaceId());
        entity.setOwnerUserId(chunk.getOwnerUserId());
        entity.setDocumentId(chunk.getDocumentId());
        entity.setRevisionId(chunk.getRevisionId());
        entity.setTitle(chunk.getTitle());
        entity.setChunkType(chunk.getChunkType());
        entity.setBlockId(chunk.getBlockId());
        entity.setBlockType(chunk.getBlockType());
        entity.setChunkIndex(chunk.getChunkIndex());
        entity.setHeadingPath(chunk.getHeadingPath() == null ? List.of() : new ArrayList<>(chunk.getHeadingPath()));
        entity.setContent(chunk.getContent());
        entity.setEmbedding(chunk.getEmbedding());
        entity.setCreateTime(chunk.getCreateTime());
        entity.setCreateBy(chunk.getCreateBy());
        entity.setCreatorId(chunk.getCreatorId());
        entity.setUpdateTime(chunk.getUpdateTime());
        entity.setUpdateBy(chunk.getUpdateBy());
        entity.setUpdaterId(chunk.getUpdaterId());
        entity.setIsDeleted(chunk.getIsDeleted());
        return entity;
    }

    public KnowledgeIndexedChunk toChunk() {
        KnowledgeIndexedChunk chunk = new KnowledgeIndexedChunk();
        chunk.setId(id);
        chunk.setWorkspaceId(workspaceId);
        chunk.setOwnerUserId(ownerUserId);
        chunk.setDocumentId(documentId);
        chunk.setRevisionId(revisionId);
        chunk.setTitle(title);
        chunk.setChunkType(chunkType);
        chunk.setBlockId(blockId);
        chunk.setBlockType(blockType);
        chunk.setChunkIndex(chunkIndex);
        chunk.setHeadingPath(headingPath == null ? List.of() : new ArrayList<>(headingPath));
        chunk.setContent(content);
        chunk.setEmbedding(embedding);
        chunk.setCreateTime(createTime);
        chunk.setCreateBy(createBy);
        chunk.setCreatorId(creatorId);
        chunk.setUpdateTime(updateTime);
        chunk.setUpdateBy(updateBy);
        chunk.setUpdaterId(updaterId);
        chunk.setIsDeleted(isDeleted);
        return chunk;
    }

    public Map<String, Object> toSource() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(Fields.id, id);
        source.put(Fields.workspaceId, workspaceId);
        source.put(Fields.ownerUserId, ownerUserId);
        source.put(Fields.documentId, documentId);
        source.put(Fields.revisionId, revisionId);
        source.put(Fields.title, title);
        source.put(Fields.chunkType, chunkType);
        source.put(Fields.blockId, blockId);
        source.put(Fields.blockType, blockType);
        source.put(Fields.chunkIndex, chunkIndex);
        source.put(Fields.headingPath, headingPath);
        source.put(Fields.content, content);
        source.put(Fields.embedding, embedding);
        source.put(Fields.createTime, format(createTime));
        source.put(Fields.createBy, createBy);
        source.put(Fields.creatorId, creatorId);
        source.put(Fields.updateTime, format(updateTime));
        source.put(Fields.updateBy, updateBy);
        source.put(Fields.updaterId, updaterId);
        source.put(Fields.isDeleted, isDeleted);
        return source;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static List<Float> floatList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Float> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(item instanceof Number number ? number.floatValue() : Float.parseFloat(String.valueOf(item)));
        }
        return result;
    }

}
