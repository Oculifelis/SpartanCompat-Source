package dev.oculifelis.spartancompat.compat;

import dev.oculifelis.spartancompat.content.CompatItems;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import vazkii.botania.common.component.BotaniaDataComponents;
import vazkii.botania.common.component.ManaRepair;

/** Botania builds that have the MANA_REPAIR component (457 and later): Botania's own hook does the mending. */
final class BotaniaManaRepair {
    private BotaniaManaRepair() {}

    static void modifyComponents(ModifyDefaultComponentsEvent event) {
        for (DeferredItem<Item> item : CompatItems.WEAPONS) {
            int cost = BotaniaCompat.manaPerDamage(CompatItems.MATERIAL_OF.get(item.getId().getPath()));
            if (cost > 0) event.modify(item, b -> b.set(BotaniaDataComponents.MANA_REPAIR, new ManaRepair(cost, cost * 2)));
        }
    }
}
