package mindustrytool.workflow;

import java.util.List;

import lombok.Data;

@Data
public class NodeData {
    private String id;
    private int x;
    private int y;
    private String name;
    private List<NodeDataOutput> outputs;
    private List<NodeDataConsumer> consumers;
}
