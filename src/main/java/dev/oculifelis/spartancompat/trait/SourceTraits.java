package dev.oculifelis.spartancompat.trait;

import dev.oculifelis.spartancompat.config.TraitConfig;
import dev.oculifelis.spartancompat.compat.DarkSteelEnergy;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.xiyu.spartanweaponryunofficial.api.WeaponMaterial;
import org.xiyu.spartanweaponryunofficial.api.trait.WeaponTrait;
import org.xiyu.spartanweaponryunofficial.api.trait.WeaponTrait.TraitQuality;

/**
 * Traits that copy what the source mods' own swords do (Botania, Ender IO, Psi, Eternal Starlight, Nature's Aura,
 * L2 Complements). Behaviour that needs the source mod's classes lives in content.BotaniaCompat and content.PsiCompat,
 * which only load when that mod is present.
 */
public final class SourceTraits {
    private static final net.neoforged.neoforge.registries.DeferredRegister<WeaponTrait> R = CompatTraits.TRAITS;

    private SourceTraits() {}

    /** Called from the mod constructor so the holders below are registered. */
    public static void init() {}

    private static DeferredHolder<WeaponTrait, WeaponTrait> marker(String id, float magnitude, boolean melee) {
        return R.register(id, () -> {
            WeaponTrait t = new CompatTrait(id, TraitQuality.POSITIVE).setMagnitude(magnitude);
            return melee ? t.setMelee() : t.setUniversal();
        });
    }

