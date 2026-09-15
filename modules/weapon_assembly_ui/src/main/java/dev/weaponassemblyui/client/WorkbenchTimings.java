package dev.weaponassemblyui.client;

import java.util.Arrays;

/** Optional UI-thread timings, including buffer submission; not a GPU timer or an FPS assertion. */
final class WorkbenchTimings {
    private final boolean enabled=Boolean.getBoolean("weaponassembly.profile");
    private final long[] layout=new long[240],render=new long[240];
    private int count;
    void record(long layoutNanos,long renderNanos,int triangles) {
        if(!enabled)return;
        layout[count]=layoutNanos;render[count]=renderNanos;
        if(++count<layout.length)return;
        System.getLogger("weapon_assembly_ui.performance").log(System.Logger.Level.INFO,
            String.format(java.util.Locale.ROOT,"Workbench CPU 240 frames: layout p50/p95 %.3f/%.3f ms; mesh p50/p95 %.3f/%.3f ms; triangles %d",
                percentile(layout,.5),percentile(layout,.95),percentile(render,.5),percentile(render,.95),triangles));
        count=0;
    }
    private static double percentile(long[] values,double p) {
        var sorted=values.clone();Arrays.sort(sorted);return sorted[(int)((sorted.length-1)*p)]/1_000_000d;
    }
}
