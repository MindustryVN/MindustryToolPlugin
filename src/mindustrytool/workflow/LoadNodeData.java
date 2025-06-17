package mindustrytool.workflow;

import java.util.List;

import lombok.Data;

@Data
public class LoadNodeData {
    private int id;
    private int x;
    private int y;
    private String name;
    private List<Parameter> parameters;
    private List<Output> outputs;
    private List<Consumer> consumers;

    @Data
    public class Parameter {
        private String name;
        private Object value;
    }

    @Data
    public class Output {
        private String name;
        private int nextId;
    }

    @Data
    public class Consumer {
        private final String name;
        private String value;
    }
}
