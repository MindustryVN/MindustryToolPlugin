package mindustrytool.type;

import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StatsDto {
    private long ramUsage;
    private long totalRam;
    private int players;
    private String mapName;
    private byte[] mapData;
    private List<ModDto> mods;
    private String status;
    private int kicks;
    private boolean isPaused = false;
}
