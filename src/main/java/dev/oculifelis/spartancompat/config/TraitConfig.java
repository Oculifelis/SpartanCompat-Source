package dev.oculifelis.spartancompat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oculifelis.spartancompat.SpartanCompat;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.neoforged.fml.loading.FMLPaths;

/** Startup settings for trait probabilities, effect lengths, damage and enchantment packages. */
public final class TraitConfig {
    private static JsonObject values;
    /** Defaults plus config/spartan_compat/traits.json, before any datapack. */
    private static JsonObject base;

    private TraitConfig() {}

    public static void load() {
        try (var in = TraitConfig.class.getResourceAsStream("/spartan_compat/traits_default.json")) {
            if (in == null) throw new IllegalStateException("traits_default.json missing");
            values = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("Could not read trait defaults", e);
        }
        var file = FMLPaths.CONFIGDIR.get().resolve(SpartanCompat.MOD_ID).resolve("traits.json");
        try {
            if (Files.exists(file)) {
                var user = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                merge(values, user, file.toString());
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(values) + "\n");
        } catch (Exception e) {
            SpartanCompat.LOG.warn("Could not read/write {}: {}", file, e.toString());
        }
        base = values.deepCopy();
    }

    /** Applies one source of overrides onto {@code target}, keeping the earlier value for bad or unknown keys. */
    static void merge(JsonObject target, JsonObject user, String source) {
        for (String key : user.keySet()) {
            if (!target.has(key)) {
                SpartanCompat.LOG.warn("Unknown trait setting {} in {}", key, source);
                continue;
            }
            try {
                double n = user.get(key).getAsDouble();
                double max = key.endsWith("_chance") || key.endsWith("_fraction") ? 1
                        : key.endsWith("_amplifier") ? 255 : key.endsWith("_level") ? 255 : 1_000_000;
                if (!Double.isFinite(n) || n < 0 || n > max) throw new IllegalArgumentException("out of range");
                target.addProperty(key, n);
            } catch (RuntimeException e) {
                SpartanCompat.LOG.warn("Invalid trait setting {} in {}, keeping default", key, source);
            }
        }
    }

    /**
     * Datapack layer: every data/&lt;namespace&gt;/spartan_compat/traits/*.json is applied on top of the config values,
     * in id order, on each world load and /reload. Values read at startup (trait magnitudes shown in tooltips,
     * Blazing Brand armour cuts, Twilight enchantment levels) keep their startup value.
     */
    public static void applyDatapacks(Map<ResourceLocation, JsonElement> files) {
        JsonObject next = base.deepCopy();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            if (e.getValue().isJsonObject()) merge(next, e.getValue().getAsJsonObject(), e.getKey().toString());
            else SpartanCompat.LOG.warn("Trait file {} is not a JSON object", e.getKey());
        });
        values = next;
    }

    public static final class Reloader extends SimpleJsonResourceReloadListener {
        public Reloader() { super(new Gson(), "spartan_compat/traits"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            applyDatapacks(files);
        }
    }

    public static float number(String key) { return values.get(key).getAsFloat(); }
    public static int integer(String key) { return (int) number(key); }
}
