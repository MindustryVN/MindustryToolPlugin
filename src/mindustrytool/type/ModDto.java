package mindustrytool.type;

import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;
import mindustry.mod.Mods.ModMeta;

@Data
@Accessors(chain = true)
public class ModDto {
    private String name;
    private String filename;
    private ModMeta meta;

    @Data
    @Accessors(chain = true)
    public class ModMetaDto {
        private String name;
        private String internalName;
        private String minGameVersion = "0";
        private String displayName, author, description, subtitle, version, main, repo;
        private List<String> dependencies = List.of();
        private boolean hidden;
        private boolean java;
    }
}
