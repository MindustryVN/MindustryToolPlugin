package mindustrytool.messages.request;

import lombok.Data;
import lombok.experimental.Accessors;
import mindustrytool.type.Team;

@Data
@Accessors(chain = true)
public class PlayerDto {
    private String name;
    private String uuid;
    private String locale;
    private Team team;
}
