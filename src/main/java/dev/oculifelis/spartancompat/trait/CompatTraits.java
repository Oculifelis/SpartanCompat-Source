package dev.oculifelis.spartancompat.trait;

import dev.oculifelis.spartancompat.SpartanCompat;
import dev.oculifelis.spartancompat.effect.CompatEffects;
import dev.oculifelis.spartancompat.config.TraitConfig;
import dev.oculifelis.spartancompat.content.TwilightEnchantments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.Mth;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.xiyu.spartanweaponryunofficial.api.SpartanWeaponryAPI;
import org.xiyu.spartanweaponryunofficial.api.WeaponMaterial;
import org.xiyu.spartanweaponryunofficial.api.WeaponTraits;
import org.xiyu.spartanweaponryunofficial.api.trait.WeaponTrait;
import org.xiyu.spartanweaponryunofficial.api.trait.WeaponTrait.TraitQuality;

/**
 * The traits this mod adds. Materials pick them up through the weapon_traits tags in data, so a datapack can move
 * them between materials. Balance settings live in config/spartan_compat/traits.json.
 */
public final class CompatTraits {
    public static final DeferredRegister<WeaponTrait> TRAITS = DeferredRegister.create(WeaponTraits.REGISTRY_KEY, SpartanCompat.MOD_ID);

    /** Tag on projectiles fired or thrown by a weapon with Neptune's Strike. */
    public static final String NEPTUNE_TAG = SpartanCompat.MOD_ID + ":neptunes_strike";
    /** Material captured at firing time, so changing the held weapon cannot change an arrow's traits. */
    public static final String RANGED_MATERIAL = SpartanCompat.MOD_ID + ":ranged_material";

