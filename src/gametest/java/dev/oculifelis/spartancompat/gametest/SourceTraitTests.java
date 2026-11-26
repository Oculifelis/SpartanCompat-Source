package dev.oculifelis.spartancompat.gametest;

import dev.oculifelis.spartancompat.SpartanCompat;
import dev.oculifelis.spartancompat.compat.DarkSteelEnergy;
import dev.oculifelis.spartancompat.compat.SourceEvents;
import dev.oculifelis.spartancompat.content.CompatItems;
import dev.oculifelis.spartancompat.trait.CompatTraits;
import dev.oculifelis.spartancompat.trait.SourceTraits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.xiyu.spartanweaponryunofficial.api.SpartanWeaponryAPI.WeaponItemType;
import org.xiyu.spartanweaponryunofficial.api.WeaponMaterial;
import org.xiyu.spartanweaponryunofficial.api.trait.IMeleeTraitCallback;
import org.xiyu.spartanweaponryunofficial.api.trait.WeaponTrait;

/** One test per wave 3 trait (Botania, Ender IO, Psi, Eternal Starlight, Nature's Aura, L2 Complements). */
@GameTestHolder(SpartanCompatGameTestMod.ID)
@PrefixGameTestTemplate(false)
public final class SourceTraitTests {
    private SourceTraitTests() {}

    private static final BlockPos A = new BlockPos(2, 2, 2), B = new BlockPos(4, 2, 2), C = new BlockPos(4, 2, 3);

    static ItemStack weapon(GameTestHelper helper, String material) {
        Item item = BuiltInRegistries.ITEM.get(SpartanCompat.id(CompatItems.itemId(material, WeaponItemType.LONGSWORD)));
        helper.assertTrue(item != Items.AIR, material + " longsword not registered");
        return new ItemStack(item);
    }

    static WeaponMaterial mat(GameTestHelper helper, String material) {
        WeaponMaterial m = CompatItems.MATERIALS.get(material);
        helper.assertTrue(m != null, material + " not active");
        return m;
    }

    static IMeleeTraitCallback melee(DeferredHolder<WeaponTrait, WeaponTrait> t) {
        return t.get().getMeleeCallback().orElseThrow();
    }

    static void assertCarries(GameTestHelper helper, String material, DeferredHolder<WeaponTrait, WeaponTrait> trait) {
        helper.assertTrue(CompatTraits.has(weapon(helper, material), trait), material + " lacks " + trait.getId());
    }

    static boolean hasModifier(ItemStack stack, Holder<Attribute> attribute) {
        return stack.getAttributeModifiers().modifiers().stream().anyMatch(e -> e.attribute().equals(attribute));
    }

