package io.docpilot.workspace.infrastructure.mongo;

import io.docpilot.workspace.enums.WorkspaceNodeType;
import io.docpilot.workspace.enums.WorkspaceResourceType;
import io.docpilot.workspace.enums.WorkspaceType;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceMongoConfigTest {

    @Test
    void typeMapperCustomizerStopsWritingClassField() throws Exception {
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        WorkspaceMongoConfig config = new WorkspaceMongoConfig();
        config.workspaceMongoTypeMapperCustomizer().postProcessBeforeInitialization(converter, "mappingMongoConverter");
        converter.afterPropertiesSet();

        SampleDocument sampleDocument = new SampleDocument();
        sampleDocument.id = 1L;
        sampleDocument.name = "Personal Workspace";

        Document document = new Document();
        converter.write(sampleDocument, document);

        assertThat(document).doesNotContainKey("_class");
    }

    @Test
    void baseEnumFieldsAreStoredAsValues() throws Exception {
        WorkspaceMongoConfig config = new WorkspaceMongoConfig();
        MongoCustomConversions customConversions = config.workspaceMongoCustomConversions();
        MappingMongoConverter converter = converterWith(customConversions);

        SampleDocument sampleDocument = new SampleDocument();
        sampleDocument.workspaceType = WorkspaceType.PERSONAL;
        sampleDocument.nodeType = WorkspaceNodeType.FOLDER;
        sampleDocument.resourceType = WorkspaceResourceType.DOCUMENT;

        Document document = new Document();
        converter.write(sampleDocument, document);

        assertThat(document.get("workspaceType")).isEqualTo(1);
        assertThat(document.get("nodeType")).isEqualTo(1);
        assertThat(document.get("resourceType")).isEqualTo(1);
    }

    @Test
    void baseEnumValuesAreReadBackAsEnums() throws Exception {
        MappingMongoConverter converter = converterWith(new WorkspaceMongoConfig().workspaceMongoCustomConversions());
        Document document = new Document()
                .append("workspaceType", 1)
                .append("nodeType", 2)
                .append("resourceType", 1);

        SampleDocument sampleDocument = converter.read(SampleDocument.class, document);

        assertThat(sampleDocument.workspaceType).isEqualTo(WorkspaceType.PERSONAL);
        assertThat(sampleDocument.nodeType).isEqualTo(WorkspaceNodeType.RESOURCE);
        assertThat(sampleDocument.resourceType).isEqualTo(WorkspaceResourceType.DOCUMENT);
    }

    private MappingMongoConverter converterWith(MongoCustomConversions customConversions) throws Exception {
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(customConversions.getSimpleTypeHolder());
        mappingContext.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        converter.setCustomConversions(customConversions);
        converter.afterPropertiesSet();
        return converter;
    }

    private static class SampleDocument {

        private Long id;

        private String name;

        private WorkspaceType workspaceType;

        private WorkspaceNodeType nodeType;

        private WorkspaceResourceType resourceType;

    }

}
