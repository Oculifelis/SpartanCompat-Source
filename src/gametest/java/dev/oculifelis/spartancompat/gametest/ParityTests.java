package dev.oculifelis.spartancompat.gametest;

import dev.oculifelis.spartancompat.SpartanCompat;
import dev.oculifelis.spartancompat.config.TraitConfig;
import dev.oculifelis.spartancompat.content.CompatItems;
import dev.oculifelis.spartancompat.content.TwilightEnchantments;
import dev.oculifelis.spartancompat.effect.CompatEffects;
import dev.oculifelis.spartancompat.trait.CompatTraits;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.xiyu.spartanweaponryunofficial.api.SpartanWeaponryAPI.WeaponItemType;
import org.xiyu.spartanweaponryunofficial.util.WeaponType;

@GameTestHolder(SpartanCompatGameTestMod.ID)
@PrefixGameTestTemplate(false)
public final class ParityTests {
    private ParityTests() {}

    private static final class ThrownProbe extends org.xiyu.spartanweaponryunofficial.entity.projectile.ThrowingWeaponEntity {
        ThrownProbe(net.minecraft.world.entity.LivingEntity owner, ItemStack stack) {
            super(org.xiyu.spartanweaponryunofficial.init.ModEntities.THROWING_WEAPON.get(), owner, owner.level(), stack);
            setWeapon(stack);
            setBaseDamage(4);
        }

        void strike(net.minecraft.world.entity.LivingEntity target) {
            super.onHitEntity(new net.minecraft.world.phys.EntityHitResult(target));
        }
    }

