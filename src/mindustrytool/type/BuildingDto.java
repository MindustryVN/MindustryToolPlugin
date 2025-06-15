package mindustrytool.type;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class BuildingDto {
    private float x;
    private float y;
    private String name;
    private String lastAccess;
}
