package dev.oculifelis.spartancompat;

import dev.oculifelis.spartancompat.config.CompatConfig;
import dev.oculifelis.spartancompat.config.TraitConfig;
import dev.oculifelis.spartancompat.content.CompatEvents;
import dev.oculifelis.spartancompat.content.CompatItems;
import dev.oculifelis.spartancompat.effect.CompatEffects;
import dev.oculifelis.spartancompat.trait.CompatTraits;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiyu.spartanweaponryunofficial.api.SpartanWeaponryAPI;

@Mod(SpartanCompat.MOD_ID)
public final class SpartanCompat {
    public static final String MOD_ID = "spartan_compat";
    public static final Logger LOG = LoggerFactory.getLogger("Spartan Compat");

    public SpartanCompat(IEventBus modBus) {
        SpartanWeaponryAPI.assertAPIVersion(MOD_ID, 15);
        CompatConfig.load();
        TraitConfig.load();
        CompatEffects.EFFECTS.register(modBus);
        dev.oculifelis.spartancompat.trait.SourceTraits.init();
        CompatTraits.TRAITS.register(modBus);
        dev.oculifelis.spartancompat.compat.DarkSteelEnergy.COMPONENTS.register(modBus);
        CompatItems.register(modBus);
        modBus.addListener(CompatEvents::modifyComponents);
        modBus.addListener(CompatItems::addToCreativeTab);
        NeoForge.EVENT_BUS.register(CompatEvents.class);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddReloadListenerEvent e) -> e.addListener(new TraitConfig.Reloader()));
        NeoForge.EVENT_BUS.register(dev.oculifelis.spartancompat.compat.SourceEvents.class);
        modBus.addListener(dev.oculifelis.spartancompat.compat.SourceEvents::registerPayloads);
        modBus.addListener(dev.oculifelis.spartancompat.compat.DarkSteelEnergy::registerCapabilities);
        // Botania and Psi classes are only touched when those mods are present.
        if (ModList.get().isLoaded("botania")) modBus.addListener(dev.oculifelis.spartancompat.compat.BotaniaCompat::modifyComponents);
        if (ModList.get().isLoaded("botania") && !dev.oculifelis.spartancompat.compat.BotaniaCompat.HAS_REPAIR_COMPONENT)
            NeoForge.EVENT_BUS.addListener(dev.oculifelis.spartancompat.compat.BotaniaCompat::legacyRepairTick);
        if (ModList.get().isLoaded("psi")) modBus.addListener(dev.oculifelis.spartancompat.compat.PsiCompat::registerCapabilities);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
