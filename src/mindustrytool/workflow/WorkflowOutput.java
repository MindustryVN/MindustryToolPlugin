package mindustrytool.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WorkflowOutput {
    private final String name;
    private final String description;
    private int nextId;
}
