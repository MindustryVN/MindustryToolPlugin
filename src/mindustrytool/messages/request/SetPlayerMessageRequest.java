package mindustrytool.messages.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SetPlayerMessageRequest {
    String uuid;
    boolean admin;
    String name;
}
