package dev.ps5.mcapolyamory;

import java.util.Map;
import java.util.UUID;
import net.conczin.mca.entity.ai.relationship.RelationshipState;

/**
 * Duck interface mixed into MCA's FamilyTreeNode. MCA keeps one "primary" partner per node;
 * every other romantic partner lives in the extra map (insertion ordered).
 */
public interface PolyNode {
    Map<UUID, RelationshipState> mcapoly$extraPartners();

    void mcapoly$setPrimary(UUID partner, RelationshipState state);
}
