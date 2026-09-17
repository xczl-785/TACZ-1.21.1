package dev.weaponassemblyui.client;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.itemfoundation.client.api.InventorySurfaceTexture;

/** Quiet graphite inspection bay: enough depth to separate the gun without competing with it. */
final class WorkbenchBackdrop extends UIElement {
    WorkbenchBackdrop(){setId("assembly-backdrop");setAllowHitTest(false);}
    @Override public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        var g=context.graphics;
        int x=(int)getPositionX(),y=(int)getPositionY();
        int right=(int)Math.ceil(getPositionX()+getSizeWidth()),bottom=(int)Math.ceil(getPositionY()+getSizeHeight());
        g.fillGradient(x,y,right,bottom,0xff182127,0xff080c0f);
        int bandTop=y+(bottom-y)*18/100,bandBottom=y+(bottom-y)*72/100;
        g.fillGradient(x,bandTop,right,bandBottom,0x101f3038,0x06202b31);
        int step=Math.max(28,(right-x)/20);
        for(int gx=x+step;gx<right;gx+=step)InventorySurfaceTexture.fill(g,gx,y,gx+1,bottom,0x0b9bb0b8);
        for(int gy=y+step;gy<bottom;gy+=step)InventorySurfaceTexture.fill(g,x,gy,right,gy+1,0x087f969f);
        InventorySurfaceTexture.fill(g,x,bandTop,right,bandTop+1,0x247f98a2);
        InventorySurfaceTexture.fill(g,x,bandBottom,right,bandBottom+1,0x14627379);
        g.fillGradient(x,y,right,y+Math.max(40,(bottom-y)/7),0xaa05080a,0x0005080a);
        g.fillGradient(x,bottom-Math.max(70,(bottom-y)/5),right,bottom,0x0005080a,0xcc050709);
    }
}
