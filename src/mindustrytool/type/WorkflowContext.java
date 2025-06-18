package mindustrytool.type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;
import mindustrytool.workflow.NodeData;

@Data
public class WorkflowContext {

    private List<NodeData> nodes = new ArrayList<>();

    @JsonFormat(without = { JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS })
    private Instant createdAt = Instant.now();

}
