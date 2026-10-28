package dev.ps5.mcapolyamory.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ps5.mcapolyamory.Poly;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.conczin.mca.server.ServerInteractionManager;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player-to-player marriage (/mca propose, /mca separate). */
@Mixin(value = ServerInteractionManager.class, remap = false)
public abstract class ServerInteractionManagerMixin {
    @Inject(method = "sendProposal", at = @At("HEAD"), cancellable = true)
    private void mcapoly$alreadyPartners(ServerPlayer sender, ServerPlayer receiver, CallbackInfo ci) {
        if (Poly.isLinked(PlayerSaveData.get(sender).getFamilyEntry(), receiver.getUUID())) {
            sender.sendSystemMessage(Component.literal("You are already with " + receiver.getScoreboardName() + ".").withStyle(ChatFormatting.RED));
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "sendProposal", at = @At(value = "INVOKE",
        target = "Lnet/conczin/mca/server/world/data/PlayerSaveData;isMarried()Z"))
    private boolean mcapoly$proposeWhileMarried(boolean married, ServerPlayer sender, ServerPlayer receiver) {
        return Poly.atLimit(PlayerSaveData.get(sender).getFamilyEntry());
    }

    /** /mca separate ends the marriage with the primary partner, on both sides only. */
    @WrapMethod(method = "endMarriage")
    private void mcapoly$endMarriage(ServerPlayer sender, Operation<Void> original) {
        Set<UUID> involved = new HashSet<>();
        involved.add(sender.getUUID());
        PlayerSaveData.get(sender).getPartnerUUID().ifPresent(involved::add);
        Poly.withContext(new Poly.EndContext(null, involved), () -> original.call(sender));
    }
}
