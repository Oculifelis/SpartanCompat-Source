package dev.oculifelis.spartancompat.compat;

import dev.oculifelis.spartancompat.SpartanCompat;
import dev.oculifelis.spartancompat.content.CompatItems;
import dev.oculifelis.spartancompat.trait.SourceTraits;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Forge Energy for dark steel weapons (the "empowered" trait). Ender IO 1.21.1 has no powered tools yet, so this is
 * our own take on its Empowered upgrade: the weapon holds FE, stored FE takes durability loss, and charged hits
 * strike harder. Charge it in any FE charger. Turned off with dark_steel_energy_enabled = 0 in traits.json.
 */
public final class DarkSteelEnergy {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SpartanCompat.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY = COMPONENTS.registerComponentType(
            "energy", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private DarkSteelEnergy() {}

    public static int capacity() {
        return SourceTraits.EMPOWERED.isBound() ? (int) SourceTraits.EMPOWERED.get().getMagnitude() : 100000;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (!SourceTraits.flag("dark_steel_energy_enabled")) return;
        Item[] items = CompatItems.WEAPONS.stream()
                .filter(w -> "dark_steel".equals(CompatItems.MATERIAL_OF.get(w.getId().getPath())))
                .map(w -> (Item) w.get()).toArray(Item[]::new);
        if (items.length == 0) return;
        event.registerItem(Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> new ComponentEnergyStorage(stack, ENERGY.get(), capacity()), items);
    }

    public static int get(ItemStack stack) {
        return stack.getOrDefault(ENERGY.get(), 0);
    }

    public static void set(ItemStack stack, int fe) {
        stack.set(ENERGY.get(), Math.max(0, Math.min(fe, capacity())));
    }

    /** Takes {@code fe} from the stack if it holds that much. */
    public static boolean drain(ItemStack stack, int fe) {
        if (stack.isEmpty() || fe <= 0) return false;
        int have = get(stack);
        if (have < fe) return false;
        set(stack, have - fe);
        return true;
    }
}
