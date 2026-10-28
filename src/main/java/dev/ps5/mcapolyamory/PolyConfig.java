package dev.ps5.mcapolyamory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class PolyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PolyConfig instance = new PolyConfig();

    /** Maximum romantic partners (promised, engaged or married) per player; 0 = unlimited. */
    public int maxPartners = 0;
    /** Lets players court villagers that are already promised, engaged or married to someone else. */
    public boolean allowTakenVillagers = true;

    public static PolyConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("mca_polyamory.json");
        try {
            if (Files.exists(path)) {
                PolyConfig loaded = GSON.fromJson(Files.readString(path), PolyConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            }
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException | RuntimeException e) {
            MCAPolyamory.LOGGER.error("Could not load {}, using defaults", path, e);
        }
    }
}
