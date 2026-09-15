package dev.weaponassemblyui.client;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.itemfoundation.client.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Same square image cell on the weapon and in the chooser. Empty removal cells contain no text. */
final class AttachmentCard extends Button {
    private final UiDesign design;
    private String name;
    private ResourceLocation image;
    private SpriteTexture icon;
    private boolean selected;
    AttachmentCard(UiDesign design, String name, ResourceLocation image, boolean enabled, Runnable click) {
        this.design = design;
        content(name, image);
        setText(Component.empty());noText();setActive(enabled);setOnClick(e -> click.run());
        buttonStyle(s -> s.baseTexture(IGuiTexture.EMPTY).hoverTexture(IGuiTexture.EMPTY).pressedTexture(IGuiTexture.EMPTY));
    }
    void content(String name, ResourceLocation image) {
        if (!java.util.Objects.equals(this.name, name)) {
            this.name = name;
            style(s -> s.tooltips(name.isEmpty() ? new Component[0] : new Component[]{Component.literal(name)}));
        }
        if (!java.util.Objects.equals(this.image, image)) {
            this.image = image;
            icon = image == null ? null : new SpriteTexture().setImageLocation(image);
        }
    }
    void selected(boolean value) { selected = value; }
    @Override public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        var g = context.graphics;
        float x=getPositionX(), y=getPositionY(), w=getSizeWidth(), h=getSizeHeight();
        boolean hover=isActive()&&isMouseOver(context.mouseX,context.mouseY);
        InventorySurfaceTexture.fill(g,x,y,x+w,y+h,hover?0xee26343a:0xe6101c22);
        InventorySurfaceTexture.border(g,x,y,x+w,y+h,design.px(selected?2:1),
                selected?InventoryUiTheme.WINDOW_SELECTED_BORDER:hover?0xffb7c2c5:0xff637178);
        if(icon!=null) {
            float size=Math.min(w,h)-design.px(8);
            icon.draw(g,0,0,x+(w-size)/2,y+(h-size)/2,size,size,0);
        }
        if(!name.isEmpty()) {
            var font=Minecraft.getInstance().font;
            float scale=design.px(12)/9;
            int available=Math.max(1,(int)((w-design.px(8))/scale));
            String first=font.plainSubstrByWidth(name,available);
            String rest=name.substring(first.length()).stripLeading();
            String second=font.plainSubstrByWidth(rest,available);
            if(!second.equals(rest))second=font.plainSubstrByWidth(rest,Math.max(1,available-font.width("…")))+"…";
            g.pose().pushPose();
            try {
                g.pose().translate(x+design.px(4),y+design.px(4),0);g.pose().scale(scale,scale,1);
                g.drawString(font,first,0,0,UiDesign.TEXT,false);
                if(!second.isEmpty())g.drawString(font,second,0,10,UiDesign.TEXT,false);
            }
            finally {g.pose().popPose();}
        }
        if(!isActive())InventorySurfaceTexture.fill(g,x,y,x+w,y+h,0x66000000);
    }
}