    /** Kills have a chance to drop each item twice. Handled in CompatEvents. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> BOUNTIFUL = TRAITS.register("bountiful",
            () -> new CompatTrait("bountiful", TraitQuality.POSITIVE).setMagnitude(0.25f).setUniversal());

    /** Extra damage against any target wearing armour. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> ARMORED_DAMAGE_BONUS = TRAITS.register("armored_damage_bonus",
            () -> new CompatTrait("armored_damage_bonus", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    if (!(victim.level() instanceof ServerLevel world) || victim.getArmorValue() <= 0) return damage;
                    world.getChunkSource().broadcastAndSend(victim, new net.minecraft.network.protocol.game.ClientboundAnimatePacket(victim, 5));
                    float cover = victim.getArmorCoverPercentage();
                    float bonus = TraitConfig.number("knightmetal_bonus");
                    return damage + (cover > 0 ? (int) (bonus * cover) : bonus);
                }
            }.setMagnitude(TraitConfig.number("knightmetal_bonus")).setUniversal());

    /** Sets foes on fire, or emits flame particles around fire-immune targets. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> IGNITE = TRAITS.register("ignite",
            () -> new CompatTrait("ignite", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    if (!(target.level() instanceof ServerLevel world)) return;
                    if (!target.fireImmune()) target.igniteForSeconds(TraitConfig.number("fiery_seconds"));
                    else world.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() / 2,
                            target.getZ(), TraitConfig.integer("fiery_immune_particles"), target.getBbWidth(),
                            target.getBbHeight() / 2, target.getBbWidth(), 0.02);
                }
            }.setMagnitude(TraitConfig.number("fiery_seconds")).setUniversal());

    /** Original Neptune's Might checks the eyes, including on thrown hits. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> NEPTUNES_MIGHT = TRAITS.register("neptunes_might",
            () -> new CompatTrait("neptunes_might", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    return victim.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                            ? damage * (1.0f + TraitConfig.number("neptunes_might_bonus")) : damage;
                }
            }.setMagnitude(TraitConfig.number("neptunes_might_bonus")).setMelee().setThrowing());

    /** Arrows and thrown weapons keep their speed underwater. The drag fix runs in CompatEvents. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> NEPTUNES_STRIKE = TRAITS.register("neptunes_strike",
            () -> new CompatTrait("neptunes_strike", TraitQuality.POSITIVE) {
                @Override
                public void onProjectileSpawn(WeaponMaterial material, AbstractArrow projectile) {
                    projectile.getPersistentData().putBoolean(NEPTUNE_TAG, true);
                }

                @Override
                public void onThrowingProjectileSpawn(WeaponMaterial material, AbstractArrow projectile) {
                    projectile.getPersistentData().putBoolean(NEPTUNE_TAG, true);
                }
            }.setRanged().setThrowing());

    /** Original Cataclysm stacking effects, adapted to the modern universal callbacks. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> BLAZING_BRAND = TRAITS.register("blazing_brand",
            () -> new CompatTrait("blazing_brand", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    if (attacker != null && roll(attacker, "blazing_brand_chance")) {
                        int max = TraitConfig.integer("blazing_brand_max_amplifier");
                        int amp = stack(victim, CompatEffects.BLAZING_BRAND, TraitConfig.integer("blazing_brand_ticks"), max);
                        float heal = TraitConfig.number("blazing_brand_heal_per_level");
                        if (attacker instanceof Player) heal /= Mth.clamp((float) attacker.getAttributeValue(Attributes.ATTACK_SPEED), 0.5f, 2f);
                        if (roll(attacker, "blazing_brand_heal_chance")) {
                            attacker.heal(heal * (amp + 1));
                            particles(attacker, ParticleTypes.SMALL_FLAME, attacker.getX(), attacker.getY() + 1, attacker.getZ(), 6, 0.3, 0.6, 0.3, 0.02);
                        }
                        particles(attacker, amp >= max ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                                victim.getX(), victim.getY() + victim.getEyeHeight() - 1.0, victim.getZ(), 10, 0.4, 0.7, 0.4, 0.02);
                        sound(attacker, victim, "ignis_hurt", 0.4f, 1.25f * pitch(attacker));
                    }
                    return damage;
                }
            }.setUniversal());

    public static final DeferredHolder<WeaponTrait, WeaponTrait> ACCURSED_RAGE = TRAITS.register("accursed_rage",
            () -> new CompatTrait("accursed_rage", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    if (attacker == null) return damage;
                    int max = TraitConfig.integer("accursed_rage_max_amplifier");
                    var old = attacker.getEffect(CompatEffects.ACCURSED_RAGE);
                    int next = old == null ? 0 : Math.min(max, old.getAmplifier() + 1);
                    boolean reachedMax = old != null && next >= max && old.getAmplifier() == next - 1;
                    if (next >= max) particles(attacker, cataclysmParticle("small_cursed_flame"),
                            victim.getX(), victim.getY() + victim.getEyeHeight() - 1.0, victim.getZ(), 8, 0.4, 0.7, 0.4, 0.02);
                    if (roll(attacker, "accursed_rage_chance")) {
                        stack(attacker, CompatEffects.ACCURSED_RAGE, TraitConfig.integer("accursed_rage_ticks"), max);
                        if (reachedMax) {
                            particles(attacker, cataclysmParticle("cursed_flame"), attacker.getX(), attacker.getY() + 1, attacker.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
                            sound(attacker, attacker, "maledictus_short_roar", 0.5f, 1f);
                        } else if (next < max) {
                            particles(attacker, cataclysmParticle("small_cursed_flame"), attacker.getX(), attacker.getY() + 1, attacker.getZ(), 9, 0.3, 0.6, 0.3, 0.01);
                        }
                    }
                    var rage = attacker.getEffect(CompatEffects.ACCURSED_RAGE);
                    if (rage == null) return damage;
                    sound(attacker, victim, "hammertime", 0.4f, pitch(attacker));
                    return damage + (rage.getAmplifier() + 1) * TraitConfig.number("accursed_rage_damage_per_level");
                }
            }.setUniversal());

    public static final DeferredHolder<WeaponTrait, WeaponTrait> MECHA_SMITE = TRAITS.register("mecha_smite",
            () -> new CompatTrait("mecha_smite", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    if (attacker == null) return;
                    if (roll(attacker, "mecha_smite_chance")) {
                        int wither = TraitConfig.integer("mecha_smite_wither_ticks");
                        if (wither > 0) target.addEffect(new MobEffectInstance(MobEffects.WITHER, wither,
                                TraitConfig.integer("mecha_smite_wither_amplifier")), attacker);
                        float fire = TraitConfig.number("mecha_smite_fire_seconds");
                        if (fire > 0) target.igniteForSeconds(fire);
                        sound(attacker, target, "emp_activated", 0.4f, pitch(attacker));
                    }
                    float threshold = TraitConfig.number("mecha_smite_regen_uses_fraction") > 0
                            ? attacker.getMaxHealth() * TraitConfig.number("mecha_smite_regen_health_fraction")
                            : TraitConfig.number("mecha_smite_regen_health");
                    if (attacker.getHealth() < threshold && roll(attacker, "mecha_smite_regen_chance")) {
                        attacker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, TraitConfig.integer("mecha_smite_regen_ticks"),
                                TraitConfig.integer("mecha_smite_regen_amplifier")));
                    }
                }
            }.setUniversal());

    public static final DeferredHolder<WeaponTrait, WeaponTrait> MECHA_PULSE = TRAITS.register("mecha_pulse",
            () -> new CompatTrait("mecha_pulse", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    if (attacker == null || attacker.hasEffect(CompatEffects.PULSE_COOLDOWN)
                            || !roll(attacker, "mecha_pulse_charge_chance")) return damage;
                    var old = attacker.getEffect(CompatEffects.PULSE_CHARGE);
                    int threshold = TraitConfig.integer("mecha_pulse_threshold_amplifier");
                    int amp = old == null ? 0 : Math.min(threshold, old.getAmplifier() + 1);
                    particles(attacker, ParticleTypes.ANGRY_VILLAGER, attacker.getX(), attacker.getY() + 1.4, attacker.getZ(), 3, 0.4, 0.4, 0.4, 0);
                    if (amp < threshold) {
                        attacker.addEffect(new MobEffectInstance(CompatEffects.PULSE_CHARGE,
                                TraitConfig.integer("mecha_pulse_charge_ticks"), amp));
                        return damage;
                    }
                    attacker.removeEffect(CompatEffects.PULSE_CHARGE);
                    attacker.addEffect(new MobEffectInstance(CompatEffects.PULSE_COOLDOWN,
                            TraitConfig.integer("mecha_pulse_cooldown_ticks"), 0, false, false, true));
                    // Use Cataclysm's real stun when available. Bare forced modules have a functional fallback.
                    Holder<MobEffect> stun = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                            .getHolder(net.minecraft.resources.ResourceLocation.parse("cataclysm:stun"))
                            .<Holder<MobEffect>>map(h -> h).orElse(CompatEffects.STUN);
                    victim.addEffect(new MobEffectInstance(stun, TraitConfig.integer("mecha_pulse_stun_ticks")), attacker);
                    particles(attacker, cataclysmParticle("em_pulse"), victim.getX(), victim.getY() + victim.getEyeHeight() - 1.25, victim.getZ(), 1, 0, 0, 0, 0);
                    sound(attacker, attacker, "harbinger_charge", 0.42f, 1f);
                    sound(attacker, victim, "emp_activated", 0.8f, 1f);
                    return damage + TraitConfig.number("mecha_pulse_damage");
                }
            }.setUniversal());

    public static final DeferredHolder<WeaponTrait, WeaponTrait> NUMBING = TRAITS.register("numbing",
            () -> new CompatTrait("numbing", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    if (roll(attacker == null ? target : attacker, "numbing_chance"))
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                                TraitConfig.integer("numbing_ticks"), TraitConfig.integer("numbing_amplifier")), attacker);
                }
            }.setUniversal());

    public static final DeferredHolder<WeaponTrait, WeaponTrait> IRONWOOD_ENCHANTMENTS = enchantmentTrait("ironwood_enchantments");
    public static final DeferredHolder<WeaponTrait, WeaponTrait> STEELEAF_ENCHANTMENTS = enchantmentTrait("steeleaf_enchantments");

    private static DeferredHolder<WeaponTrait, WeaponTrait> enchantmentTrait(String name) {
        return TRAITS.register(name, () -> new CompatTrait(name, TraitQuality.POSITIVE) {
            @Override
            public void onItemUpdate(WeaponMaterial material, ItemStack stack, net.minecraft.world.level.Level level,
                                     LivingEntity holder, int slot, boolean selected) {
                if (!level.isClientSide()) TwilightEnchantments.initialize(stack, level.registryAccess());
            }
        }.setUniversal());
    }

    private static boolean roll(LivingEntity entity, String key) {
        return entity.getRandom().nextFloat() < TraitConfig.number(key);
    }

    /** Server-side particles; a null type (the source mod is missing) is skipped. */
    private static void particles(LivingEntity source, net.minecraft.core.particles.ParticleOptions type, double x, double y, double z,
                                  int count, double dx, double dy, double dz, double speed) {
        if (type != null && source.level() instanceof ServerLevel level) level.sendParticles(type, x, y, z, count, dx, dy, dz, speed);
    }

