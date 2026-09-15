package dev.tacticaltacz;
import dev.tacticalcombat.api.BallisticProfile;
import dev.tacticalcombat.api.BulletImpactEvent;
public interface ImpactCarrier {
    void tacticalInitializeContinuation(ContinuationState state, dev.tacticalcombat.api.ProjectileContinuation continuation);
    BallisticProfile tacticalAmmo();
    BulletImpactEvent tacticalImpact();
}
