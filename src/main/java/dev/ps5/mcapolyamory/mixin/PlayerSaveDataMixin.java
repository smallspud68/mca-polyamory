package dev.ps5.mcapolyamory.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ps5.mcapolyamory.Poly;
import java.util.UUID;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.entity.ai.relationship.RelationshipType;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/** Partner checks that also see the extra partners (MCA's interface defaults only look at the primary). */
@Mixin(value = PlayerSaveData.class, remap = false)
public abstract class PlayerSaveDataMixin implements EntityRelationship {
    @Override
    public boolean isMarriedTo(UUID uuid) {
        return Poly.is(this, uuid, RelationshipState::isMarried);
    }

    @Override
    public boolean isEngagedWith(UUID uuid) {
        return Poly.is(this, uuid, state -> state == RelationshipState.ENGAGED);
    }

    @Override
    public boolean isPromisedTo(UUID uuid) {
        return Poly.is(this, uuid, state -> state == RelationshipState.PROMISED);
    }

    @WrapMethod(method = "onTragedy(Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/core/BlockPos;Lnet/conczin/mca/entity/ai/relationship/RelationshipType;Lnet/minecraft/world/entity/Entity;)V")
    private void mcapoly$onTragedy(DamageSource cause, @Nullable BlockPos burialSite, RelationshipType type, Entity victim, Operation<Void> original) {
        Poly.onTragedy(this, cause, burialSite, type, victim, () -> original.call(cause, burialSite, type, victim));
    }

    /** Resetting a player (admin "reset marriage") clears all of their relationships. */
    @WrapMethod(method = "reset")
    private void mcapoly$reset(Operation<Void> original) {
        Poly.withContext(new Poly.EndContext(getUUID(), java.util.Set.of()), () -> original.call());
    }
}
