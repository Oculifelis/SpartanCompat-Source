package dev.oculifelis.spartancompat.gametest.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game window opens hidden and unfocused, then shows at -4000,-4000 without taking focus. */
@Mixin(Window.class)
public class WindowMixin {
    @Shadow @Final private long window;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J", remap = false))
    private void spartancompat$hints(CallbackInfo ci) {
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void spartancompat$offscreen(CallbackInfo ci) {
        GLFW.glfwSetWindowPos(window, -4000, -4000);
        GLFW.glfwShowWindow(window);
        GLFW.glfwSetWindowPos(window, -4000, -4000);
    }
}
