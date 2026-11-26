package dev.oculifelis.spartancompat.content;

import dev.oculifelis.spartancompat.config.CompatConfig;
import dev.oculifelis.spartancompat.effect.CompatEffects;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.xiyu.spartanweaponryunofficial.util.WeaponType;
import dev.oculifelis.spartancompat.trait.CompatTraits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;

public final class CompatEvents {
    private CompatEvents() {}

    /** Mod bus: rarity, unbreakable and fire-resistant materials get those components on every weapon. */
    public static void modifyComponents(ModifyDefaultComponentsEvent event) {
        for (DeferredItem<Item> item : CompatItems.WEAPONS) {
            CompatConfig.Material m = CompatItems.SETTINGS.get(CompatItems.MATERIAL_OF.get(item.getId().getPath()));
            if (m == null) continue;
            if (m.rarity() != null) {
                net.minecraft.world.item.Rarity r = net.minecraft.world.item.Rarity.valueOf(m.rarity().toUpperCase(java.util.Locale.ROOT));
                event.modify(item, b -> b.set(DataComponents.RARITY, r));
            }
            if (m.unbreakable()) event.modify(item, b -> b.set(DataComponents.UNBREAKABLE, new Unbreakable(true)));
            if (m.fireproof()) event.modify(item, b -> b.set(DataComponents.FIRE_RESISTANT, net.minecraft.util.Unit.INSTANCE));
        }
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        TwilightEnchantments.initialize(event.getCrafting(), event.getEntity().level().registryAccess());
    }

    /** The modern ranged API has spawn callbacks only. Thrown weapons already dispatch melee callbacks. */
    @SubscribeEvent
    public static void onRangedHit(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker.hasEffect(CompatEffects.STUN)) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)
                || !(arrow.getOwner() instanceof LivingEntity attacker)) return;
        String name = arrow.getPersistentData().getString(CompatTraits.RANGED_MATERIAL);
        var material = CompatItems.MATERIALS.get(name);
        if (material == null) return;
        float damage = event.getAmount();
        for (var trait : material.getBonusTraits(WeaponType.RANGED)) {
            if (!(trait instanceof dev.oculifelis.spartancompat.trait.CompatTrait)) continue;
            var callback = trait.getMeleeCallback().orElse(null);
            if (callback == null) continue;
            damage = callback.modifyDamageDealt(material, damage, event.getSource(), attacker, event.getEntity());
            callback.onHitEntity(material, ItemStack.EMPTY, event.getEntity(), attacker, arrow);
        }
        event.setAmount(damage);
    }

    /** Bountiful: each drop has a chance to come twice. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return;
        if (!CompatTraits.has(killer.getMainHandItem(), CompatTraits.BOUNTIFUL)) return;
        float chance = CompatTraits.BOUNTIFUL.get().getMagnitude();
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            if (killer.getRandom().nextFloat() < chance) {
                extra.add(new ItemEntity(drop.level(), drop.getX(), drop.getY(), drop.getZ(), drop.getItem().copy()));
            }
        }
        event.getDrops().addAll(extra);
    }

    /** Neptune's Strike: undo water drag on marked projectiles (vanilla slows arrows to 0.6 per tick in water). */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living && living.hasEffect(CompatEffects.STUN)) {
            living.setDeltaMovement(0, Math.min(0, living.getDeltaMovement().y), 0);
            if (living instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop();
        }
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!arrow.isInWater() || !arrow.getPersistentData().getBoolean(CompatTraits.NEPTUNE_TAG)) return;
        if (arrow.onGround() || arrow.getDeltaMovement().lengthSqr() < 1.0E-4) return;
        Vec3 v = arrow.getDeltaMovement();
        arrow.setDeltaMovement(v.scale(0.99 / 0.6));
    }

    /** Self-Repair: every few seconds, each carried weapon with the trait mends one point. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !CompatTraits.SELF_REPAIR.isBound()) return;
        int every = Math.max(1, (int) CompatTraits.SELF_REPAIR.get().getMagnitude());
        if ((player.level().getGameTime() + player.getId()) % every != 0) return;
        for (ItemStack stack : player.getInventory().items) mend(stack);
        mend(player.getOffhandItem());
    }

    private static void mend(ItemStack stack) {
        if (stack.isDamaged() && CompatTraits.has(stack, CompatTraits.SELF_REPAIR)) stack.setDamageValue(stack.getDamageValue() - 1);
    }
}
