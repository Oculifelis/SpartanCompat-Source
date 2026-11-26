package dev.oculifelis.spartancompat.gametest;

import dev.oculifelis.spartancompat.compat.BotaniaCompat;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.common.entity.ManaBurstEntity;
import vazkii.botania.common.item.BotaniaItems;

/** Botania-only calls for SourceTraitTests; loaded only when Botania is present. */
final class BotaniaChecks {
    private BotaniaChecks() {}

    static boolean beam(Player player, float strength) {
        return BotaniaCompat.trySpawnBeam(player, strength);
    }

    static int burstsNear(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ManaBurstEntity.class, helper.getBounds().inflate(16)).size();
    }

    /** A full mana tablet. */
    static ItemStack fullTablet() {
        ItemStack tablet = new ItemStack(BotaniaItems.MANA_TABLET);
        ManaItem mana = ManaItem.LOOKUP.find(tablet);
        mana.addMana(mana.getMaxMana());
        return tablet;
    }

    static int mana(ItemStack tablet) {
        return ManaItem.LOOKUP.find(tablet).getMana();
    }

    /** One player tick as the game posts it. */
    static void tick(Player player) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    }
}
