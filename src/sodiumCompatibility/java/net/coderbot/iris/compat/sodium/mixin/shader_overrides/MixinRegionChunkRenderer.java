package net.coderbot.iris.compat.sodium.mixin.shader_overrides;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.caffeinemc.gfx.api.array.VertexArrayDescription;
import net.caffeinemc.gfx.api.array.VertexArrayResourceBinding;
import net.caffeinemc.gfx.api.array.attribute.VertexAttributeBinding;
import net.caffeinemc.gfx.api.array.attribute.VertexFormat;
import net.caffeinemc.gfx.api.device.RenderDevice;
import net.caffeinemc.gfx.api.device.commands.RenderCommandList;
import net.caffeinemc.gfx.api.pipeline.Pipeline;
import net.caffeinemc.gfx.api.pipeline.PipelineState;
import net.caffeinemc.gfx.api.shader.Program;
import net.caffeinemc.gfx.api.shader.ShaderDescription;
import net.caffeinemc.gfx.api.shader.ShaderType;
import net.caffeinemc.sodium.render.chunk.draw.ChunkPrep;
import net.caffeinemc.sodium.render.chunk.draw.ChunkRenderMatrices;
import net.caffeinemc.sodium.render.chunk.draw.DefaultChunkRenderer;
import net.caffeinemc.sodium.render.chunk.draw.ShaderChunkRenderer;
import net.caffeinemc.sodium.render.chunk.passes.ChunkRenderPass;
import net.caffeinemc.sodium.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.sodium.render.shader.ShaderConstants;
import net.caffeinemc.sodium.render.shader.ShaderLoader;
import net.caffeinemc.sodium.render.shader.ShaderParser;
import net.caffeinemc.sodium.render.terrain.format.TerrainMeshAttribute;
import net.caffeinemc.sodium.render.terrain.format.TerrainVertexType;
import net.coderbot.iris.Iris;
import net.coderbot.iris.compat.sodium.impl.IrisChunkShaderBindingPoints;
import net.coderbot.iris.compat.sodium.impl.shader_overrides.GlObjectExt;
import net.coderbot.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides;
import net.coderbot.iris.compat.sodium.impl.shader_overrides.IrisChunkShaderInterface;
import net.coderbot.iris.compat.sodium.impl.shader_overrides.ShaderChunkRendererExt;
import net.coderbot.iris.compat.sodium.impl.vertex_format.IrisChunkMeshAttributes;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.coderbot.iris.texunits.TextureUnit;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(DefaultChunkRenderer.class)
public abstract class MixinRegionChunkRenderer extends ShaderChunkRenderer<ChunkShaderInterface> implements ShaderChunkRendererExt<ChunkShaderInterface> {
	@Shadow
	private static ShaderConstants getShaderConstants(ChunkRenderPass pass, TerrainVertexType vertexType) {
		return null;
	}

	public MixinRegionChunkRenderer(RenderDevice device, TerrainVertexType vertexType) {
		super(device, vertexType);
	}

	@Inject(method = "lambda$render$0", at = @At("HEAD"))
	private void setup(ChunkRenderPass pass, ChunkRenderMatrices matrices, ChunkPrep.PreparedRenderList list, RenderCommandList commandList, ChunkShaderInterface chunkShaderInterface, PipelineState state, CallbackInfo ci) {
		if (chunkShaderInterface instanceof IrisChunkShaderInterface programInterface2) {
			programInterface2.setup();
		}
	}

	@ModifyConstant(method="setupTextures", constant=@Constant(intValue=1))
	private int redirectLightMap(int constant) {
		if (IrisApi.getInstance().isShaderPackInUse()) {
			return TextureUnit.LIGHTMAP.getSamplerId();
		}
		return constant;
	}

	@Inject(method = "lambda$render$0", at = @At("TAIL"))
	private void end(ChunkRenderPass pass, ChunkRenderMatrices matrices, ChunkPrep.PreparedRenderList list, RenderCommandList commandList, ChunkShaderInterface chunkShaderInterface, PipelineState state, CallbackInfo ci) {
		if (chunkShaderInterface instanceof IrisChunkShaderInterface programInterface2) {
			programInterface2.restore();
		}
	}

	/**
	 * @author IMS
	 */
	@Override
	public Program<ChunkShaderInterface> createProgram(ChunkRenderPass pass) {
		Program<ChunkShaderInterface> program = this.getIrisProgram(false, pass);
		if (program != null) {
			return program;
		} else {
			ShaderConstants constants = getShaderConstants(pass, this.vertexType);
			String vertShader = ShaderParser.parseShader(ShaderLoader.MINECRAFT_ASSETS, new ResourceLocation("sodium", "blocks/block_layer_opaque.vsh"), constants);
			String fragShader = ShaderParser.parseShader(ShaderLoader.MINECRAFT_ASSETS, new ResourceLocation("sodium", "blocks/block_layer_opaque.fsh"), constants);
			ShaderDescription desc = ShaderDescription.builder().addShaderSource(ShaderType.VERTEX, vertShader).addShaderSource(ShaderType.FRAGMENT, fragShader).addAttributeBinding("a_Position", 1).addAttributeBinding("a_Color", 2).addAttributeBinding("a_TexCoord", 3).addAttributeBinding("a_LightCoord", 4).addFragmentBinding("fragColor", 0).build();
			return this.device.createProgram(desc, ChunkShaderInterface::new);
		}
	}
}
