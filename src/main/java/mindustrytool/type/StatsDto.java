package mindustrytool.type;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class StatsDto {
    private long tps = 0;
    private long ramUsage = 0;
    private long totalRam = 0;
    private int players = 1;
    private String mapName = "DEBUG";
    private List<ModDto> mods = new ArrayList<>();
    private String status = "SERVER_UNSET";
    private int kicks = 0;
    private boolean isPaused = false;
    private boolean isHosting = false;
    private String version = "custom";
}
