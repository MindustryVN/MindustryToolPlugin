package mindustrytool.workflow;

import lombok.Getter;

@Getter
public enum WorkflowGroup {
    EMITTER("emitter", WorkflowColor.CYAN),
    OPERATION("operation", WorkflowColor.PURPLE),
    ACTION("action", WorkflowColor.OCEAN),
    FLOW("flow", WorkflowColor.LIME),
    DISPLAY("display", WorkflowColor.ORANGE);

    private final String name;
    private final String color;

    WorkflowGroup(String name, String color) {
        this.name = name;
        this.color = color;
    }
}
