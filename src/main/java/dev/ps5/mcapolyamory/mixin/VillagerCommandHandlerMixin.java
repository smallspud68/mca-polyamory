package dev.ps5.mcapolyamory.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ps5.mcapolyamory.Poly;
import dev.ps5.mcapolyamory.ProcreationPartner;
import java.util.Set;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.EntityCommandHandler;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VillagerCommandHandler.class, remap = false)
public abstract class VillagerCommandHandlerMixin extends EntityCommandHandler<VillagerEntityMCA> {
    private VillagerCommandHandlerMixin() {
        super(null);
    }

    /** Divorce only ends this villager's relationship with this player. */
    @WrapMethod(method = "handle")
    private boolean mcapoly$handle(ServerPlayer player, String command, Operation<Boolean> original) {
        if (!"divorceConfirm".equals(command)) {
            return original.call(player, command);
        }
        return Poly.withContext(new Poly.EndContext(null, Set.of(entity.getUUID(), player.getUUID())),
            () -> original.call(player, command));
    }

    /** Remember who asked for a baby, MCA would otherwise use the villager's primary partner. */
    @Inject(method = "handle", at = @At(value = "INVOKE",
        target = "Lnet/conczin/mca/entity/ai/BreedableRelationship;startProcreating(J)V"))
    private void mcapoly$procreate(ServerPlayer player, String command, CallbackInfoReturnable<Boolean> cir) {
        ((ProcreationPartner) entity.getRelationships()).mcapoly$setProcreationPartner(player.getUUID());
    }
}
