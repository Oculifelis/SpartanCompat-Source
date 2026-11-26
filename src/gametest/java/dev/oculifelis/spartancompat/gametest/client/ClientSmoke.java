package dev.oculifelis.spartancompat.gametest.client;

import com.mojang.logging.LogUtils;
import dev.oculifelis.spartancompat.SpartanCompat;
import dev.oculifelis.spartancompat.content.CompatItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Dev-only client check, inert unless the JVM runs with -Dspartancompat.smoke (gradle runClient -Psmoke). Keeps the
 * window off-screen and the mouse free, makes a creative superflat world, checks every weapon has a real model and every
 * texture of ours is on the atlas, takes two screenshots and stops the client. Logs "SCTEST" lines.
 */
public final class ClientSmoke {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean offScreen;
    private static boolean creating;
    private static boolean finished;
    private static int worldTicks;
    private static int idle;
    private static long startedAt;

    private ClientSmoke() {}

    public static void arm() {
        if (System.getProperty("spartancompat.smoke") == null) return;
        LOG.info("SCTEST armed");
        startedAt = System.nanoTime();
        NeoForge.EVENT_BUS.addListener(ClientSmoke::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || finished) return;
        if (!offScreen) {
            offScreen = true;
            GLFW.glfwSetWindowPos(mc.getWindow().getWindow(), -4000, -4000);
        }
        mc.mouseHandler.releaseMouse();
        if (System.nanoTime() - startedAt > 8L * 60L * 1_000_000_000L) {
            LOG.info("SCTEST timeout");
            finish(mc);
            return;
        }
        try {
            step(mc);
        } catch (Throwable t) {
            LOG.error("SCTEST step failed", t);
            finish(mc);
        }
    }

