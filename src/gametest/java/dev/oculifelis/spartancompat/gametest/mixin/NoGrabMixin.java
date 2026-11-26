package dev.oculifelis.spartancompat.gametest.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev-only: test clients never take the mouse. */
@Mixin(MouseHandler.class)
public class NoGrabMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void spartancompat$noGrab(CallbackInfo ci) {
        ci.cancel();
    }
}
