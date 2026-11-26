package dev.oculifelis.spartancompat.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.oculifelis.spartancompat.config.TraitConfig;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A datapack file under data/<ns>/spartan_compat/traits/ overrides trait values. */
@GameTestHolder(SpartanCompatGameTestMod.ID)
@PrefixGameTestTemplate(false)
public final class DataPackTests {
    private DataPackTests() {}

    /** The test mod ships data/spartan_compat_gametest/spartan_compat/traits/override_test.json (100 -> 101). */
    @GameTest(template = "arena")
    public static void datapackOverridesTraitValue(GameTestHelper helper) {
        helper.assertTrue(TraitConfig.integer("concentration_reset_ticks") == 101,
                "datapack override of concentration_reset_ticks applied, got " + TraitConfig.integer("concentration_reset_ticks"));
        helper.assertTrue(TraitConfig.integer("numbing_ticks") == 120, "keys not in the pack keep their default");
        helper.succeed();
    }

    /** Bad values in a pack are ignored and the earlier value stays; the live layer is restored afterwards. */
    @GameTest(template = "arena")
    public static void datapackRejectsBadValues(GameTestHelper helper) {
        var good = JsonParser.parseString("{\"concentration_reset_ticks\": 101}");
        var bad = JsonParser.parseString("{\"numbing_chance\": 7, \"no_such_key\": 1, \"numbing_ticks\": 90}");
        TraitConfig.applyDatapacks(Map.<ResourceLocation, JsonElement>of(
                ResourceLocation.fromNamespaceAndPath("a", "good"), good,
                ResourceLocation.fromNamespaceAndPath("b", "bad"), bad));
        boolean ok = TraitConfig.number("numbing_chance") == 1f && TraitConfig.integer("numbing_ticks") == 90;
        TraitConfig.applyDatapacks(Map.of(ResourceLocation.fromNamespaceAndPath("a", "good"), good));
        helper.assertTrue(ok, "out-of-range chance rejected, valid ticks applied");
        helper.succeed();
    }
}
