package dev.oculifelis.spartancompat.gametest;

import dev.oculifelis.spartancompat.SpartanCompat;
import dev.oculifelis.spartancompat.config.CompatConfig;
import dev.oculifelis.spartancompat.content.CompatItems;
import dev.oculifelis.spartancompat.trait.CompatTraits;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;
import org.xiyu.spartanweaponryunofficial.api.SpartanWeaponryAPI;
import org.xiyu.spartanweaponryunofficial.api.SpartanWeaponryAPI.WeaponItemType;
import org.xiyu.spartanweaponryunofficial.api.WeaponClassification;
import org.xiyu.spartanweaponryunofficial.api.WeaponMaterial;
import org.xiyu.spartanweaponryunofficial.api.trait.IMeleeTraitCallback;
import org.xiyu.spartanweaponryunofficial.api.trait.WeaponTrait;

@GameTestHolder(SpartanCompatGameTestMod.ID)
@PrefixGameTestTemplate(false)
public final class WeaponTests {
    private WeaponTests() {}

    /** Every weapon model of ours has all its own textures, and none points at another mod's sprite (no iron placeholders). */
    @GameTest(template = "arena")
    public static void modelsUseOwnTextures(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int models = 0;
        for (DeferredItem<Item> item : CompatItems.WEAPONS) {
            String base = "/assets/" + SpartanCompat.MOD_ID + "/models/item/" + item.getId().getPath();
            for (String suffix : new String[] {"", "_blocking", "_throwing", "_loaded", "_firing", "_pulling_0", "_pulling_1", "_pulling_2"}) {
                try (java.io.InputStream in = SpartanCompat.class.getResourceAsStream(base + suffix + ".json")) {
                    if (in == null) continue;
                    models++;
                    var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    if (!json.has("textures")) continue;
                    for (var t : json.getAsJsonObject("textures").entrySet()) {
                        String v = t.getValue().getAsString();
                        if (t.getKey().startsWith("layer") && !v.startsWith(SpartanCompat.MOD_ID + ":")) problems.add(item.getId().getPath() + suffix + " " + t.getKey() + " -> " + v);
                        if (v.startsWith(SpartanCompat.MOD_ID + ":")
                                && SpartanCompat.class.getResource("/assets/" + SpartanCompat.MOD_ID + "/textures/" + v.substring(v.indexOf(':') + 1) + ".png") == null) {
                            problems.add(item.getId().getPath() + suffix + " missing " + v);
                        }
                    }
                } catch (java.io.IOException e) {
                    problems.add(item.getId().getPath() + suffix + " " + e);
                }
            }
        }
        if (models == 0) helper.fail("no weapon models found");
        if (!problems.isEmpty()) helper.fail(problems.size() + " model texture problems, first: " + problems.subList(0, Math.min(5, problems.size())));
        helper.succeed();
    }

