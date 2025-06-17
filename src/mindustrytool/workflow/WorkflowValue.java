package mindustrytool.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WorkflowValue {
    private final String name;
    private final Object value;
}
