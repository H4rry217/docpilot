package io.docpilot.config;

import io.docpilot.document.application.DocumentManager;
import io.docpilot.document.application.WorkspaceManager;
import io.docpilot.document.model.CreateDocumentCommand;
import io.docpilot.document.model.CreateWorkspaceCommand;
import io.docpilot.document.model.CreateWorkspaceNodeCommand;
import io.docpilot.document.model.DocPilotDocument;
import io.docpilot.document.model.Workspace;
import io.docpilot.document.model.WorkspaceNode;
import io.docpilot.document.model.WorkspaceNodeType;
import io.docpilot.document.repository.WorkspaceNodeRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DevDataInitializer {

    private static final String JOURNEY_TO_THE_WEST_MARKDOWN = """
            # 《西游记》节选

            ## 第一回 灵根育孕源流出

            东胜神洲海外有一国土，名曰傲来国。国近大海，海中有一座名山，唤为花果山。那山顶上有一块仙石，受天地方精华，感日月灵气，久而久之，竟迸裂出一只石猴。

            那猴：12e21e21e21

            - 目运金光dsadsa1d21d21f1e21e2121
            - 声震山林dwqdwqfwqfwqf3f2f3g433g22g4f3f342f323
            - 行走如飞fwqfwqfwqfwqfwqfwqfwqf21f2qdwqd21d21

            群猴见了，无不惊异。wqfwqf11dsadsa

            ---

            ## 第二回 水帘洞称王

            一日，众猴在山间嬉戏。d332df321

            忽听得：

            > “哪里有水流，哪里便有源头。”

            石猴胆大，纵身跃入瀑布，只见其中别有洞天。

            ### 洞内景象

            桥边有花，洞中有石，石上刻着“花果山福地，水帘洞洞天”。
            """;

    private final WorkspaceManager workspaceManager;
    private final DocumentManager documentManager;
    private final WorkspaceNodeRepository workspaceNodeRepository;

    public DevDataInitializer(WorkspaceManager workspaceManager,
                              DocumentManager documentManager,
                              WorkspaceNodeRepository workspaceNodeRepository) {
        this.workspaceManager = workspaceManager;
        this.documentManager = documentManager;
        this.workspaceNodeRepository = workspaceNodeRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!workspaceManager.listMyWorkspaces().isEmpty()) {
            return;
        }

        Workspace workspace = createWorkspace("新工作区");
        WorkspaceNode folder = createNode(workspace.getWorkspaceId(), workspace.getRootNodeId(), WorkspaceNodeType.FOLDER, "新建文件夹", null, 10);
        createNode(workspace.getWorkspaceId(), folder.getNodeId(), WorkspaceNodeType.FOLDER, "新建文件夹", null, 10);
        createDocumentNode(workspace.getWorkspaceId(), folder.getNodeId(), "未命名 2.md", "# 未命名 2\n\n这里可以记录新的想法。", 20);
        createDocumentNode(workspace.getWorkspaceId(), folder.getNodeId(), "未命名.md", "# 未命名\n\n空白文档。", 30);
        createDocumentNode(workspace.getWorkspaceId(), folder.getNodeId(), "未命名 2.md", "# 未命名 2\n\n另一个草稿。", 40);
        createDocumentNode(workspace.getWorkspaceId(), folder.getNodeId(), "未命名 3.md", "# 未命名 3\n\n更多材料。", 50);
        createDocumentNode(workspace.getWorkspaceId(), folder.getNodeId(), "未命名.md", "# 未命名\n\n继续整理。", 60);
        createDocumentNode(workspace.getWorkspaceId(), folder.getNodeId(), "西游记.md", JOURNEY_TO_THE_WEST_MARKDOWN, 70);
    }

    private Workspace createWorkspace(String name) {
        CreateWorkspaceCommand command = new CreateWorkspaceCommand();
        command.setName(name);
        return workspaceManager.createWorkspace(command);
    }

    private void createDocumentNode(String workspaceId, String parentNodeId, String name, String markdown, long sortOrder) {
        CreateDocumentCommand documentCommand = new CreateDocumentCommand();
        documentCommand.setTitle(name);
        documentCommand.setMarkdown(markdown);
        DocPilotDocument document = documentManager.createDocument(documentCommand);
        createNode(workspaceId, parentNodeId, WorkspaceNodeType.DOCUMENT, name, document.getDocumentId(), sortOrder);
    }

    private WorkspaceNode createNode(String workspaceId, String parentNodeId, WorkspaceNodeType type,
                                     String name, String documentId, long sortOrder) {
        CreateWorkspaceNodeCommand command = new CreateWorkspaceNodeCommand();
        command.setWorkspaceId(workspaceId);
        command.setParentNodeId(parentNodeId);
        command.setType(type);
        command.setName(name);
        command.setDocumentId(documentId);
        WorkspaceNode node = workspaceManager.createNode(command);
        node.setSortOrder(sortOrder);
        return workspaceNodeRepository.save(node);
    }

}
