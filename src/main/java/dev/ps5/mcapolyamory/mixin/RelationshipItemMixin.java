package dev.ps5.mcapolyamory.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ps5.mcapolyamory.Poly;
import dev.ps5.mcapolyamory.PolyConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.item.RelationshipItem;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Bouquets and rings: being taken no longer blocks a new romance. */
@Mixin(value = RelationshipItem.class, remap = false)
public abstract class RelationshipItemMixin {
    @ModifyExpressionValue(method = "validate", at = @At(value = "INVOKE",
        target = "Lnet/conczin/mca/entity/ai/BreedableRelationship;isMarried()Z"))
    private boolean mcapoly$villagerMarried(boolean married) {
        return married && !PolyConfig.get().allowTakenVillagers;
    }

    @ModifyExpressionValue(method = "validate", at = @At(value = "INVOKE",
        target = "Lnet/conczin/mca/entity/ai/BreedableRelationship;isEngaged()Z"))
    private boolean mcapoly$villagerEngaged(boolean engaged) {
        return engaged && !PolyConfig.get().allowTakenVillagers;
    }

    @ModifyExpressionValue(method = "validate", at = @At(value = "INVOKE",
        target = "Lnet/conczin/mca/server/world/data/PlayerSaveData;isMarried()Z"))
    private boolean mcapoly$playerMarried(boolean married, ServerPlayer player, VillagerEntityMCA villager) {
        // upgrading an existing partner (bouquet -> ring) never counts against the limit
        FamilyTreeNode me = PlayerSaveData.get(player).getFamilyEntry();
        return Poly.atLimit(me) && !Poly.isLinked(me, villager.getUUID());
    }
}
