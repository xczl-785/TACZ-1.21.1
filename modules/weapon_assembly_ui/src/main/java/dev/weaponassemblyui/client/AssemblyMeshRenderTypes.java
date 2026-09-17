package dev.weaponassemblyui.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.HashMap;

/** Opaque workbench materials with depth, independent of world lighting. */
final class AssemblyMeshRenderTypes {
    static final RenderType SOLID=RenderType.create("assembly_mesh",
            DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.TRIANGLES,262144,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                .setCullState(RenderStateShard.NO_CULL)
                .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).createCompositeState(false));
    private AssemblyMeshRenderTypes() {}
    private static final Map<String,RenderType> TEXTURED=new HashMap<>();
    static RenderType forTexture(String texture) {
        if(texture.isEmpty())return SOLID;
        return TEXTURED.computeIfAbsent(texture,key->RenderType.create("assembly_material",
            DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.TRIANGLES,262144,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.parse(key),AssemblyTextureQuality.BLUR,AssemblyTextureQuality.MIPMAP))
                .setCullState(RenderStateShard.NO_CULL).setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).createCompositeState(false)));
    }
}
