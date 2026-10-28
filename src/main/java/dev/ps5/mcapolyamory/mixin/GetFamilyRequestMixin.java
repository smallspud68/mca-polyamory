package dev.ps5.mcapolyamory.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ps5.mcapolyamory.Poly;
import java.util.UUID;
import java.util.stream.Stream;
import net.conczin.mca.network.c2s.GetFamilyRequest;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The family list (whistle, blueprint) includes every partner. */
@Mixin(value = GetFamilyRequest.class, remap = false)
public abstract class GetFamilyRequestMixin {
    @ModifyExpressionValue(method = "handleServer", at = @At(value = "INVOKE", target = "Ljava/util/Optional;stream()Ljava/util/stream/Stream;"))
    private Stream<UUID> mcapoly$addPartners(Stream<UUID> partner, ServerPlayer player) {
        return Stream.concat(partner, Poly.extras(PlayerSaveData.get(player).getFamilyEntry()).keySet().stream());
    }
}
