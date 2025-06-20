package mindustrytool.workflow;

import lombok.Data;

@Data
public class NodeDataConsumer {
    private String name;
    private String value;

    private NodeDataProduce produce;
}
