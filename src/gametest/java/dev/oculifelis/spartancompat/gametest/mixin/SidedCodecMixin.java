package dev.oculifelis.spartancompat.gametest.mixin;

import java.lang.reflect.Method;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dev-only: the dev runtime encodes owo-lib's handshake through the payload map, which skips the
 * place owo unpacks its two-sided codec, so the dev client could not join a world. Unpack it here.
 */
@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
public class SidedCodecMixin {
    @Shadow @Final PacketFlow val$packetFlow;

    @Inject(method = "findCodec", at = @At("RETURN"), cancellable = true)
    private void spartancompat$unpackSided(CallbackInfoReturnable<StreamCodec<?, ?>> cir) {
        Object codec = cir.getReturnValue();
        if (codec == null || !codec.getClass().getName().equals("io.wispforest.owo.network.neoforge.SidedPacketCodec")) return;
        try {
            Method m = codec.getClass().getMethod("getCodec", PacketFlow.class);
            cir.setReturnValue((StreamCodec<?, ?>) m.invoke(codec, val$packetFlow));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
