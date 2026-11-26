package dev.oculifelis.spartancompat.compat;

import dev.oculifelis.spartancompat.content.CompatItems;
import dev.oculifelis.spartancompat.trait.CompatTraits;
import dev.oculifelis.spartancompat.trait.SourceTraits;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import vazkii.botania.api.mana.ManaItemHandler;
import vazkii.botania.common.entity.ManaBurstEntity;
import vazkii.botania.common.item.BotaniaItems;
import vazkii.botania.common.item.equipment.tool.terrasteel.TerraBladeItem;

/**
 * Botania behaviour for manasteel, elementium and terrasteel weapons. Only loaded when Botania is present.
 * Mana repair: Botania 457+ mends any stack carrying its MANA_REPAIR component, so the weapons get that component.
 * Older builds (455 and before) have no such component; there the weapons mend themselves each tick the same way
 * Botania's tools of those builds do. Only the stable API (ManaItemHandler) and members present in both are used here.
 */
public final class BotaniaCompat {
    private BotaniaCompat() {}

    private static final ResourceLocation TERRABLADE_SOUND = ResourceLocation.fromNamespaceAndPath("botania", "terrablade");
    /** True when this Botania build has the MANA_REPAIR data component. */
    public static final boolean HAS_REPAIR_COMPONENT = classExists("vazkii.botania.common.component.ManaRepair");

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, BotaniaCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Mana per point of durability, as Botania's tools of the same metal; 0 = no mana repair. */
    static int manaPerDamage(String material) {
        return switch (material == null ? "" : material) {
            case "manasteel", "elementium" -> 60;
            case "terrasteel" -> 100;
            default -> 0;
        };
    }

    public static void modifyComponents(ModifyDefaultComponentsEvent event) {
        if (HAS_REPAIR_COMPONENT) BotaniaManaRepair.modifyComponents(event);
    }

    /** Older Botania: mend one point per tick from the holder's mana, like ManasteelSwordItem.inventoryTick there. */
    public static void legacyRepairTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || stack.getDamageValue() <= 0) continue;
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!id.getNamespace().equals("spartan_compat")) continue;
            int cost = manaPerDamage(CompatItems.MATERIAL_OF.get(id.getPath()));
            if (cost <= 0) continue;
            if (ManaItemHandler.instance().requestManaExactForTool(stack, player, cost * 2, true))
                stack.setDamageValue(stack.getDamageValue() - 1);
        }
    }

    /** The Terra Blade's beam: at full swing strength, fire a burst with the Terra Blade's own lens behaviour. */
    public static boolean trySpawnBeam(Player player, float strength) {
        if (player.level().isClientSide() || strength < 1.0F || !SourceTraits.flag("terra_beam_enabled")) return false;
        ItemStack stack = player.getMainHandItem();
        if (!CompatTraits.has(stack, SourceTraits.TERRA_BEAM)) return false;
        ManaBurstEntity burst = TerraBladeItem.getBurst(player, new ItemStack(BotaniaItems.TERRA_BLADE));
        player.level().addFreshEntity(burst);
        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        // By id: the sound field was renamed between Botania builds (terraBlade -> TERRABLADE).
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(TERRABLADE_SOUND);
        if (sound != null) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1, 1);
        return true;
    }
}
