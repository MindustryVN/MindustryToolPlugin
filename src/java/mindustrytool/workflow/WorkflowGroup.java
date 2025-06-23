package mindustrytool.workflow;

import lombok.Getter;

@Getter
public enum WorkflowGroup {
    EMITTER("emitter", WorkflowColor.CYAN),
    TIME("time", WorkflowColor.ORANGE),
    OPERATION("operation", WorkflowColor.PURPLE),
    ACTION("action", WorkflowColor.GREEN),
    FLOW("flow", WorkflowColor.LIME),
    DISPLAY("display", WorkflowColor.YELLOW);

    private final String name;
    private final String color;

    WorkflowGroup(String name, String color) {
        this.name = name;
        this.color = color;
    }
}
