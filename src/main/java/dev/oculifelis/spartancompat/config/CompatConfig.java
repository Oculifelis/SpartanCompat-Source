package dev.oculifelis.spartancompat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oculifelis.spartancompat.SpartanCompat;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Reads config/spartan_compat/materials.json at mod construction (items register from it, so it cannot be a
 * normal reloadable config). The file is rewritten with every known key on each start; missing keys fall back to
 * the built-in table, so a file from an older version keeps working.
 */
public final class CompatConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record Module(String modId, boolean enabled, boolean forceEnable) {
        /** Register this module's items: switched on, and the target mod is present or forced. */
        public boolean active() {
            return enabled && (forceEnable || ModList.get().isLoaded(modId));
        }
    }

    public record Material(String name, String display, String module, String ingot, String ingotTag,
                           boolean upgradeFromNetherite, boolean enabled, int durability, float speed, float damage,
                           int enchantability, String incorrectFor, int primaryColour, int secondaryColour,
                           boolean fireproof, boolean unbreakable, String rarity) {}

    private static final Map<String, Module> MODULES = new LinkedHashMap<>();
    private static final List<Material> MATERIALS = new ArrayList<>();

    private CompatConfig() {}

    public static Map<String, Module> modules() { return Collections.unmodifiableMap(MODULES); }
    public static List<Material> materials() { return Collections.unmodifiableList(MATERIALS); }
    public static Module module(String id) { return MODULES.get(id); }

    public static void load() {
        MODULES.clear();
        MATERIALS.clear();
        JsonObject defaults = readDefaults();
        Path file = FMLPaths.CONFIGDIR.get().resolve(SpartanCompat.MOD_ID).resolve("materials.json");
        JsonObject user = new JsonObject();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    user = JsonParser.parseReader(r).getAsJsonObject();
                }
            }
        } catch (Exception e) {
            SpartanCompat.LOG.error("Could not read {}, using defaults: {}", file, e.toString());
            user = new JsonObject();
        }
        JsonObject userModules = obj(user, "modules");
        JsonObject userMaterials = obj(user, "materials");

        JsonObject outModules = new JsonObject();
        for (JsonElement m : defaults.getAsJsonArray("modules")) {
            String id = m.getAsString();
            JsonObject u = obj(userModules, id);
            boolean enabled = bool(u, "enabled", !defaults.getAsJsonArray("review_pending_modules").contains(m));
            boolean force = bool(u, "force_enable", false);
            MODULES.put(id, new Module(id, enabled, force));
            JsonObject o = new JsonObject();
            o.addProperty("enabled", enabled);
            o.addProperty("force_enable", force);
            outModules.add(id, o);
        }

        JsonObject outMaterials = new JsonObject();
        for (JsonElement e : defaults.getAsJsonArray("materials")) {
            JsonObject d = e.getAsJsonObject();
            String name = d.get("name").getAsString();
            JsonObject u = obj(userMaterials, name);
            boolean enabled = bool(u, "enabled", true);
            int durability = (u.has("durability") ? u : d).get("durability").getAsInt();
            float speed = (u.has("speed") ? u : d).get("speed").getAsFloat();
            float damage = (u.has("damage") ? u : d).get("damage").getAsFloat();
            int ench = (u.has("enchantability") ? u : d).get("enchantability").getAsInt();
            boolean unbreakable = bool(u, "unbreakable", bool(d, "unbreakable", false));
            JsonObject rs = u.has("rarity") ? u : d;
            String rarity = rs.has("rarity") ? rs.get("rarity").getAsString().toLowerCase(java.util.Locale.ROOT) : "common";
            if (!java.util.Set.of("common", "uncommon", "rare", "epic").contains(rarity)) {
                SpartanCompat.LOG.warn("Material {} has unknown rarity '{}', using common", name, rarity);
                rarity = "common";
            }
            JsonArray colours = d.getAsJsonArray("colours");
            MATERIALS.add(new Material(name, d.get("display").getAsString(), d.get("module").getAsString(),
                    d.get("ingot").getAsString(), d.has("ingot_tag") ? d.get("ingot_tag").getAsString() : null,
                    bool(d, "upgrade_from_netherite", false), enabled, durability, speed, damage, ench,
                    d.get("incorrect_for").getAsString(), Integer.parseInt(colours.get(0).getAsString(), 16),
                    Integer.parseInt(colours.get(1).getAsString(), 16), bool(d, "fireproof", false), unbreakable,
                    "common".equals(rarity) ? null : rarity));
            JsonObject o = new JsonObject();
            o.addProperty("enabled", enabled);
            o.addProperty("durability", durability);
            o.addProperty("speed", speed);
            o.addProperty("damage", damage);
            o.addProperty("enchantability", ench);
            o.addProperty("unbreakable", unbreakable);
            o.addProperty("rarity", rarity);
            outMaterials.add(name, o);
        }

        JsonObject out = new JsonObject();
        out.addProperty("_help", "modules: enabled (default true) and force_enable (register the weapons even when the"
                + " target mod is missing; their recipes still need the ingot to exist). materials: enabled, the tier"
                + " numbers, unbreakable, and rarity (common, uncommon, rare or epic); damage is the bonus over the weapon type's base damage, like a vanilla tool tier."
                + " Changes apply after a restart. Traits come from the data tag"
                + " spartan_compat:materials/<name> (weapon_traits), so a datapack can change them.");
        out.add("modules", outModules);
        out.add("materials", outMaterials);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(out), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            SpartanCompat.LOG.warn("Could not write {}: {}", file, ex.toString());
        }
    }

    private static JsonObject readDefaults() {
        try (InputStream in = CompatConfig.class.getResourceAsStream("/spartan_compat/materials_default.json")) {
            if (in == null) throw new IllegalStateException("materials_default.json missing from the jar");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("Spartan Compat could not read its material table", e);
        }
    }

    private static JsonObject obj(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }
}
