package dev.ps5.mcapolyamory.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ps5.mcapolyamory.ProcreationPartner;
import java.util.Optional;
import java.util.UUID;
import net.conczin.mca.entity.ai.BreedableRelationship;
import net.conczin.mca.entity.ai.Relationship;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BreedableRelationship.class, remap = false)
public abstract class BreedableRelationshipMixin extends Relationship implements ProcreationPartner {
    @Unique
    private UUID mcapoly$procreationPartner;

    private BreedableRelationshipMixin() {
        super(null);
    }

    @Override
    public void mcapoly$setProcreationPartner(UUID partner) {
        mcapoly$procreationPartner = partner;
    }

    /** The baby's other parent is whoever asked for it, not necessarily the primary partner. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/conczin/mca/entity/ai/BreedableRelationship;getPartner()Ljava/util/Optional;"))
    private Optional<Entity> mcapoly$partner(BreedableRelationship self, Operation<Optional<Entity>> original) {
        UUID asked = mcapoly$procreationPartner;
        if (asked != null) {
            mcapoly$procreationPartner = null;
            Entity entity = getWorld().getEntity(asked);
            if (entity == null) {
                entity = getWorld().getServer().getPlayerList().getPlayer(asked);
            }
            if (entity != null) {
                return Optional.of(entity);
            }
        }
        return original.call(self);
    }
}