    private static void step(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (!creating && mc.screen != null && !(mc.screen instanceof TitleScreen) && mc.getOverlay() == null && ++idle > 200) {
                mc.setScreen(new TitleScreen());
                idle = 0;
            }
            if (!creating && mc.screen instanceof TitleScreen title && mc.getOverlay() == null) {
                creating = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
                mc.options.renderDistance().set(4);
                String name = "sctest" + System.currentTimeMillis();
                LOG.info("SCTEST creating world {}", name);
                LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261111L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), title);
            }
            return;
        }
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        worldTicks++;
        if (worldTicks == 40) {
            checkModels(mc);
            checkWave3Sprites(mc);
            checkTooltips(mc);
            checkRecipes(mc);
            checkNewMaterials(mc);
            fillHotbar(mc, "pyrium");
        } else if (worldTicks == 60) {
            hitTest(mc);
        } else if (worldTicks == 70) {
            hitResult(mc);
        } else if (worldTicks == 100) {
            shot(mc, "first");
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        } else if (worldTicks == 140) {
            shot(mc, "third");
            fillHotbar(mc, "mithril");
        } else if (worldTicks == 170) {
            shot(mc, "third_mithril");
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            LOG.info("SCTEST done");
            finish(mc);
        }
    }

    private static void checkModels(Minecraft mc) {
        BakedModel missing = mc.getModelManager().getMissingModel();
        ResourceLocation missingTex = MissingTextureAtlasSprite.getLocation();
        List<String> bad = new ArrayList<>();
        for (DeferredItem<Item> item : CompatItems.WEAPONS) {
            BakedModel model = mc.getItemRenderer().getModel(new ItemStack(item.get()), mc.level, mc.player, 0);
            if (model == missing || model.getParticleIcon().contents().name().equals(missingTex)) {
                bad.add(item.getId().getPath());
            }
        }
        LOG.info("SCTEST models: {} weapons, {} missing {}", CompatItems.WEAPONS.size(), bad.size(), bad);
        int aether = 0;
        List<String> aetherBad = new ArrayList<>();
        for (DeferredItem<Item> item : CompatItems.WEAPONS) {
            String p = item.getId().getPath();
            if (!p.startsWith("gravitite_") && !p.startsWith("zanite_")) continue;
            aether++;
            if (bad.contains(p)) aetherBad.add(p);
        }
        LOG.info("SCTEST aether: loaded={} weapons={} missing {}", net.neoforged.fml.ModList.get().isLoaded("aether"), aether, aetherBad);

        TextureAtlas atlas = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        List<String> badTex = new ArrayList<>();
        int n = 0;
        for (ResourceLocation file : mc.getResourceManager().listResources("textures/item",
                r -> r.getNamespace().equals(SpartanCompat.MOD_ID) && r.getPath().endsWith(".png")).keySet()) {
            String path = file.getPath().substring("textures/".length(), file.getPath().length() - 4);
            n++;
            if (atlas.getSprite(SpartanCompat.id(path)).contents().name().equals(missingTex)) badTex.add(path);
        }
        LOG.info("SCTEST textures: {} files, {} not on atlas {}", n, badTex.size(), badTex);
    }

    private static final java.util.Set<String> WAVE3 = java.util.Set.of("botania", "enderio", "psi", "eternal_starlight",
            "naturesaura", "eidolon_repraised", "l2complements");

    private static void flatten(net.minecraft.network.chat.Component c, List<String> keys, StringBuilder text) {
        if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            keys.add(tc.getKey());
            for (Object a : tc.getArgs()) {
                if (a instanceof net.minecraft.network.chat.Component ac) flatten(ac, keys, text);
            }
        }
        text.append(c.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents p ? p.text() : "");
        for (net.minecraft.network.chat.Component sib : c.getSiblings()) flatten(sib, keys, text);
    }

    /** Every wave 3 weapon's baked sprite must be its own texture, never Spartan's iron one. Also dumps a few sprites. */
    private static void checkWave3Sprites(Minecraft mc) {
        List<String> bad = new ArrayList<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (var m : dev.oculifelis.spartancompat.config.CompatConfig.materials()) {
            if (WAVE3.contains(m.module())) names.add(m.name());
        }
        String[] dumpKinds = {"longsword", "battleaxe", "longbow", "heavy_crossbow", "halberd", "boomerang"};
        java.nio.file.Path dir = mc.gameDirectory.toPath().resolve("sctest_sprites");
        int checked = 0, dumped = 0;
        for (DeferredItem<Item> item : CompatItems.WEAPONS) {
            String path = item.getId().getPath();
            String mat = null;
            for (String n : names) if (path.startsWith(n + "_") && (mat == null || n.length() > mat.length())) mat = n;
            if (mat == null) continue;
            checked++;
            BakedModel model = mc.getItemRenderer().getModel(new ItemStack(item.get()), mc.level, mc.player, 0);
            var sprite = model.getParticleIcon();
            ResourceLocation name = sprite.contents().name();
            if (!name.getNamespace().equals(SpartanCompat.MOD_ID) || !name.getPath().startsWith("item/" + mat + "_")) {
                bad.add(path + " -> " + name);
                continue;
            }
            for (String k : dumpKinds) {
                if (path.equals(mat + "_" + k) && (mat.equals("manasteel") || mat.equals("elementium") || mat.equals("eternium")
                        || mat.equals("psi_metal") || mat.equals("amaramber"))) {
                    try {
                        java.nio.file.Files.createDirectories(dir);
                        sprite.contents().getOriginalImage().writeToFile(dir.resolve(path + ".png"));
                        dumped++;
                    } catch (Exception e) {
                        LOG.error("SCTEST dump failed", e);
                    }
                }
            }
        }
        LOG.info("SCTEST wave3 sprites: {} weapons checked, {} not their own texture {}, {} sprites dumped", checked, bad.size(), bad, dumped);
        LOG.info("SCTEST wave3 sprites {}", checked > 0 && bad.isEmpty() ? "PASS" : "FAIL");
    }

    private static void checkTooltips(Minecraft mc) {
        var lang = net.minecraft.locale.Language.getInstance();
        List<String> bad = new ArrayList<>();
        int stacks = 0, lines = 0, active = 0;
        for (var m : dev.oculifelis.spartancompat.config.CompatConfig.materials()) {
            var mod = dev.oculifelis.spartancompat.config.CompatConfig.module(m.module());
            if (mod == null || !mod.active() || !m.enabled()) continue;
            active++;
            for (DeferredItem<Item> item : CompatItems.WEAPONS) {
                String path = item.getId().getPath();
                if (!path.startsWith(m.name() + "_")) continue;
                ItemStack stack = new ItemStack(item.get());
                stacks++;
                for (boolean shift : new boolean[] {false, true}) {
                    var flag = net.minecraft.world.item.TooltipFlag.Default.NORMAL;
                    List<net.minecraft.network.chat.Component> tip = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, flag);
                    for (var line : tip) {
                        lines++;
                        List<String> keys = new ArrayList<>();
                        StringBuilder text = new StringBuilder();
                        flatten(line, keys, text);
                        for (String k : keys) if (!lang.has(k)) bad.add(path + " -> " + k);
                        String all = line.getString();
                        if (all.contains("tooltip.spartan") || all.contains("trait.spartan") || all.contains("spartan_compat.")) bad.add(path + " raw: " + all);
                    }
                    if (!shift) break;
                }
            }
        }
        LOG.info("SCTEST tooltips: {} materials active, {} stacks, {} lines, {} bad {}", active, stacks, lines,
                new java.util.LinkedHashSet<>(bad).size(), new java.util.LinkedHashSet<>(bad));
        ItemStack sample = new ItemStack(BuiltInRegistries.ITEM.get(SpartanCompat.id("elementium_longsword")));
        if (!sample.isEmpty()) {
            for (var line : sample.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL)) {
                LOG.info("SCTEST tooltip elementium_longsword | {}", line.getString());
            }
        }
    }

    private static void checkRecipes(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            var rm = server.getRecipeManager();
            java.util.Map<String, Integer> perMod = new java.util.TreeMap<>();
            int total = 0;
            for (var h : rm.getRecipes()) {
                if (!h.id().getNamespace().equals(SpartanCompat.MOD_ID)) continue;
                total++;
                String id = h.id().getPath();
                for (var m : dev.oculifelis.spartancompat.config.CompatConfig.materials()) {
                    if (WAVE3.contains(m.module()) && id.startsWith(m.name() + "_")) perMod.merge(m.name(), 1, Integer::sum);
                }
            }
            LOG.info("SCTEST recipes: {} spartan_compat total, wave3 per material {}", total, perMod);
            boolean kjs = net.neoforged.fml.ModList.get().isLoaded("kubejs");
            if (kjs) {
                boolean removed = rm.byKey(SpartanCompat.id("manasteel_longsword")).isEmpty();
                boolean added = rm.byKey(ResourceLocation.parse("kubejs:test_manasteel_longsword")).isPresent();
                boolean kept = rm.byKey(SpartanCompat.id("glacite_longsword")).isPresent();
                boolean outRemoved = rm.getRecipes().stream().noneMatch(h -> h.id().getNamespace().equals(SpartanCompat.MOD_ID)
                        && h.value().getResultItem(server.registryAccess()).is(BuiltInRegistries.ITEM.get(SpartanCompat.id("glacite_katana"))));
                var tag = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.parse("kubejs_test:marker"));
                boolean tagged = new ItemStack(BuiltInRegistries.ITEM.get(SpartanCompat.id("flowglaze_halberd"))).is(tag);
                LOG.info("SCTEST KUBEJS removedById={} addedById={} keptOther={} removedByOutput={} tagged={}", removed, added, kept, outRemoved, tagged);
                LOG.info("SCTEST KUBEJS {}", removed && added && kept && outRemoved && tagged ? "PASS" : "FAIL");
            }
        });
    }

    private static final String[] NEW_MATS = {"mithril", "pyrium", "shimmersteel"};

    /** 1.5.0 materials: every weapon has its own sprite, and the tooltip shows its trait. */
    private static void checkNewMaterials(Minecraft mc) {
        var lang = net.minecraft.locale.Language.getInstance();
        for (String mat : NEW_MATS) {
            int n = 0;
            List<String> bad = new ArrayList<>();
            for (DeferredItem<Item> item : CompatItems.WEAPONS) {
                String path = item.getId().getPath();
                if (!path.startsWith(mat + "_")) continue;
                n++;
                ResourceLocation name = mc.getItemRenderer().getModel(new ItemStack(item.get()), mc.level, mc.player, 0)
                        .getParticleIcon().contents().name();
                if (!name.getNamespace().equals(SpartanCompat.MOD_ID) || !name.getPath().startsWith("item/" + mat + "_")) bad.add(path + " -> " + name);
            }
            ItemStack sample = new ItemStack(BuiltInRegistries.ITEM.get(SpartanCompat.id(mat + "_longsword")));
            List<String> tip = new ArrayList<>();
            if (!sample.isEmpty()) {
                for (var line : sample.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL)) tip.add(line.getString());
            }
            LOG.info("SCTEST new {}: {} weapons, {} not own sprite {}, tooltip {}", mat, n, bad.size(), bad, tip);
        }
    }

    private static java.util.UUID victim;

    /** A real melee hit with the Pyrium longsword on a pig (the world is peaceful): it should be set on fire. */
    private static void hitTest(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            var sword = BuiltInRegistries.ITEM.get(SpartanCompat.id("pyrium_longsword"));
            if (player == null || sword == null || new ItemStack(sword).isEmpty()) { LOG.info("SCTEST hit pyrium skipped"); return; }
            var level = player.serverLevel();
            var z = net.minecraft.world.entity.EntityType.PIG.create(level);
            z.moveTo(player.getX() + 1.5, player.getY(), player.getZ(), 0, 0);
            z.setNoAi(true);
            level.addFreshEntity(z);
            victim = z.getUUID();
            player.getInventory().selected = 0;
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(sword));
            float before = z.getHealth();
            player.attack(z);
            LOG.info("SCTEST hit pyrium health {} -> {} onFire={}", before, z.getHealth(), z.isOnFire());
        });
    }

    private static void hitResult(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null || victim == null) return;
        server.execute(() -> {
            var e = server.overworld().getEntity(victim);
            boolean fire = e != null && (e.isOnFire() || e.getRemainingFireTicks() > 0);
            LOG.info("SCTEST hit pyrium {} (fireTicks={})", fire ? "PASS" : "FAIL", e == null ? -1 : e.getRemainingFireTicks());
            if (e != null) e.discard();
        });
    }

    private static void fillHotbar(Minecraft mc, String material) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        String[] types = {"longsword", "katana", "battle_hammer", "flanged_mace", "battleaxe", "longbow", "heavy_crossbow", "halberd", "glaive"};
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return;
            Inventory inv = player.getInventory();
            for (int i = 0; i < types.length; i++) {
                inv.setItem(i, new ItemStack(BuiltInRegistries.ITEM.get(SpartanCompat.id(material + "_" + types[i]))));
            }
            inv.selected = 0;
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "sctest_" + name + ".png", mc.getMainRenderTarget(), c -> LOG.info("SCTEST shot {}", name));
    }

    private static void finish(Minecraft mc) {
        finished = true;
        mc.stop();
    }
}
