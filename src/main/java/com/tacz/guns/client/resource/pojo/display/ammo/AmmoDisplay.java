package com.tacz.guns.client.resource.pojo.display.ammo;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.client.resource.pojo.display.IDisplay;
import javax.annotation.Nullable;

/** Caliber effects only. Native ammunition item models/icons were retired. */
public class AmmoDisplay implements IDisplay {
    @Nullable
    @SerializedName("entity")
    private AmmoEntityDisplay ammoEntity;
    @Nullable
    @SerializedName("shell")
    private ShellDisplay shellDisplay;
    @Nullable
    @SerializedName("particle")
    private AmmoParticle particle;
    @SerializedName("tracer_color")
    private String tracerColor = "0xFFFFFF";

    @Nullable
    public AmmoEntityDisplay getAmmoEntity() { return ammoEntity; }
    @Nullable
    public ShellDisplay getShellDisplay() { return shellDisplay; }
    @Nullable
    public AmmoParticle getParticle() { return particle; }
    public String getTracerColor() { return tracerColor; }

    @Override
    public void init() {
        if (ammoEntity != null && ammoEntity.modelTexture != null)
            ammoEntity.modelTexture = converter.idToFile(ammoEntity.modelTexture);
        if (shellDisplay != null && shellDisplay.modelTexture != null)
            shellDisplay.modelTexture = converter.idToFile(shellDisplay.modelTexture);
    }
}
