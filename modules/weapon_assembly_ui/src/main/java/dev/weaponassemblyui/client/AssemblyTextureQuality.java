package dev.weaponassemblyui.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.Map;

/** Shared sampling policy for large interactive assembly surfaces and cached assembly images. */
public final class AssemblyTextureQuality {
    public static final boolean BLUR=false;
    public static final boolean MIPMAP=true;
    private static final Map<ResourceLocation,Integer> PREPARED_IDS=new HashMap<>();

    public static int mipmapLevels(int width,int height) {
        if(width<1||height<1)throw new IllegalArgumentException("Texture dimensions must be positive");
        return 31-Integer.numberOfLeadingZeros(Math.min(width,height));
    }

    /** Simple textures only upload level zero. Generate the remaining levels once per GL texture id. */
    public static void prepare(ResourceLocation location) {
        RenderSystem.assertOnRenderThreadOrInit();
        var texture=Minecraft.getInstance().getTextureManager().getTexture(location);
        int id=texture.getId();
        if(PREPARED_IDS.getOrDefault(location,-1)==id)return;
        texture.bind();
        int width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH);
        int height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
        if(width<1||height<1)return;
        int levels=mipmapLevels(width,height);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL12Compat.GL_TEXTURE_BASE_LEVEL,0);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D,GL12Compat.GL_TEXTURE_MAX_LEVEL,levels);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        texture.setFilter(BLUR,MIPMAP);
        PREPARED_IDS.put(location,id);
    }

    /** Constants live here to avoid depending on an additional LWJGL compatibility class. */
    private static final class GL12Compat {
        static final int GL_TEXTURE_BASE_LEVEL=0x813C;
        static final int GL_TEXTURE_MAX_LEVEL=0x813D;
    }
    private AssemblyTextureQuality(){}
}
