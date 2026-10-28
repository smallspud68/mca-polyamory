package dev.ps5.mcapolyamory.mixin;

import dev.ps5.mcapolyamory.Poly;
import dev.ps5.mcapolyamory.PolyNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FamilyTreeNode.class, remap = false)
public abstract class FamilyTreeNodeMixin implements PolyNode {
    @Unique
    private static final String TAG = "mcaPolyPartners";

    @Shadow @Final private UUID id;
    @Shadow @Final private transient FamilyTree rootNode;
    @Shadow private UUID partner;
    @Shadow private RelationshipState relationshipState;

    @Unique
    private final Map<UUID, RelationshipState> mcapoly$extras = new LinkedHashMap<>();

    @Override
    public Map<UUID, RelationshipState> mcapoly$extraPartners() {
        return mcapoly$extras;
    }

    @Override
    public void mcapoly$setPrimary(UUID partner, RelationshipState state) {
        this.partner = partner;
        this.relationshipState = state;
    }

    @Inject(method = "<init>(Lnet/conczin/mca/server/world/data/FamilyTree;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void mcapoly$read(FamilyTree root, CompoundTag nbt, CallbackInfo ci) {
        nbt.getList(TAG).ifPresent(list -> {
            for (Tag tag : list) {
                if (tag instanceof CompoundTag entry) {
                    entry.read("uuid", UUIDUtil.CODEC).ifPresent(uuid ->
                        mcapoly$extras.put(uuid, RelationshipState.byId(entry.getIntOr("state", 0))));
                }
            }
        });
        mcapoly$extras.values().removeIf(state -> !Poly.isRomantic(state));
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void mcapoly$write(CallbackInfoReturnable<CompoundTag> cir) {
        if (mcapoly$extras.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        mcapoly$extras.forEach((uuid, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.store("uuid", UUIDUtil.CODEC, uuid);
            entry.putInt("state", state.ordinal());
            list.add(entry);
        });
        cir.getReturnValue().put(TAG, list);
    }

    @Inject(method = "willBeRemembered", at = @At("HEAD"), cancellable = true)
    private void mcapoly$remembered(CallbackInfoReturnable<Boolean> cir) {
        if (!mcapoly$extras.isEmpty()) {
            cir.setReturnValue(true);
        }
    }

    /**
     * MCA's only way to start or end a romance. New partners go to the extra slots when the primary
     * slot is taken, and ending only cuts the relationship the current context names.
     */
    @Inject(method = "updatePartner(Lnet/minecraft/world/entity/Entity;Lnet/conczin/mca/entity/ai/relationship/RelationshipState;)V",
        at = @At("HEAD"), cancellable = true)
    private void mcapoly$updatePartner(@Nullable Entity newPartner, @Nullable RelationshipState state, CallbackInfo ci) {
        FamilyTreeNode self = (FamilyTreeNode) (Object) this;

        if (newPartner != null) {
            UUID other = newPartner.getUUID();
            if (other.equals(partner)) {
                mcapoly$extras.remove(other);
                return; // MCA upgrades the primary (promised -> engaged -> married)
            }
            if (mcapoly$extras.containsKey(other) || Poly.hasPrimary(self)) {
                mcapoly$extras.put(other, state);
                rootNode.getOrCreate(newPartner);
                rootNode.setDirty();
                ci.cancel();
            }
            return; // free primary slot: MCA's own code takes it
        }

        switch (Poly.resolveEnd(self)) {
            case Poly.EndTarget.Nothing nothing -> {
            }
            case Poly.EndTarget.One one when !one.partner().equals(partner) -> {
                mcapoly$extras.remove(one.partner());
                rootNode.getOrEmpty(one.partner()).ifPresent(n -> Poly.unlink(n, id));
            }
            case Poly.EndTarget target -> {
                if (target instanceof Poly.EndTarget.All) {
                    for (UUID extra : new ArrayList<>(mcapoly$extras.keySet())) {
                        rootNode.getOrEmpty(extra).ifPresent(n -> Poly.unlink(n, id));
                    }
                    mcapoly$extras.clear();
                }
                UUID old = partner;
                partner = Util.NIL_UUID;
                relationshipState = state == null ? RelationshipState.SINGLE : state;
                if (FamilyTreeNode.isValid(old)) {
                    rootNode.getOrEmpty(old).ifPresent(n -> Poly.unlink(n, id));
                }
                Poly.promote(self);
            }
        }
        rootNode.setDirty();
        ci.cancel();
    }
}
