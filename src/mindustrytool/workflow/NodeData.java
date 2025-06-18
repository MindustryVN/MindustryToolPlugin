package mindustrytool.workflow;

import java.util.List;

import lombok.Data;

@Data
public class NodeData {
    private String id;
    private int x;
    private int y;
    private String name;
    private List<Output> outputs;
    private List<Consumer> consumers;

    @Data
    public class Output {
        private String name;
        private String nextId;
    }

    @Data
    public class Consumer {
        private final String name;
        private String value;
    }
}
