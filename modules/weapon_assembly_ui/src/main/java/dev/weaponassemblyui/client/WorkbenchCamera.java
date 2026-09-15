package dev.weaponassemblyui.client;

/** Input state in design-independent screen coordinates; no inertia or integer wheel quantization. */
final class WorkbenchCamera {
    double yaw=-Math.PI/2,pitch,zoom=1,panX,panY;
    void rotate(double dx,double dy) { yaw+=dx*.012;pitch=Math.clamp(pitch+dy*.012,-1.35,1.35); }
    void zoomAt(double delta,double mouseX,double mouseY,double centerX,double centerY) {
        if(!Double.isFinite(delta))return;
        double next=Math.clamp(zoom*Math.exp(Math.clamp(delta*.12,-50,50)),.25,6);
        double ratio=next/zoom;
        panX=mouseX-centerX-(mouseX-centerX-panX)*ratio;
        panY=mouseY-centerY-(mouseY-centerY-panY)*ratio;
        zoom=next;
    }
    void reset() { yaw=-Math.PI/2;pitch=0;zoom=1;panX=panY=0; }
}