    // ---- Botania ----
    /** Mends from the wielder's mana like Botania's tools. Mending is set up in BotaniaCompat. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> MANA_REPAIR = marker("mana_repair", 0, false);
    /** Like the Elementium Sword: +5% chance to summon a pixie when hurt. Attribute added in CompatAttributes. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> PIXIE_AFFINITY = marker("pixie_affinity", 0.05f, true);
    /** Like the Terra Blade: a full-strength swing fires a mana beam. Handled in BotaniaCompat. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> TERRA_BEAM = marker("terra_beam", 7, true);

    // ---- Ender IO ----
    /** Dark steel's Beheading: kills have a chance to drop the victim's head. Handled in CompatAttributes.onDrops. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> BEHEADING = marker("beheading", 0.07f, true);
    /** Stores Forge Energy; stored energy takes durability loss and charged hits strike harder. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> EMPOWERED = R.register("empowered",
            () -> new CompatTrait("empowered", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    if (!flag("dark_steel_energy_enabled") || attacker == null) return damage;
                    ItemStack held = attacker.getMainHandItem();
                    int cost = TraitConfig.integer("empowered_hit_fe");
                    return DarkSteelEnergy.drain(held, cost) ? damage + TraitConfig.number("empowered_hit_damage") : damage;
                }

                @Override
                public <T extends LivingEntity> int onDamageItem(ItemStack stack, T entity, int amount) {
                    if (!flag("dark_steel_energy_enabled") || amount <= 0) return amount;
                    int per = TraitConfig.integer("empowered_fe_per_durability");
                    int absorbed = Math.min(amount, DarkSteelEnergy.get(stack) / Math.max(1, per));
                    if (absorbed > 0) DarkSteelEnergy.drain(stack, absorbed * per);
                    return amount - absorbed;
                }
            }.setMagnitude(100000).setUniversal());

    // ---- Psi ----
    /** Socketable like Psimetal tools; casts the selected spell bullet on hit and regenerates while not held. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> PSI_PROGRAMMABLE = R.register("psi_programmable",
            () -> new CompatTrait("psi_programmable", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    if (projectile == null && flag("psi_cast_enabled") && attacker instanceof Player p && ModList.get().isLoaded("psi"))
                        dev.oculifelis.spartancompat.compat.PsiCompat.castOnHit(stack, target, p);
                }

                @Override
                public void onItemUpdate(WeaponMaterial material, ItemStack stack, Level level, LivingEntity holder, int slot, boolean selected) {
                    if (!level.isClientSide() && ModList.get().isLoaded("psi"))
                        dev.oculifelis.spartancompat.compat.PsiCompat.regen(stack, holder);
                }

                /** Psimetal never breaks: it stops one point short and stops casting until mended. */
                @Override
                public <T extends LivingEntity> int onDamageItem(ItemStack stack, T entity, int amount) {
                    int room = stack.getMaxDamage() - 1 - stack.getDamageValue();
                    return Math.max(0, Math.min(amount, room));
                }
            }.setUniversal());

    // ---- Eternal Starlight ----
    private static final String CONC_TARGET = "spartan_compat:conc_target", CONC_LEVEL = "spartan_compat:conc_level",
            CONC_TIME = "spartan_compat:conc_time";

    /** Flowglaze: repeated hits on the same target build focus; at full focus hits deal +25%. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> CONCENTRATION = R.register("concentration",
            () -> new CompatTrait("concentration", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    if (attacker == null) return damage;
                    var data = attacker.getPersistentData();
                    long now = attacker.level().getGameTime();
                    boolean same = data.getInt(CONC_TARGET) == victim.getId()
                            && now - data.getLong(CONC_TIME) <= TraitConfig.integer("concentration_reset_ticks");
                    int level = same ? Math.min(data.getInt(CONC_LEVEL) + 1, 4) : 0;
                    data.putInt(CONC_TARGET, victim.getId());
                    data.putInt(CONC_LEVEL, level);
                    data.putLong(CONC_TIME, now);
                    return level >= 4 ? damage * (1 + magnitude) : damage;
                }
            }.setMagnitude(0.25f).setMelee());

    /** Glacite: freezes the target a little more with each hit. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> FROSTBITE = R.register("frostbite",
            () -> new CompatTrait("frostbite", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    if (target.canFreeze()) target.setTicksFrozen(Math.min(target.getTicksFrozen() + (int) magnitude, 300));
                }
            }.setMagnitude(80).setMelee());

    /** Malarite: poisons the target for 3 seconds. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> MALARITE_POISON = R.register("malarite_poison",
            () -> new CompatTrait("malarite_poison", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, (int) magnitude, 0), attacker);
                }
            }.setMagnitude(60).setMelee());

    /** Starfire: applies Eternal Starlight's Starfire effect for 3 seconds. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> STARFIRE = R.register("starfire",
            () -> new CompatTrait("starfire", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    Holder<MobEffect> fire = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("eternal_starlight:starfire"))
                            .<Holder<MobEffect>>map(h -> h).orElse(null);
                    if (fire != null) target.addEffect(new MobEffectInstance(fire, (int) magnitude, 0), attacker);
                    else target.igniteForTicks((int) magnitude);
                    SoundEvent whoosh = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("eternal_starlight:starfire_whoosh"));
                    if (whoosh != null) target.level().playSound(null, target.getX(), target.getY(), target.getZ(), whoosh, SoundSource.PLAYERS, 1, 1);
                }
            }.setMagnitude(60).setMelee());

    /** Thermal Springstone: adds 10 seconds of burning. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> SCORCHING = R.register("scorching",
            () -> new CompatTrait("scorching", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    target.setRemainingFireTicks(Math.max(0, target.getRemainingFireTicks()) + (int) magnitude);
                }
            }.setMagnitude(200).setMelee());

    /** Unrealium: a little less knockback (attribute added in CompatAttributes). */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> UNREALIUM_HEFT = marker("unrealium_heft", 0.05f, true);

    // ---- Nature's Aura ----
    /** Botanist's Blade: Slowness III for 3 seconds. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> NA_SLOWING = R.register("na_slowing",
            () -> new CompatTrait("na_slowing", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) magnitude, 2), attacker);
                }
            }.setMagnitude(60).setMelee());

    /** Skyseeker's Blade: Levitation III for 3 seconds. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> NA_LEVITATING = R.register("na_levitating",
            () -> new CompatTrait("na_levitating", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, (int) magnitude, 2), attacker);
                }
            }.setMagnitude(60).setMelee());

    /** Soulstrider's Blade: every hit sweeps the foes around the target for 75% of attack damage. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> DEPTH_SWEEP = R.register("depth_sweep",
            () -> new CompatTrait("depth_sweep", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    if (projectile != null || !(attacker instanceof Player player) || player.level().isClientSide()) return;
                    if (player.getPersistentData().getBoolean("spartan_compat:sweeping")) return;
                    player.getPersistentData().putBoolean("spartan_compat:sweeping", true);
                    try {
                        float dmg = (float) (magnitude * player.getAttributeValue(Attributes.ATTACK_DAMAGE));
                        double range = player.entityInteractionRange();
                        for (LivingEntity other : player.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2, 1, 2))) {
                            if (other == player || other == target || player.isAlliedTo(other)) continue;
                            if (other instanceof ArmorStand stand && stand.isMarker()) continue;
                            if (player.distanceToSqr(other) > range * range) continue;
                            other.knockback(0.4, Math.sin(Math.toRadians(player.getYRot())), -Math.cos(Math.toRadians(player.getYRot())));
                            other.hurt(player.damageSources().playerAttack(player), dmg);
                        }
                        player.sweepAttack();
                    } finally {
                        player.getPersistentData().remove("spartan_compat:sweeping");
                    }
                }
            }.setMagnitude(0.75f).setMelee());

    // ---- L2 Complements ----
    /** Totemic gold: durability lost heals the wielder; double damage against undead. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> TOTEMIC = R.register("totemic",
            () -> new CompatTrait("totemic", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    return victim.getType().is(EntityTypeTags.SENSITIVE_TO_SMITE) ? damage * (1 + magnitude) : damage;
                }

                @Override
                public <T extends LivingEntity> int onDamageItem(ItemStack stack, T entity, int amount) {
                    if (entity != null && amount > 0) entity.heal(amount);
                    return amount;
                }
            }.setMagnitude(1.0f).setMelee());

    /** Poseidite: +50% damage and +20% attack speed in rain or water; double damage against water mobs. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> TIDAL = R.register("tidal",
            () -> new CompatTrait("tidal", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    if (attacker != null && attacker.isInWaterRainOrBubble()) damage *= 1 + magnitude;
                    if (isWaterMob(victim)) damage *= 2;
                    return damage;
                }
            }.setMagnitude(0.5f).setMelee());

    /** Shulkerate: loses at most one durability per use, +1 block and entity reach (attributes in CompatAttributes). */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> SHULKER_REACH = R.register("shulker_reach",
            () -> new CompatTrait("shulker_reach", TraitQuality.POSITIVE) {
                @Override
                public <T extends LivingEntity> int onDamageItem(ItemStack stack, T entity, int amount) {
                    return Math.min(amount, 1);
                }
            }.setMagnitude(1).setMelee());

    /** Eternium: never breaks. The Unbreakable component comes from the material's "unbreakable" flag. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> ETERNIUM = R.register("eternium",
            () -> new CompatTrait("eternium", TraitQuality.POSITIVE) {
                @Override
                public <T extends LivingEntity> int onDamageItem(ItemStack stack, T entity, int amount) {
                    return 0;
                }
            }.setUniversal());

    // ---- Wave 4: Nexus, Embers Re-Ignited, Create Deep Dark, Legendary Monsters ----
    private static final String OC_STACKS = "spartan_compat:overclock_stacks", OC_TIME = "spartan_compat:overclock_time",
            HEAT = "spartan_compat:heat", HEAT_TIME = "spartan_compat:heat_time";

    /** Overclock stacks still live for this entity: 0 once the reset window has passed. */
    public static int overclockStacks(LivingEntity e) {
        var data = e.getPersistentData();
        if (e.level().getGameTime() - data.getLong(OC_TIME) > TraitConfig.integer("overclock_reset_ticks")) return 0;
        return Math.min(data.getInt(OC_STACKS), TraitConfig.integer("overclock_max_stacks"));
    }

    /** Accelerite: each hit adds a stack of attack speed (set in SourceEvents); hits at full stacks add knockback. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> OVERCLOCK = R.register("overclock",
            () -> new CompatTrait("overclock", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    if (attacker == null || attacker.level().isClientSide()) return;
                    int max = TraitConfig.integer("overclock_max_stacks");
                    int before = overclockStacks(attacker);
                    if (before >= max && max > 0) {
                        double kb = TraitConfig.number("overclock_knockback");
                        target.knockback(kb, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
                    }
                    var data = attacker.getPersistentData();
                    data.putInt(OC_STACKS, Math.min(before + 1, max));
                    data.putLong(OC_TIME, attacker.level().getGameTime());
                }
            }.setMelee());

    /** Dawnstone: hits build heat; the hit that fills it deals bonus fire damage and sets the target alight. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> HEAT_SINK = R.register("heat_sink",
            () -> new CompatTrait("heat_sink", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    if (attacker == null || attacker.level().isClientSide()) return damage;
                    var data = attacker.getPersistentData();
                    long now = attacker.level().getGameTime();
                    int heat = now - data.getLong(HEAT_TIME) > TraitConfig.integer("heat_sink_reset_ticks") ? 0 : data.getInt(HEAT);
                    heat++;
                    data.putLong(HEAT_TIME, now);
                    if (heat < TraitConfig.integer("heat_sink_max_heat")) {
                        data.putInt(HEAT, heat);
                        return damage;
                    }
                    data.putInt(HEAT, 0);
                    if (victim.fireImmune()) return damage;
                    victim.igniteForSeconds((float) TraitConfig.number("heat_sink_fire_seconds"));
                    if (attacker.level() instanceof net.minecraft.server.level.ServerLevel sl)
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, victim.getX(), victim.getY(0.5), victim.getZ(),
                                12, 0.3, 0.4, 0.3, 0.02);
                    return damage + (float) TraitConfig.number("heat_sink_fire_damage");
                }
            }.setMelee());

    private static boolean echoing;

    /** Echo: a hit from behind the target is followed by an echo hit that ignores armour. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> ECHO_STRIKE = R.register("echo_strike",
            () -> new CompatTrait("echo_strike", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    if (echoing || attacker == null || !(attacker.level() instanceof net.minecraft.server.level.ServerLevel sl)) return damage;
                    if (!fromBehind(attacker, victim)) return damage;
                    float echo = damage * (float) TraitConfig.number("echo_strike_fraction");
                    if (echo <= 0) return damage;
                    sl.getServer().execute(() -> {
                        if (!victim.isAlive()) return;
                        echoing = true;
                        try {
                            victim.invulnerableTime = 0;
                            victim.hurt(attacker.damageSources().sonicBoom(attacker), echo);
                        } finally {
                            echoing = false;
                        }
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, victim.getX(), victim.getY(0.6), victim.getZ(),
                                6, 0.3, 0.3, 0.3, 0.02);
                    });
                    return damage;
                }
            }.setMelee());

    /** True when the attacker stands in the half-space behind where the victim faces. */
    public static boolean fromBehind(Entity attacker, LivingEntity victim) {
        double dx = attacker.getX() - victim.getX(), dz = attacker.getZ() - victim.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) return false;
        float yaw = victim.getYHeadRot() * ((float) Math.PI / 180f);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        return (dx * fx + dz * fz) / len < 0;
    }

    /** Mobs counted as End mobs by Void Edge. Packs add their own to this entity type tag. */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> END_MOBS = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("spartan_compat", "end_mobs"));

    /** Enderitium: bonus damage against End mobs. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> VOID_EDGE = R.register("void_edge",
            () -> new CompatTrait("void_edge", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    return victim.getType().is(END_MOBS) ? damage * (1 + (float) TraitConfig.number("void_edge_bonus")) : damage;
                }
            }.setMelee());

    public static boolean isWaterMob(LivingEntity e) {
        return e.isSensitiveToWater() || e.getType().is(EntityTypeTags.SENSITIVE_TO_IMPALING)
                || e.getType().is(EntityTypeTags.AQUATIC);
    }

    public static boolean flag(String key) {
        return TraitConfig.number(key) >= 0.5f;
    }
}