    /** One of Cataclysm's simple particles by id, or null when Cataclysm is absent. */
    private static net.minecraft.core.particles.ParticleOptions cataclysmParticle(String path) {
        var type = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cataclysm", path));
        return type instanceof net.minecraft.core.particles.SimpleParticleType simple ? simple : null;
    }

    /** Plays one of Cataclysm's sound events at an entity, unless the sounds are switched off in the trait data. */
    private static void sound(LivingEntity source, Entity at, String path, float volume, float pitch) {
        if (TraitConfig.number("cataclysm_sounds_enabled") <= 0) return;
        var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cataclysm", path));
        if (sound != null) source.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound,
                net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch);
    }

    private static float pitch(LivingEntity entity) {
        float v = TraitConfig.number("cataclysm_sound_pitch_variation");
        // The level's random, so a sound never shifts the entity's trait rolls.
        return 1f + (entity.level().getRandom().nextFloat() * 2f - 1f) * v;
    }

    /** Weapons slowly mend themselves while carried. Handled in CompatEvents; magnitude is ticks per point. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> SELF_REPAIR = TRAITS.register("self_repair",
            () -> new CompatTrait("self_repair", TraitQuality.POSITIVE).setMagnitude(200.0f).setUniversal());

    /** Weakens the target for a few seconds. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> WEAKENING = TRAITS.register("weakening",
            () -> new CompatTrait("weakening", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) magnitude, 0), attacker);
                }
            }.setMagnitude(60.0f).setMelee());

    /** Wraps the target in darkness and blindness for a few seconds. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> DARKENING = TRAITS.register("darkening",
            () -> new CompatTrait("darkening", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, (int) magnitude, 0), attacker);
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, (int) magnitude, 0), attacker);
                }
            }.setMagnitude(60.0f).setMelee());

    /** The more worn the weapon, the harder it hits: up to +magnitude damage at the last point of durability. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> ZANITE_STRENGTH = TRAITS.register("zanite_strength",
            () -> new CompatTrait("zanite_strength", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    return damage + wornBonus(attacker.getMainHandItem(), magnitude);
                }
            }.setMagnitude(4.0f).setMelee());

    /** Flings the target upwards. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> GRAVITY_STRIKE = TRAITS.register("gravity_strike",
            () -> new CompatTrait("gravity_strike", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    target.push(0.0, magnitude, 0.0);
                    target.hurtMarked = true;
                }
            }.setMagnitude(0.8f).setMelee());

    /** Makes the target glow through walls for a while. */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> GLOW_MARK = TRAITS.register("glow_mark",
            () -> new CompatTrait("glow_mark", TraitQuality.POSITIVE) {
                @Override
                public void onHitEntity(WeaponMaterial material, ItemStack stack, LivingEntity target, LivingEntity attacker, Entity projectile) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) magnitude, 0), attacker);
                }
            }.setMagnitude(200.0f).setMelee());

    /** More damage against the Undergarden's rotspawn (its mobs whose ids start with "rot"). */
    public static final DeferredHolder<WeaponTrait, WeaponTrait> ROTBANE = TRAITS.register("rotbane",
            () -> new CompatTrait("rotbane", TraitQuality.POSITIVE) {
                @Override
                public float modifyDamageDealt(WeaponMaterial material, float damage, DamageSource source, LivingEntity attacker, LivingEntity victim) {
                    return isRotspawn(victim) ? damage * (1.0f + magnitude) : damage;
                }
            }.setMagnitude(0.5f).setMelee());

    private CompatTraits() {}

    /** Zanite Strength's bonus for a stack: magnitude times the share of durability already used. */
    public static float wornBonus(ItemStack stack, float magnitude) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) return 0.0f;
        return magnitude * stack.getDamageValue() / (float) stack.getMaxDamage();
    }

    public static boolean isRotspawn(Entity entity) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key.getNamespace().equals("undergarden") && key.getPath().startsWith("rot");
    }

    /** Adds one level of a stacking effect, up to maxAmp, and refreshes its time. */
    static int stack(LivingEntity entity, Holder<MobEffect> effect, int ticks, int maxAmp) {
        MobEffectInstance current = entity.getEffect(effect);
        int amp = current == null ? 0 : Math.min(current.getAmplifier() + 1, maxAmp);
        entity.addEffect(new MobEffectInstance(effect, ticks, amp));
        return amp;
    }

    /** True when the stack is a Spartan weapon whose material carries the trait. */
    public static boolean has(ItemStack stack, DeferredHolder<WeaponTrait, WeaponTrait> trait) {
        if (stack.isEmpty() || !trait.isBound()) return false;
        return SpartanWeaponryAPI.getWeaponClassification(stack.getItem())
                .map(c -> c.material().getBonusTraits(c.weaponItemType().getWeaponType()).contains(trait.get()))
                .orElse(false);
    }
}
