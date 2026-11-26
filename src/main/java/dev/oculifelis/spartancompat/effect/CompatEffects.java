package dev.oculifelis.spartancompat.effect;

import dev.oculifelis.spartancompat.SpartanCompat;
import dev.oculifelis.spartancompat.config.TraitConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CompatEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SpartanCompat.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> BLAZING_BRAND = EFFECTS.register("blazing_brand",
            () -> new CompatEffect(MobEffectCategory.HARMFUL, 0xF2A23A)
                    .addAttributeModifier(Attributes.ARMOR, SpartanCompat.id("effect.blazing_brand"),
                            -TraitConfig.number("blazing_brand_armor_fraction"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, SpartanCompat.id("effect.blazing_brand_toughness"),
                            -TraitConfig.number("blazing_brand_toughness_fraction"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    // Rage only affects Cursium damage through its trait, not every item the wielder holds.
    public static final DeferredHolder<MobEffect, MobEffect> ACCURSED_RAGE = EFFECTS.register("accursed_rage",
            () -> new CompatEffect(MobEffectCategory.BENEFICIAL, 0x39D2B2));
    public static final DeferredHolder<MobEffect, MobEffect> PULSE_CHARGE = EFFECTS.register("pulse_charge",
            () -> new CompatEffect(MobEffectCategory.BENEFICIAL, 0xAE2334));
    public static final DeferredHolder<MobEffect, MobEffect> PULSE_COOLDOWN = EFFECTS.register("pulse_cooldown",
            () -> new CompatEffect(MobEffectCategory.NEUTRAL, 0x575757) {
                @Override
                public void fillEffectCures(java.util.Set<net.neoforged.neoforge.common.EffectCure> cures,
                                            net.minecraft.world.effect.MobEffectInstance instance) {}
            });
    public static final DeferredHolder<MobEffect, MobEffect> STUN = EFFECTS.register("stun",
            () -> new CompatEffect(MobEffectCategory.HARMFUL, 0x575757)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, SpartanCompat.id("effect.stun_movement"), -1,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, SpartanCompat.id("effect.stun_attack"), -1,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    private CompatEffects() {}

    static class CompatEffect extends MobEffect {
        CompatEffect(MobEffectCategory category, int colour) {
            super(category, colour);
        }
    }
}
