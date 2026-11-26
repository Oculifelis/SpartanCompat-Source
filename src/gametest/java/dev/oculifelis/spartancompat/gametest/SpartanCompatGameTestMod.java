package dev.oculifelis.spartancompat.gametest;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Dev-only test mod: holds the GameTests, which NeoForge finds from @GameTestHolder. Never shipped. */
@Mod(SpartanCompatGameTestMod.ID)
public final class SpartanCompatGameTestMod {
    public static final String ID = "spartan_compat_gametest";

    public SpartanCompatGameTestMod() {
        if (FMLEnvironment.dist.isClient()) dev.oculifelis.spartancompat.gametest.client.ClientSmoke.arm();
    }
}
