package dev.ps5.mcapolyamory;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.entity.ai.relationship.RelationshipType;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public final class Poly {
    /**
     * MCA's endRelationShip() doesn't say which relationship ends, it always means "the" partner.
     * Call sites that know who is involved push a context so the right link is cut.
     *
     * @param subject node whose relationships all end (e.g. the one who died), or null
     * @param involved the other side(s) of the relationship being ended
     */
    public record EndContext(@Nullable UUID subject, Set<UUID> involved) {
    }

    /** What an end request on a given node should remove. */
    public sealed interface EndTarget {
        record Primary() implements EndTarget {
        }

        record All() implements EndTarget {
        }

        record One(UUID partner) implements EndTarget {
        }

        record Nothing() implements EndTarget {
        }
    }

    private static final ThreadLocal<ArrayDeque<EndContext>> CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);

    private Poly() {
    }

    public static <T> T withContext(EndContext context, Supplier<T> action) {
        ArrayDeque<EndContext> stack = CONTEXT.get();
        stack.push(context);
        try {
            return action.get();
        } finally {
            stack.pop();
        }
    }

    public static EndTarget resolveEnd(FamilyTreeNode node) {
        EndContext context = CONTEXT.get().peek();
        if (context == null) {
            return new EndTarget.Primary();
        }
        if (node.id().equals(context.subject())) {
            return new EndTarget.All();
        }
        return Stream.concat(Stream.ofNullable(context.subject()), context.involved().stream())
            .filter(uuid -> !uuid.equals(node.id()) && isLinked(node, uuid))
            .findFirst()
            .<EndTarget>map(EndTarget.One::new)
            .orElseGet(EndTarget.Nothing::new);
    }

    public static Map<UUID, RelationshipState> extras(FamilyTreeNode node) {
        return ((PolyNode) (Object) node).mcapoly$extraPartners();
    }

    public static boolean isRomantic(@Nullable RelationshipState state) {
        return state == RelationshipState.PROMISED || state == RelationshipState.ENGAGED || state != null && state.isMarried();
    }

    public static boolean hasPrimary(FamilyTreeNode node) {
        return FamilyTreeNode.isValid(node.partner()) && isRomantic(node.getRelationshipState());
    }

    public static Optional<RelationshipState> stateWith(FamilyTreeNode node, UUID other) {
        if (hasPrimary(node) && node.partner().equals(other)) {
            return Optional.of(node.getRelationshipState());
        }
        return Optional.ofNullable(extras(node).get(other));
    }

    public static boolean isLinked(FamilyTreeNode node, UUID other) {
        return stateWith(node, other).isPresent();
    }

    public static boolean is(EntityRelationship relationship, UUID other, Predicate<RelationshipState> test) {
        return stateWith(relationship.getFamilyEntry(), other).filter(test).isPresent();
    }

    /** All romantic partners, primary first. */
    public static Stream<UUID> partners(FamilyTreeNode node) {
        Stream<UUID> primary = hasPrimary(node) ? Stream.of(node.partner()) : Stream.empty();
        return Stream.concat(primary, extras(node).keySet().stream());
    }

    public static boolean atLimit(FamilyTreeNode node) {
        int max = PolyConfig.get().maxPartners;
        return max > 0 && partners(node).count() >= max;
    }

    /** Cuts {@code other} out of {@code node}'s relationships, promoting an extra partner if it was the primary. */
    public static void unlink(FamilyTreeNode node, UUID other) {
        if (node.partner().equals(other)) {
            ((PolyNode) (Object) node).mcapoly$setPrimary(net.minecraft.util.Util.NIL_UUID, RelationshipState.SINGLE);
            promote(node);
        } else {
            extras(node).remove(other);
        }
        node.getRoot().setDirty();
    }

    /** Moves the most committed extra partner into MCA's primary slot. */
    public static void promote(FamilyTreeNode node) {
        Map<UUID, RelationshipState> extras = extras(node);
        extras.entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().ordinal()))
            .ifPresent(e -> {
                extras.remove(e.getKey());
                ((PolyNode) (Object) node).mcapoly$setPrimary(e.getKey(), e.getValue());
            });
    }

    /** Swaps an extra partner into the primary slot so MCA's single-partner features use them. */
    public static boolean makePrimary(FamilyTreeNode node, UUID partner) {
        Map<UUID, RelationshipState> extras = extras(node);
        RelationshipState state = extras.remove(partner);
        if (state == null) {
            return false;
        }
        if (hasPrimary(node)) {
            extras.put(node.partner(), node.getRelationshipState());
        }
        ((PolyNode) (Object) node).mcapoly$setPrimary(partner, state);
        node.getRoot().setDirty();
        return true;
    }

    /** Wraps MCA's onTragedy: on death, also tell the extra partners, and end every relationship of the deceased. */
    public static void onTragedy(EntityRelationship self, DamageSource cause, @Nullable BlockPos burialSite, RelationshipType type,
                                 Entity victim, Runnable original) {
        if (type != RelationshipType.SELF) {
            original.run();
            return;
        }
        withContext(new EndContext(self.getUUID(), Set.of()), () -> {
            List<UUID> extras = List.copyOf(extras(self.getFamilyEntry()).keySet());
            self.getRelationshipStream(extras.stream()).forEach(r -> r.onTragedy(cause, burialSite, RelationshipType.SPOUSE, victim));
            original.run();
            return null;
        });
    }
}
