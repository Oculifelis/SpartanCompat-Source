package dev.oculifelis.spartancompat.gametest.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Dev-only: NeoForge 21.1.24x and later throw on a value registered twice. T.O Magic 'n Extras 4.4.0.1
 * registers one loot modifier codec under two names, which the NeoForge its packs run on only logs.
 * Restore that for T.O so the full test set can boot on the build's NeoForge.
 */
@Mixin(MappedRegistry.class)
public class DuplicateValueMixin<T> {
    @ModifyExpressionValue(method = "register(ILnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;containsKey(Ljava/lang/Object;)Z", ordinal = 1))
    private boolean spartancompat$allowToDuplicate(boolean present, int id, ResourceKey<T> key, T value, RegistrationInfo info) {
        return present && !String.valueOf(value).contains("com.gametechbc.traveloptics");
    }
}
