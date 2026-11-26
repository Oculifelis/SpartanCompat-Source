package dev.oculifelis.spartancompat.content;

import dev.oculifelis.spartancompat.SpartanCompat;
import dev.oculifelis.spartancompat.config.TraitConfig;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Applies the original packages once; a grindstone can remove them permanently. */
public final class TwilightEnchantments {
    private static final String APPLIED = SpartanCompat.MOD_ID + ":twilight_enchantments";
    private static final Map<String, String> IRONWOOD = Map.of(
            "minecraft:knockback", "ironwood_knockback_level", "minecraft:fortune", "ironwood_fortune_level",
            "spartan_weaponry_unofficial:propel", "ironwood_propel_level", "minecraft:punch", "ironwood_punch_level");
    private static final Map<String, String> STEELEAF = Map.of(
            "minecraft:looting", "steeleaf_looting_level", "minecraft:efficiency", "steeleaf_efficiency_level",
            "spartan_weaponry_unofficial:lucky_throw", "steeleaf_lucky_throw_level", "minecraft:quick_charge", "steeleaf_quick_charge_level");

    private TwilightEnchantments() {}

    public static void initialize(ItemStack stack, RegistryAccess access) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals(SpartanCompat.MOD_ID)) return;
        String material = CompatItems.MATERIAL_OF.get(id.getPath());
        Map<String, String> pack = "ironwood".equals(material) ? IRONWOOD : "steeleaf".equals(material) ? STEELEAF : Map.of();
        if (pack.isEmpty() || stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(APPLIED)) return;
        var registry = access.registryOrThrow(Registries.ENCHANTMENT);
        EnchantmentHelper.updateEnchantments(stack, enchants -> pack.forEach((key, setting) ->
                registry.getHolder(ResourceLocation.parse(key)).ifPresent(holder -> {
                    int level = TraitConfig.integer(setting);
                    if (level > 0 && holder.value().isSupportedItem(stack)) enchants.upgrade(holder, level);
                })));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(APPLIED, true));
    }
}
