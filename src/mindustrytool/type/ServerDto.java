package mindustrytool.type;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Accessors(chain = true)
public class ServerDto {

    private List<ResponseData> servers = new ArrayList<>();

    @Data
    @Accessors(chain = true)
    public static class ResponseData {
        public UUID id;
        public UUID userId;
        public String name;
        public String description;
        public String mode;
        public int port;
        public String hostCommand;
        public String status = "UNSET";
        public boolean official;
        public long ramUsage;
        public long totalRam;
        public long players;
        public String mapName;
        private List<String> mods = new ArrayList<>();
    }
}
