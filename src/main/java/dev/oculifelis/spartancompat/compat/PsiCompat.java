package dev.oculifelis.spartancompat.compat;

import dev.oculifelis.spartancompat.content.CompatItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.ComponentItemHandler;
import vazkii.psi.api.PsiAPI;
import vazkii.psi.api.cad.IPsiBarDisplay;
import vazkii.psi.api.cad.ISocketable;
import vazkii.psi.api.capability.PsiCapabilities;
import vazkii.psi.api.spell.ISpellAcceptor;
import vazkii.psi.common.item.ItemCAD;
import vazkii.psi.common.item.base.ModDataComponents;
import vazkii.psi.common.item.tool.IPsimetalTool;
import vazkii.psi.common.item.tool.ToolSocketable;
import vazkii.psi.common.core.handler.PsiPlayerData;

/**
 * Psimetal weapons behave like Psi's own psimetal tools: three bullet sockets (CAD Assembler and the socket radial
 * menu work on them), the selected spell is cast on every melee hit, and they regenerate while not held.
 * Only loaded when Psi is present.
 */
public final class PsiCompat {
    public static final int SOCKETS = 3;
    private static final ItemCapability<ISocketable, Void> SOCKETABLE = ItemCapability.createVoid(PsiCapabilities.SOCKETABLE, ISocketable.class);
    private static final ItemCapability<IPsiBarDisplay, Void> BAR = ItemCapability.createVoid(PsiCapabilities.PSI_BAR_DISPLAY, IPsiBarDisplay.class);
    private static final ItemCapability<ISpellAcceptor, Void> ACCEPTOR = ItemCapability.createVoid(PsiCapabilities.SPELL_ACCEPTOR, ISpellAcceptor.class);

    private PsiCompat() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        Item[] items = CompatItems.WEAPONS.stream()
                .filter(w -> "psimetal".equals(CompatItems.MATERIAL_OF.get(w.getId().getPath())))
                .map(w -> (Item) w.get()).toArray(Item[]::new);
        if (items.length == 0) return;
        event.registerItem(SOCKETABLE, (stack, ctx) -> new ToolSocketable(stack, SOCKETS), items);
        event.registerItem(BAR, (stack, ctx) -> new ToolSocketable(stack, SOCKETS), items);
        event.registerItem(ACCEPTOR, (stack, ctx) -> new ToolSocketable(stack, SOCKETS), items);
        event.registerItem(Capabilities.ItemHandler.ITEM,
                (stack, ctx) -> new ComponentItemHandler(stack, ModDataComponents.BULLETS.get(), SOCKETS), items);
    }

    /** The psimetal sword's hurtEnemy: cast the selected bullet through the player's CAD, with the target set. */
    public static void castOnHit(ItemStack stack, LivingEntity target, Player player) {
        if (player.level().isClientSide() || !enabled(stack)) return;
        ItemStack cad = PsiAPI.getPlayerCAD(player);
        if (cad.isEmpty()) return;
        ISocketable sockets = stack.getCapability(SOCKETABLE);
        if (sockets == null) return;
        ItemStack bullet = sockets.getSelectedBullet();
        if (bullet.isEmpty()) return;
        ItemCAD.cast(player.level(), player, PsiPlayerData.get(player), bullet, cad, 5, 10, 0.05F, ctx -> {
            ctx.attackedEntity = target;
            ctx.tool = stack;
        });
    }

    /** Like psimetal tools: casting stops one point before breaking. */
    public static boolean enabled(ItemStack stack) {
        return stack.getDamageValue() < stack.getMaxDamage() - 1;
    }

    public static void regen(ItemStack stack, Entity holder) {
        IPsimetalTool.regen(stack, holder);
    }
}
