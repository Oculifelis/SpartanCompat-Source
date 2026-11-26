package dev.oculifelis.spartancompat.trait;

import dev.oculifelis.spartancompat.SpartanCompat;
import java.util.Optional;
import org.xiyu.spartanweaponryunofficial.api.trait.IGenericTraitCallback;
import org.xiyu.spartanweaponryunofficial.api.trait.IMeleeTraitCallback;
import org.xiyu.spartanweaponryunofficial.api.trait.IRangedTraitCallback;
import org.xiyu.spartanweaponryunofficial.api.trait.IThrowingTraitCallback;
import org.xiyu.spartanweaponryunofficial.api.trait.WeaponTrait;

/**
 * Base for this mod's traits: one class that can answer every callback kind. Which callbacks the base mod asks for
 * follows the melee/ranged/throwing flags set on the trait.
 */
public class CompatTrait extends WeaponTrait implements IMeleeTraitCallback, IRangedTraitCallback, IThrowingTraitCallback {
    public CompatTrait(String type, TraitQuality quality) {
        super(type, SpartanCompat.MOD_ID, quality);
    }

    @Override
    public void onProjectileSpawn(org.xiyu.spartanweaponryunofficial.api.WeaponMaterial material,
                                  net.minecraft.world.entity.projectile.AbstractArrow projectile) {
        projectile.getPersistentData().putString(CompatTraits.RANGED_MATERIAL, material.getMaterialName());
    }

    @Override
    public Optional<IGenericTraitCallback> getGenericCallback() {
        return Optional.of(this);
    }

    @Override
    public Optional<IMeleeTraitCallback> getMeleeCallback() {
        return isMelee ? Optional.of(this) : Optional.empty();
    }

    @Override
    public Optional<IRangedTraitCallback> getRangedCallback() {
        return isRanged ? Optional.of(this) : Optional.empty();
    }

    @Override
    public Optional<IThrowingTraitCallback> getThrowingCallback() {
        return isThrowing ? Optional.of(this) : Optional.empty();
    }
}
