package mindustrytool.type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import mindustrytool.workflow.NodeData;

@Data
public class WorkflowContext {
    private Instant createdAt = Instant.now();
    private List<NodeData> nodes = new ArrayList<>();
}
