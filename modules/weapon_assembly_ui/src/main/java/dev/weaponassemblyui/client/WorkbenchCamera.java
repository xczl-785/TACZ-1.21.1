package dev.weaponassemblyui.client;

/** Tarkov-style fixed-center orbit state; framing is automatic and has no user zoom or pan. */
final class WorkbenchCamera {
    double yaw=-Math.PI/2,pitch;
    void rotate(double dx,double dy) { yaw+=dx*.012;pitch=Math.clamp(pitch+dy*.012,-1.35,1.35); }
    void reset() { yaw=-Math.PI/2;pitch=0; }
}