    static Zombie zombie(GameTestHelper helper, BlockPos pos) {
        return helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pos);
    }

    static Player player(GameTestHelper helper, ItemStack held) {
        Player p = helper.makeMockPlayer(GameType.SURVIVAL);
        var v = helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(new BlockPos(3, 2, 2)));
        p.moveTo(v.x, v.y, v.z, 0, 0);
        p.setItemSlot(EquipmentSlot.MAINHAND, held);
        return p;
    }

    // ---- Botania ----
    @GameTest(template = "arena")
    public static void manaRepair(GameTestHelper helper) {
        for (String m : new String[]{"manasteel", "elementium", "terrasteel"}) assertCarries(helper, m, SourceTraits.MANA_REPAIR);
        if (ModList.get().isLoaded("botania")) {
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("botania:mana_repair"));
            // Botania 455 and older have no such component; legacyManaRepair covers those.
            if (dev.oculifelis.spartancompat.compat.BotaniaCompat.HAS_REPAIR_COMPONENT) {
                helper.assertTrue(type != null, "botania:mana_repair component missing");
                for (String m : new String[]{"manasteel", "elementium", "terrasteel"})
                    helper.assertTrue(weapon(helper, m).has(type), m + " has no mana repair component");
            }
        }
        helper.succeed();
    }

    /** Botania without the MANA_REPAIR component: damaged weapons mend from a mana tablet each player tick. */
    @GameTest(template = "arena")
    public static void legacyManaRepair(GameTestHelper helper) {
        if (!ModList.get().isLoaded("botania") || dev.oculifelis.spartancompat.compat.BotaniaCompat.HAS_REPAIR_COMPONENT) {
            helper.succeed();
            return;
        }
        ItemStack mana = weapon(helper, "manasteel"), terra = weapon(helper, "terrasteel");
        mana.setDamageValue(mana.getMaxDamage() - 1);
        terra.setDamageValue(terra.getMaxDamage() - 1);
        ItemStack tablet = BotaniaChecks.fullTablet();
        Player p = player(helper, mana);
        p.getInventory().setItem(1, terra);
        p.getInventory().setItem(2, tablet);
        int dm0 = mana.getDamageValue(), dt0 = terra.getDamageValue(), m0 = BotaniaChecks.mana(tablet);
        for (int i = 0; i < 20; i++) BotaniaChecks.tick(p);
        int dm1 = mana.getDamageValue(), dt1 = terra.getDamageValue(), m1 = BotaniaChecks.mana(tablet);
        org.slf4j.LoggerFactory.getLogger("spartan_compat_gametest").info(
                "legacyManaRepair: manasteel damage {} -> {} (max {}), terrasteel damage {} -> {} (max {}), tablet mana {} -> {}",
                dm0, dm1, mana.getMaxDamage(), dt0, dt1, terra.getMaxDamage(), m0, m1);
        helper.assertTrue(dm1 == dm0 - 20, "manasteel mended " + (dm0 - dm1) + " not 20");
        helper.assertTrue(dt1 == dt0 - 20, "terrasteel mended " + (dt0 - dt1) + " not 20");
        helper.assertTrue(m0 - m1 == 20 * (120 + 200), "tablet lost " + (m0 - m1) + " mana, expected 6400");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void pixieAffinity(GameTestHelper helper) {
        assertCarries(helper, "elementium", SourceTraits.PIXIE_AFFINITY);
        helper.assertTrue(SourceTraits.PIXIE_AFFINITY.get().getMagnitude() == 0.05f, "pixie chance not 5%");
        BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse("botania:pixie_spawn_chance")).ifPresent(h ->
                helper.assertTrue(hasModifier(weapon(helper, "elementium"), h), "elementium lacks the pixie modifier"));
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void terraBeam(GameTestHelper helper) {
        // Terrasteel keeps mana repair only, no beam
        helper.assertFalse(CompatTraits.has(weapon(helper, "terrasteel"), SourceTraits.TERRA_BEAM), "terrasteel still has the beam");
        if (!ModList.get().isLoaded("botania")) { helper.succeed(); return; }
        Player p = player(helper, weapon(helper, "terrasteel"));
        helper.assertFalse(BotaniaChecks.beam(p, 1.0f), "beam fired with the beam off");
        helper.succeed();
    }

    // ---- Ender IO ----
    @GameTest(template = "arena")
    public static void beheading(GameTestHelper helper) {
        assertCarries(helper, "dark_steel", SourceTraits.BEHEADING);
        helper.assertTrue(SourceEvents.headOf(zombie(helper, A)).is(Items.ZOMBIE_HEAD), "zombie head wrong");
        helper.assertTrue(SourceEvents.headOf(helper.spawnWithNoFreeWill(EntityType.SKELETON, B)).is(Items.SKELETON_SKULL), "skeleton skull wrong");
        helper.assertTrue(SourceEvents.headOf(helper.spawnWithNoFreeWill(EntityType.COW, C)).isEmpty(), "cow should have no head");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void empowered(GameTestHelper helper) {
        ItemStack stack = weapon(helper, "dark_steel");
        helper.assertTrue(CompatTraits.has(stack, SourceTraits.EMPOWERED), "dark steel not empowered");
        helper.assertTrue(stack.getCapability(Capabilities.EnergyStorage.ITEM) != null, "no energy capability");
        var energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        helper.assertTrue(energy.receiveEnergy(1000, false) == 1000, "could not charge");
        var cb = SourceTraits.EMPOWERED.get().getGenericCallback().orElseThrow();
        Zombie z = zombie(helper, A);
        helper.assertTrue(cb.onDamageItem(stack, z, 2) == 0, "charged weapon still lost durability");
        helper.assertTrue(DarkSteelEnergy.get(stack) == 500, "wear drained " + (1000 - DarkSteelEnergy.get(stack)) + " FE, not 500");
        z.setItemSlot(EquipmentSlot.MAINHAND, stack);
        DamageSource src = helper.getLevel().damageSources().mobAttack(z);
        float hit = melee(SourceTraits.EMPOWERED).modifyDamageDealt(mat(helper, "dark_steel"), 5f, src, z, zombie(helper, B));
        helper.assertTrue(hit == 6f && DarkSteelEnergy.get(stack) == 0, "charged hit gave " + hit);
        helper.assertTrue(cb.onDamageItem(stack, z, 2) == 2, "empty weapon should take full wear");
        helper.succeed();
    }

    // ---- Psi ----
    @GameTest(template = "arena")
    public static void psiProgrammable(GameTestHelper helper) {
        ItemStack stack = weapon(helper, "psimetal");
        helper.assertTrue(CompatTraits.has(stack, SourceTraits.PSI_PROGRAMMABLE), "psimetal not programmable");
        var cb = SourceTraits.PSI_PROGRAMMABLE.get().getGenericCallback().orElseThrow();
        stack.setDamageValue(stack.getMaxDamage() - 3);
        helper.assertTrue(cb.onDamageItem(stack, zombie(helper, A), 10) == 2, "psimetal should stop one point short of breaking");
        if (ModList.get().isLoaded("psi")) {
            String problem = PsiChecks.sockets(weapon(helper, "psimetal"));
            helper.assertTrue(problem == null, problem);
        }
        helper.succeed();
    }

    // ---- Eternal Starlight ----
    @GameTest(template = "arena")
    public static void concentration(GameTestHelper helper) {
        assertCarries(helper, "flowglaze", SourceTraits.CONCENTRATION);
        Zombie attacker = zombie(helper, A), target = zombie(helper, B);
        DamageSource src = helper.getLevel().damageSources().mobAttack(attacker);
        WeaponMaterial m = mat(helper, "flowglaze");
        float last = 0;
        for (int i = 0; i < 5; i++) last = melee(SourceTraits.CONCENTRATION).modifyDamageDealt(m, 4f, src, attacker, target);
        helper.assertTrue(last == 5f, "fifth hit on one target gave " + last + ", not 5");
        float other = melee(SourceTraits.CONCENTRATION).modifyDamageDealt(m, 4f, src, attacker, zombie(helper, C));
        helper.assertTrue(other == 4f, "focus carried to a new target");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void frostbite(GameTestHelper helper) {
        assertCarries(helper, "glacite", SourceTraits.FROSTBITE);
        Zombie z = zombie(helper, A);
        melee(SourceTraits.FROSTBITE).onHitEntity(mat(helper, "glacite"), ItemStack.EMPTY, z, z, null);
        helper.assertTrue(z.getTicksFrozen() == 80, "frozen ticks " + z.getTicksFrozen());
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void malaritePoison(GameTestHelper helper) {
        assertCarries(helper, "malarite", SourceTraits.MALARITE_POISON);
        Zombie z = zombie(helper, A);
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, B);
        melee(SourceTraits.MALARITE_POISON).onHitEntity(mat(helper, "malarite"), ItemStack.EMPTY, cow, z, null);
        var e = cow.getEffect(MobEffects.POISON);
        helper.assertTrue(e != null && e.getDuration() <= 60, "no poison: " + e);
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void starfire(GameTestHelper helper) {
        assertCarries(helper, "starfire", SourceTraits.STARFIRE);
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, A);
        melee(SourceTraits.STARFIRE).onHitEntity(mat(helper, "starfire"), ItemStack.EMPTY, cow, cow, null);
        var effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("eternal_starlight:starfire"));
        if (effect.isPresent()) helper.assertTrue(cow.hasEffect(effect.get()), "no starfire effect");
        else helper.assertTrue(cow.getRemainingFireTicks() > 0, "not burning without Eternal Starlight");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void scorching(GameTestHelper helper) {
        assertCarries(helper, "thermal_springstone", SourceTraits.SCORCHING);
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, A);
        melee(SourceTraits.SCORCHING).onHitEntity(mat(helper, "thermal_springstone"), ItemStack.EMPTY, cow, cow, null);
        helper.assertTrue(cow.getRemainingFireTicks() >= 200, "fire ticks " + cow.getRemainingFireTicks());
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void unrealiumHeft(GameTestHelper helper) {
        assertCarries(helper, "unrealium", SourceTraits.UNREALIUM_HEFT);
        helper.assertTrue(hasModifier(weapon(helper, "unrealium"), Attributes.ATTACK_KNOCKBACK), "no knockback modifier");
        helper.succeed();
    }

    // ---- Nature's Aura ----
    @GameTest(template = "arena")
    public static void naSlowing(GameTestHelper helper) {
        assertCarries(helper, "infused_iron", SourceTraits.NA_SLOWING);
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, A);
        melee(SourceTraits.NA_SLOWING).onHitEntity(mat(helper, "infused_iron"), ItemStack.EMPTY, cow, cow, null);
        var e = cow.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(e != null && e.getAmplifier() == 2 && e.getDuration() <= 60, "slowness wrong: " + e);
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void naLevitating(GameTestHelper helper) {
        assertCarries(helper, "sky", SourceTraits.NA_LEVITATING);
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, A);
        melee(SourceTraits.NA_LEVITATING).onHitEntity(mat(helper, "sky"), ItemStack.EMPTY, cow, cow, null);
        var e = cow.getEffect(MobEffects.LEVITATION);
        helper.assertTrue(e != null && e.getAmplifier() == 2, "levitation wrong: " + e);
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void depthSweep(GameTestHelper helper) {
        assertCarries(helper, "depth", SourceTraits.DEPTH_SWEEP);
        Player p = player(helper, weapon(helper, "depth"));
        Zombie target = zombie(helper, B), near = zombie(helper, C);
        float before = near.getHealth(), targetBefore = target.getHealth();
        melee(SourceTraits.DEPTH_SWEEP).onHitEntity(mat(helper, "depth"), p.getMainHandItem(), target, p, null);
        helper.assertTrue(near.getHealth() < before, "neighbour not swept");
        helper.assertTrue(target.getHealth() == targetBefore, "sweep hit the main target twice");
        helper.succeed();
    }

    // ---- L2 Complements ----
    @GameTest(template = "arena")
    public static void totemic(GameTestHelper helper) {
        assertCarries(helper, "totemic_gold", SourceTraits.TOTEMIC);
        Zombie z = zombie(helper, A);
        z.setHealth(10);
        SourceTraits.TOTEMIC.get().getGenericCallback().orElseThrow().onDamageItem(weapon(helper, "totemic_gold"), z, 3);
        helper.assertTrue(z.getHealth() == 13, "wear did not heal the wielder: " + z.getHealth());
        DamageSource src = helper.getLevel().damageSources().generic();
        float vsUndead = melee(SourceTraits.TOTEMIC).modifyDamageDealt(mat(helper, "totemic_gold"), 4f, src, z, zombie(helper, B));
        float vsCow = melee(SourceTraits.TOTEMIC).modifyDamageDealt(mat(helper, "totemic_gold"), 4f, src, z, helper.spawnWithNoFreeWill(EntityType.COW, C));
        helper.assertTrue(vsUndead == 8f && vsCow == 4f, "undead bonus wrong: " + vsUndead + " / " + vsCow);
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void tidal(GameTestHelper helper) {
        assertCarries(helper, "poseidite", SourceTraits.TIDAL);
        Zombie z = zombie(helper, A);
        DamageSource src = helper.getLevel().damageSources().generic();
        WeaponMaterial m = mat(helper, "poseidite");
        helper.assertTrue(melee(SourceTraits.TIDAL).modifyDamageDealt(m, 4f, src, z, zombie(helper, B)) == 4f, "bonus on dry land");
        var enderman = helper.spawnWithNoFreeWill(EntityType.ENDERMAN, C);
        helper.assertTrue(melee(SourceTraits.TIDAL).modifyDamageDealt(m, 4f, src, z, enderman) == 8f, "no bonus against a water-sensitive mob");
        helper.setBlock(A, net.minecraft.world.level.block.Blocks.WATER);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(melee(SourceTraits.TIDAL).modifyDamageDealt(m, 4f, src, z, zombie(helper, B.above(2))) == 6f, "no bonus in water");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void shulkerReach(GameTestHelper helper) {
        ItemStack stack = weapon(helper, "shulkerate");
        helper.assertTrue(CompatTraits.has(stack, SourceTraits.SHULKER_REACH), "shulkerate lacks reach");
        helper.assertTrue(SourceTraits.SHULKER_REACH.get().getGenericCallback().orElseThrow().onDamageItem(stack, zombie(helper, A), 5) == 1, "wear not capped at 1");
        helper.assertTrue(hasModifier(stack, Attributes.ENTITY_INTERACTION_RANGE) && hasModifier(stack, Attributes.BLOCK_INTERACTION_RANGE), "no reach modifiers");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void eternium(GameTestHelper helper) {
        ItemStack stack = weapon(helper, "eternium");
        helper.assertTrue(CompatTraits.has(stack, SourceTraits.ETERNIUM), "eternium lacks its trait");
        helper.assertTrue(SourceTraits.ETERNIUM.get().getGenericCallback().orElseThrow().onDamageItem(stack, zombie(helper, A), 5) == 0, "eternium took wear");
        helper.assertTrue(stack.has(net.minecraft.core.component.DataComponents.UNBREAKABLE), "eternium not unbreakable");
        helper.succeed();
    }
}
