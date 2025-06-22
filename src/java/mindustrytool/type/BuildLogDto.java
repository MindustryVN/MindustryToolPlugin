package mindustrytool.type;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class BuildLogDto {
    private String message;
    private PlayerDto player;
    private BuildingDto building;

}