    @GameTest(template = "arena")
    public static void thrownNeptuniumDamage(GameTestHelper h) {
        if (!CompatItems.MATERIALS.containsKey("neptunium")) { h.succeed(); return; }
        var owner = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        var dry = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 2, 2));
        var wet = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 2, 4));
        for (var target : new net.minecraft.world.entity.LivingEntity[]{dry, wet}) {
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            target.setHealth(100);
            target.setNoGravity(true);
        }
        h.setBlock(new BlockPos(4, 2, 4), net.minecraft.world.level.block.Blocks.WATER);
        h.setBlock(new BlockPos(4, 3, 4), net.minecraft.world.level.block.Blocks.WATER);
        h.runAfterDelay(5, () -> {
            h.assertTrue(wet.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "Thrown test victim's eyes are dry");
            var knife = BuiltInRegistries.ITEM.get(SpartanCompat.id("neptunium_throwing_knife"));
            new ThrownProbe(owner, new ItemStack(knife)).strike(dry);
            new ThrownProbe(owner, new ItemStack(knife)).strike(wet);
            float dryDamage = 100 - dry.getHealth(), wetDamage = 100 - wet.getHealth();
            h.assertTrue(dryDamage > 0 && Math.abs(wetDamage - dryDamage * 1.5f) < 0.001f,
                    "Actual thrown damage: dry=" + dryDamage + ", wet=" + wetDamage);
            h.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void rangedTraitsUseFiringMaterial(GameTestHelper h) {
        var material = CompatItems.MATERIALS.get("necromium");
        if (material == null) { h.succeed(); return; }
        var attacker = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        var target = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 2, 2));
        var arrow = new net.minecraft.world.entity.projectile.Arrow(h.getLevel(), attacker,
                new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        CompatTraits.NUMBING.get().getRangedCallback().orElseThrow().onProjectileSpawn(material, arrow);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
        target.hurt(h.getLevel().damageSources().arrow(arrow, attacker), 4);
        var slow = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(slow != null && slow.getAmplifier() == 1 && slow.getDuration() == 120,
                "Ranged trait lost after switching the held weapon");
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void enchantmentPackages(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (String material : new String[]{"ironwood", "steeleaf"}) {
            if (!CompatItems.MATERIALS.containsKey(material)) continue;
            var keys = material.equals("ironwood")
                    ? new String[]{"minecraft:knockback", "minecraft:fortune", "spartan_weaponry_unofficial:propel", "minecraft:punch"}
                    : new String[]{"minecraft:looting", "minecraft:efficiency", "spartan_weaponry_unofficial:lucky_throw", "minecraft:quick_charge"};
            for (WeaponItemType type : WeaponItemType.values()) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(SpartanCompat.id(CompatItems.itemId(material, type))));
                TwilightEnchantments.initialize(stack, h.getLevel().registryAccess());
                int supported = 0;
                for (String key : keys) {
                    var enchantment = registry.getHolder(ResourceLocation.parse(key)).orElseThrow();
                    if (!enchantment.value().isSupportedItem(stack)) continue;
                    supported++;
                    h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) == (material.equals("ironwood") ? 1 : 2),
                            material + " " + type + " missing " + key);
                }
                boolean noApplicableEnchantment = material.equals("ironwood") && type == WeaponItemType.HEAVY_CROSSBOW
                        || material.equals("steeleaf") && type == WeaponItemType.LONGBOW;
                h.assertTrue(supported > 0 || noApplicableEnchantment, "No package enchantment supports " + material + " " + type);
                EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.removeIf(e -> true));
                TwilightEnchantments.initialize(stack, h.getLevel().registryAccess());
                h.assertTrue(stack.getEnchantments().isEmpty(), "Grindstone removal did not persist");
            }
        }
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void universalCallbacksAndCataclysm(GameTestHelper h) {
        var attacker = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 2));
        var target = h.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 2));
        var material = CompatItems.MATERIALS.values().iterator().next();
        var source = h.getLevel().damageSources().mobAttack(attacker);
        for (var trait : new org.xiyu.spartanweaponryunofficial.api.trait.WeaponTrait[]{
                CompatTraits.NUMBING.get(), CompatTraits.IGNITE.get(), CompatTraits.ARMORED_DAMAGE_BONUS.get(),
                CompatTraits.BLAZING_BRAND.get(), CompatTraits.ACCURSED_RAGE.get(), CompatTraits.MECHA_SMITE.get(), CompatTraits.MECHA_PULSE.get()}) {
            h.assertTrue(trait.isMeleeTrait() && trait.isRangedTrait() && trait.isThrowingTrait(), trait + " not universal");
        }
        CompatTraits.NUMBING.get().getMeleeCallback().orElseThrow().onHitEntity(material, ItemStack.EMPTY, target, attacker, null);
        var slow = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(slow != null && slow.getAmplifier() == 1 && slow.getDuration() == 120, "Slowness II / 120 ticks missing");
        var brand = CompatTraits.BLAZING_BRAND.get().getMeleeCallback().orElseThrow();
        attacker.getRandom().setSeed(0); // first roll is below 0.75
        attacker.setHealth(5);
        brand.modifyDamageDealt(material, 5, source, attacker, target);
        var effect = target.getEffect(CompatEffects.BLAZING_BRAND);
        h.assertTrue(effect != null && effect.getDuration() == 240 && effect.getAmplifier() == 0, "Brand default differs from checked source");
        h.assertTrue(Math.abs(attacker.getHealth() - 6.25f) < 0.001, "Brand healing is not per stack");
        double attackBeforeRage = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        var rage = CompatTraits.ACCURSED_RAGE.get().getMeleeCallback().orElseThrow();
        attacker.getRandom().setSeed(4096); // first roll is below 0.66
        float rageDamage = rage.modifyDamageDealt(material, 5, source, attacker, target);
        h.assertTrue(attacker.hasEffect(CompatEffects.ACCURSED_RAGE) && rageDamage == 6, "Rage did not add weapon-specific damage");
        h.assertTrue(attacker.getEffect(CompatEffects.ACCURSED_RAGE).getDuration() == 110, "Rage duration");
        h.assertTrue(attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) == attackBeforeRage, "Rage affects unrelated weapons");
        // Zombies resist regeneration; use a living animal to verify all Smite effects.
        var living = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 4));
        var victim = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 2, 4));
        living.setHealth(2);
        living.getRandom().setSeed(2);
        CompatTraits.MECHA_SMITE.get().getMeleeCallback().orElseThrow().onHitEntity(material, ItemStack.EMPTY, victim, living, null);
        h.assertTrue(victim.getEffect(MobEffects.WITHER).getAmplifier() == 1 && victim.getEffect(MobEffects.WITHER).getDuration() == 100, "Smite Wither II / 100 ticks");
        h.assertTrue(victim.getRemainingFireTicks() == 100, "Smite fire is not five seconds");
        var regen = living.getEffect(MobEffects.REGENERATION);
        h.assertTrue(regen != null && regen.getAmplifier() == 1 && regen.getDuration() == 100, "Smite regeneration chance/duration/amplifier");
        CompatTraits.IGNITE.get().getMeleeCallback().orElseThrow().onHitEntity(material, ItemStack.EMPTY, victim, living, null);
        h.assertTrue(victim.getRemainingFireTicks() == 300, "Fiery ignition is not fifteen seconds");
        var pulse = CompatTraits.MECHA_PULSE.get().getMeleeCallback().orElseThrow();
        for (int i = 0; i < 9; i++) {
            attacker.getRandom().setSeed(0);
            h.assertTrue(pulse.modifyDamageDealt(material, 5, source, attacker, target) == 5, "Pulse fired before ten charges");
        }
        h.assertTrue(attacker.getEffect(CompatEffects.PULSE_CHARGE).getAmplifier() == 8, "Charge amplifier");
        attacker.getRandom().setSeed(0);
        h.assertTrue(pulse.modifyDamageDealt(material, 5, source, attacker, target) == 9, "Pulse damage");
        h.assertTrue(attacker.hasEffect(CompatEffects.PULSE_COOLDOWN) && !attacker.hasEffect(CompatEffects.PULSE_CHARGE), "Pulse reset and cooldown");
        h.assertTrue(attacker.getEffect(CompatEffects.PULSE_COOLDOWN).getDuration() == 800, "Pulse cooldown length");
        h.assertTrue(pulse.modifyDamageDealt(material, 5, source, attacker, target) == 5 && !attacker.hasEffect(CompatEffects.PULSE_CHARGE), "Pulse charged during cooldown");
        var stun = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("cataclysm:stun"));
        h.assertTrue(stun.isPresent() ? target.hasEffect(stun.get()) : target.hasEffect(CompatEffects.STUN), "Pulse stun missing");
        h.succeed();
    }

    /** Spartan Cataclysm extras: rarity, templates, smelting, explosion tag, the fixed regen threshold and zero-length switches. */
    @GameTest(template = "arena")
    public static void cataclysmExtras(GameTestHelper h) {
        var level = h.getLevel();
        boolean cataclysm = net.neoforged.fml.ModList.get().isLoaded("cataclysm");
        if (CompatItems.MATERIALS.containsKey("fiery"))
            h.assertTrue(new ItemStack(BuiltInRegistries.ITEM.get(SpartanCompat.id("fiery_dagger"))).getRarity() == net.minecraft.world.item.Rarity.UNCOMMON, "Fiery rarity");
        for (String mat : new String[]{"ignitium", "cursium", "witherite"}) {
            if (!CompatItems.MATERIALS.containsKey(mat)) continue;
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(SpartanCompat.id(mat + "_dagger")));
            h.assertTrue(stack.getRarity() == net.minecraft.world.item.Rarity.EPIC, mat + " is not Epic");
            var immune = net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.parse("cataclysm:explosion_immune_item"));
            h.assertTrue(stack.is(immune), mat + " weapons are not explosion immune");
        }
        for (String mat : new String[]{"ignitium", "cursium"}) {
            if (!cataclysm || !CompatItems.MATERIALS.containsKey(mat)) continue;
            var holder = level.getRecipeManager().byKey(SpartanCompat.id(mat + "_dagger")).orElseThrow();
            var template = BuiltInRegistries.ITEM.get(ResourceLocation.parse("cataclysm:" + mat + "_upgrade_smithing_template"));
            h.assertTrue(((SmithingTransformRecipe) holder.value()).isTemplateIngredient(new ItemStack(template)), mat + " template");
            h.assertFalse(((SmithingTransformRecipe) holder.value()).isTemplateIngredient(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)), mat + " takes a netherite template");
        }
        int cooked = 0;
        for (var holder : level.getRecipeManager().getRecipes()) {
            if (!holder.id().getNamespace().equals(SpartanCompat.MOD_ID)
                    || !(holder.value() instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe cooking)) continue;
            var input = new net.minecraft.world.item.crafting.SingleRecipeInput(cooking.getIngredients().get(0).getItems()[0].copy());
            h.assertTrue(cooking.matches(input, level) && !cooking.assemble(input, level.registryAccess()).isEmpty(), holder.id() + " does not smelt");
            cooked++;
        }
        if (cataclysm && CompatItems.MATERIALS.containsKey("black_steel")) h.assertTrue(cooked == 6, "Expected six smelting recipes, got " + cooked);

        var material = CompatItems.MATERIALS.values().iterator().next();
        var smite = CompatTraits.MECHA_SMITE.get().getMeleeCallback().orElseThrow();
        var living = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        var victim = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 2, 2));
        var restore = Map.<ResourceLocation, com.google.gson.JsonElement>of(ResourceLocation.fromNamespaceAndPath("spartan_compat_gametest", "override_test"),
                com.google.gson.JsonParser.parseString("{\"concentration_reset_ticks\": 101}"));
        try {
            TraitConfig.applyDatapacks(Map.of(ResourceLocation.fromNamespaceAndPath("t", "smite"), com.google.gson.JsonParser.parseString(
                    "{\"mecha_smite_regen_uses_fraction\": 0, \"mecha_smite_regen_health\": 3, \"mecha_smite_regen_chance\": 1,"
                    + " \"mecha_smite_chance\": 1, \"mecha_smite_wither_ticks\": 0, \"mecha_smite_fire_seconds\": 0, \"cataclysm_sounds_enabled\": 0}")));
            living.setHealth(4); // below half of 10, above the fixed 3
            smite.onHitEntity(material, ItemStack.EMPTY, victim, living, null);
            h.assertFalse(living.hasEffect(MobEffects.REGENERATION), "Fixed regen threshold ignored");
            h.assertFalse(victim.hasEffect(MobEffects.WITHER) || victim.getRemainingFireTicks() > 0, "Zero wither or fire length still applied");
            living.setHealth(2);
            smite.onHitEntity(material, ItemStack.EMPTY, victim, living, null);
            h.assertTrue(living.hasEffect(MobEffects.REGENERATION), "Fixed regen threshold did not heal");
        } finally {
            TraitConfig.applyDatapacks(restore);
        }
        h.succeed();
    }

    /** Resolve ingredients, match a filled grid and assemble every loaded weapon recipe. */
    @GameTest(template = "arena")
    public static void craftEveryLoadedRecipe(GameTestHelper h) {
        var level = h.getLevel();
        int checked = 0;
        var shadowed = new java.util.ArrayList<String>();
        for (var holder : level.getRecipeManager().getRecipes()) {
            if (!holder.id().getNamespace().equals(SpartanCompat.MOD_ID)) continue;
            var recipe = holder.value();
            var ingredients = recipe.getIngredients();
            var stacks = ingredients.stream().map(i -> i.isEmpty() ? ItemStack.EMPTY : i.getItems()[0].copy()).toList();
            ItemStack result;
            if (recipe instanceof ShapedRecipe shaped) {
                var input = CraftingInput.of(shaped.getWidth(), shaped.getHeight(), stacks);
                h.assertTrue(shaped.matches(input, level), holder.id() + " shaped match failed");
                var winner = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
                if (winner.isPresent() && !winner.get().id().equals(holder.id())) shadowed.add(holder.id() + " (taken by " + winner.get().id() + ")");
                result = shaped.assemble(input, level.registryAccess());
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                var input = CraftingInput.of(3, 3, java.util.stream.IntStream.range(0, 9)
                        .mapToObj(i -> i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY).toList());
                h.assertTrue(shapeless.matches(input, level), holder.id() + " shapeless match failed");
                result = shapeless.assemble(input, level.registryAccess());
            } else if (recipe instanceof SmithingTransformRecipe smithing) {
                ItemStack template = BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(smithing::isTemplateIngredient).findFirst().orElseThrow();
                ItemStack base = BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(smithing::isBaseIngredient).findFirst().orElseThrow();
                ItemStack addition = BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(smithing::isAdditionIngredient).findFirst().orElseThrow();
                base.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Tempered"));
                var input = new SmithingRecipeInput(template, base, addition);
                h.assertTrue(smithing.matches(input, level), holder.id() + " smithing match failed");
                result = smithing.assemble(input, level.registryAccess());
                h.assertTrue(result.getHoverName().getString().equals("Tempered"), holder.id() + " lost the base weapon name");
            } else continue;
            h.assertTrue(!result.isEmpty() && result.getItem() == recipe.getResultItem(level.registryAccess()).getItem(), holder.id() + " assemble failed");
            checked++;
        }
        SpartanCompat.LOG.info("[test] matched and assembled {} recipes", checked);
        h.assertTrue(shadowed.isEmpty(), shadowed.size() + " recipes are taken by another recipe with the same grid: " + shadowed);
        h.succeed();
    }
}