    /** Every active material has all 24 weapon types, each classified back to its own material and type. */
    @GameTest(template = "arena")
    public static void everyWeaponRegisters(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, WeaponMaterial> e : CompatItems.MATERIALS.entrySet()) {
            for (WeaponItemType type : WeaponItemType.values()) {
                String id = CompatItems.itemId(e.getKey(), type);
                Item item = BuiltInRegistries.ITEM.get(SpartanCompat.id(id));
                if (item == Items.AIR) { problems.add(id + " missing"); continue; }
                WeaponClassification c = SpartanWeaponryAPI.getWeaponClassification(item).orElse(null);
                if (c == null || c.weaponItemType() != type || c.material() != e.getValue()) problems.add(id + " classified as " + c);
            }
        }
        int expected = CompatItems.MATERIALS.size() * WeaponItemType.values().length;
        helper.assertTrue(CompatItems.WEAPONS.size() == expected, "weapon count " + CompatItems.WEAPONS.size() + " != " + expected);
        helper.assertTrue(problems.isEmpty(), "weapon problems: " + problems);
        SpartanCompat.LOG.info("[test] {} materials, {} weapons registered", CompatItems.MATERIALS.size(), CompatItems.WEAPONS.size());
        helper.succeed();
    }

    /**
     * Each weapon whose ingot exists has exactly one recipe making it; each weapon whose ingot is missing (a module
     * forced on without its mod) has none.
     */
    @GameTest(template = "arena")
    public static void everyRecipeResolves(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        Map<Item, Integer> counts = new java.util.HashMap<>();
        for (RecipeHolder<?> holder : helper.getLevel().getRecipeManager().getRecipes()) {
            if (!holder.id().getNamespace().equals(SpartanCompat.MOD_ID)) continue;
            ItemStack out = holder.value().getResultItem(access);
            counts.merge(out.getItem(), 1, Integer::sum);
        }
        List<String> problems = new ArrayList<>();
        int resolved = 0;
        for (DeferredItem<Item> item : CompatItems.WEAPONS) {
            String path = item.getId().getPath();
            CompatConfig.Material m = CompatItems.SETTINGS.get(CompatItems.MATERIAL_OF.get(path));
            // recipes also need the module's mod: a vanilla ingot (lapis) with the mod absent gives none
            boolean ingotExists = BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(m.ingot()))
                    && ModList.get().isLoaded(m.module());
            int n = counts.getOrDefault(item.get(), 0);
            // base_twin materials step aside while base Spartan Weaponry crafts the same weapon from the same ingot
            if (ingotExists && BASE_TWINS.contains(m.name())) {
                if (n != 0) problems.add(path + " has " + n + " recipes but base Spartan Weaponry already makes it");
            } else if (ingotExists && n != (m.name().equals("fiery") ? 2 : 1)) problems.add(path + " has " + n + " recipes");
            if (!ingotExists && n != 0) problems.add(path + " has a recipe but " + m.ingot() + " is missing");
            if (n > 0) resolved++;
        }
        helper.assertTrue(problems.isEmpty(), "recipe problems: " + problems);
        SpartanCompat.LOG.info("[test] {} of {} weapon recipes resolved", resolved, CompatItems.WEAPONS.size());
        helper.succeed();
    }

    /** materials_default.json entries with base_twin (base material left on in the test config). */
    private static final Set<String> BASE_TWINS = Set.of("silver");

    /** Materials whose source tool has no weapon ability of its own, so they carry no trait. */
    private static final Set<String> NO_TRAIT = Set.of("amaramber", "deepsilver", "starlit_diamond", "sculkium");

    /** Every material's trait tag loaded and holds at least one trait; force-enabled ones too. */
    @GameTest(template = "arena")
    public static void everyMaterialHasTraits(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, WeaponMaterial> e : CompatItems.MATERIALS.entrySet()) {
            if (!e.getValue().hasAnyBonusTraits() && !NO_TRAIT.contains(e.getKey())) problems.add(e.getKey());
        }
        helper.assertTrue(problems.isEmpty(), "materials without traits: " + problems);
        helper.succeed();
    }

    /** Unbreakable and fireproof materials carry those components on every weapon. */
    @GameTest(template = "arena")
    public static void componentsApplied(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (DeferredItem<Item> item : CompatItems.WEAPONS) {
            String path = item.getId().getPath();
            CompatConfig.Material m = CompatItems.SETTINGS.get(CompatItems.MATERIAL_OF.get(path));
            ItemStack stack = new ItemStack(item.get());
            if (m.unbreakable() != stack.has(DataComponents.UNBREAKABLE)) problems.add(path + " unbreakable");
            if (m.fireproof() != stack.has(DataComponents.FIRE_RESISTANT)) problems.add(path + " fireproof");
        }
        helper.assertTrue(problems.isEmpty(), "component problems: " + problems);
        helper.succeed();
    }

    /** Spot checks of trait numbers against a real mob: armoured bonus, water bonus, item lookups. */
    @GameTest(template = "arena")
    public static void traitEffects(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        DamageSource src = helper.getLevel().damageSources().generic();
        WeaponMaterial any = CompatItems.MATERIALS.values().iterator().next();
        IMeleeTraitCallback armored = CompatTraits.ARMORED_DAMAGE_BONUS.get().getMeleeCallback().orElseThrow();
        helper.assertTrue(armored.modifyDamageDealt(any, 5f, src, zombie, zombie) == 7f, "natural armor bonus missing");
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        helper.assertTrue(armored.modifyDamageDealt(any, 5f, src, zombie, zombie) == 5f, "partial armor coverage should round bonus down");

        ItemStack worn = new ItemStack(Items.IRON_SWORD);
        helper.assertTrue(CompatTraits.wornBonus(worn, 4f) == 0f, "zanite bonus on a new weapon");
        worn.setDamageValue(worn.getMaxDamage() / 2);
        helper.assertTrue(Math.abs(CompatTraits.wornBonus(worn, 4f) - 2f) < 0.05f, "zanite bonus wrong at half durability");
        helper.assertFalse(CompatTraits.isRotspawn(zombie), "zombie counted as rotspawn");

        IMeleeTraitCallback might = CompatTraits.NEPTUNES_MIGHT.get().getMeleeCallback().orElseThrow();
        helper.assertTrue(might.modifyDamageDealt(any, 4f, src, zombie, zombie) == 4f, "water bonus on dry zombie");
        helper.assertTrue(CompatTraits.NEPTUNES_MIGHT.get().isThrowingTrait(), "Might not available on throwing weapons");
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.WATER);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(zombie.isInWater(), "zombie not in water");
            helper.assertFalse(zombie.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "eyes unexpectedly submerged");
            helper.assertTrue(might.modifyDamageDealt(any, 4f, src, zombie, zombie) == 4f, "bonus with only feet wet");
            helper.setBlock(new BlockPos(3, 3, 3), Blocks.WATER);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(zombie.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "eyes not submerged");
                helper.assertTrue(might.modifyDamageDealt(any, 4f, src, zombie, zombie) == 6f, "bonus missing with eyes underwater");
                helper.succeed();
            });
        });
    }

    /** The trait each material is meant to have is the one its loaded tag gave it. */
    @GameTest(template = "arena")
    public static void materialTraitsMatchTable(GameTestHelper helper) {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("ironwood", "spartan_compat:ironwood_enchantments"),
                Map.entry("steeleaf", "spartan_compat:steeleaf_enchantments"),
                Map.entry("knightmetal", "spartan_compat:armored_damage_bonus"),
                Map.entry("fiery", "spartan_compat:ignite"),
                Map.entry("neptunium", "spartan_compat:neptunes_strike"),
                Map.entry("ignitium", "spartan_compat:blazing_brand"),
                Map.entry("cursium", "spartan_compat:accursed_rage"),
                Map.entry("witherite", "spartan_compat:mecha_pulse"),
                Map.entry("ancient_metal", "spartan_weaponry_unofficial:armor_piercing"),
                Map.entry("black_steel", "spartan_weaponry_unofficial:shield_breach"),
                Map.entry("necromium", "spartan_compat:numbing"),
                Map.entry("thaumium", "spartan_weaponry_unofficial:lightweight_1"),
                Map.entry("void_metal", "spartan_compat:self_repair"),
                Map.entry("starsteel", "spartan_weaponry_unofficial:lightweight_2"),
                Map.entry("eclipse", "spartan_compat:weakening"),
                Map.entry("cloggrum", "spartan_weaponry_unofficial:heavy_1"),
                Map.entry("froststeel", "spartan_compat:numbing"),
                Map.entry("utherium", "spartan_compat:rotbane"),
                Map.entry("forgotten", "spartan_weaponry_unofficial:armor_piercing"),
                Map.entry("resonarium", "spartan_weaponry_unofficial:extra_knockback"),
                Map.entry("warden", "spartan_compat:darkening"),
                Map.entry("zanite", "spartan_compat:zanite_strength"),
                Map.entry("gravitite", "spartan_compat:gravity_strike"),
                Map.entry("allthemodium", "spartan_weaponry_unofficial:shield_breach"),
                Map.entry("vibranium", "spartan_weaponry_unofficial:armor_piercing"),
                Map.entry("unobtainium", "spartan_compat:self_repair"),
                Map.entry("osmium", "spartan_weaponry_unofficial:heavy_2"),
                Map.entry("refined_obsidian", "spartan_weaponry_unofficial:heavy_2"),
                Map.entry("refined_glowstone", "spartan_compat:glow_mark"),
                Map.entry("lapis", "spartan_compat:bountiful"),
                Map.entry("soul_stained_steel", "spartan_weaponry_unofficial:armor_piercing"),
                Map.entry("hallowed_gold", "spartan_weaponry_unofficial:undead_damage_bonus"),
                Map.entry("deorum", "spartan_weaponry_unofficial:lightweight_2"),
                Map.entry("draco_arcanus", "spartan_compat:ignite"),
                Map.entry("silver", "spartan_weaponry_unofficial:undead_damage_bonus"),
                Map.entry("manasteel", "spartan_compat:mana_repair"),
                Map.entry("elementium", "spartan_compat:pixie_affinity"),
                Map.entry("terrasteel", "spartan_compat:mana_repair"),
                Map.entry("dark_steel", "spartan_compat:empowered"),
                Map.entry("psimetal", "spartan_compat:psi_programmable"),
                Map.entry("flowglaze", "spartan_compat:concentration"),
                Map.entry("glacite", "spartan_compat:frostbite"),
                Map.entry("malarite", "spartan_compat:malarite_poison"),
                Map.entry("starfire", "spartan_compat:starfire"),
                Map.entry("thermal_springstone", "spartan_compat:scorching"),
                Map.entry("unrealium", "spartan_compat:unrealium_heft"),
                Map.entry("infused_iron", "spartan_compat:na_slowing"),
                Map.entry("sky", "spartan_compat:na_levitating"),
                Map.entry("depth", "spartan_compat:depth_sweep"),
                Map.entry("totemic_gold", "spartan_compat:totemic"),
                Map.entry("poseidite", "spartan_compat:tidal"),
                Map.entry("shulkerate", "spartan_compat:shulker_reach"),
                Map.entry("eternium", "spartan_compat:eternium"),
                Map.entry("arcane", "spartan_weaponry_unofficial:lightweight_1"),
                Map.entry("mithril", "spartan_weaponry_unofficial:armor_piercing"),
                Map.entry("pyrium", "spartan_compat:ignite"),
                Map.entry("shimmersteel", "spartan_compat:self_repair"),
                Map.entry("pyro_spellweave", "spartan_compat:ignite"),
                Map.entry("verdant_spellweave", "spartan_compat:na_slowing"),
                Map.entry("void_spellweave", "spartan_compat:darkening"),
                Map.entry("abyssal_spellweave", "spartan_compat:neptunes_might"));
        var registry = helper.getLevel().registryAccess().registryOrThrow(org.xiyu.spartanweaponryunofficial.api.WeaponTraits.REGISTRY_KEY);
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, WeaponMaterial> e : CompatItems.MATERIALS.entrySet()) {
            String want = expected.get(e.getKey());
            if (want == null) continue;
            Set<String> have = new HashSet<>();
            for (WeaponTrait t : e.getValue().getBonusTraits()) have.add(String.valueOf(registry.getKey(t)));
            if (!have.contains(want)) problems.add(e.getKey() + " lacks " + want + " (has " + have + ")");
        }
        helper.assertTrue(problems.isEmpty(), "trait problems: " + problems);
        helper.assertTrue(ModList.get().isLoaded(SpartanCompat.MOD_ID), "mod not loaded");
        helper.succeed();
    }
}
