package dev.oculifelis.spartancompat.gametest;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import vazkii.psi.api.cad.ISocketable;
import vazkii.psi.api.capability.PsiCapabilities;

/** Psi-only calls for SourceTraitTests; loaded only when Psi is present. */
final class PsiChecks {
    private PsiChecks() {}

    /** Null when the psimetal weapon exposes three bullet sockets and a bullet item handler, else the problem. */
    static String sockets(ItemStack stack) {
        ISocketable s = stack.getCapability(ItemCapability.createVoid(PsiCapabilities.SOCKETABLE, ISocketable.class));
        if (s == null) return "psimetal has no socketable capability";
        if (!ISocketable.isSocketable(stack)) return "Psi does not see psimetal as socketable";
        if (!s.isSocketSlotAvailable(2) || s.isSocketSlotAvailable(3)) return "psimetal does not have exactly 3 sockets";
        var items = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (items == null || items.getSlots() != 3) return "psimetal has no 3-slot bullet handler";
        return null;
    }
}
