package net.coderbot.iris.colorspace;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.coderbot.iris.gl.texture.InternalTextureFormat;
import net.coderbot.iris.postprocess.FullScreenQuadRenderer;
import net.coderbot.iris.shaderpack.preprocessor.JcppProcessor;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

public class ColorSpaceConverter {
	private ColorSpace colorSpace;
	private int target;
	private int width;
	private int height;
	private Program fallbackProgram;
	private GlFramebuffer render;
	private int swap;
	private GlFramebuffer swapFb;
	private ComputeProgram program;
	private boolean fragment;

	public ColorSpaceConverter(int mainRenderTarget, ColorSpace currentColorSpace, int width, int height) {
		this.target = mainRenderTarget;
		this.colorSpace = currentColorSpace;

		this.width = width;
		this.height = height;
		recreateShader(colorSpace);
	}

	public void changeMainRenderTarget(int mainRenderTarget, int width, int height) {
		this.target = mainRenderTarget;
		if (swapFb != null) {
			swapFb.addColorAttachment(0, target);
		}
		this.width = width;
		this.height = height;
	}

	public void changeCurrentColorSpace(ColorSpace space) {
		colorSpace = space;
		recreateShader(colorSpace);
	}

	public void recreateShader(ColorSpace colorSpace) {
		if (IrisRenderSystem.supportsCompute()) {
			recreateShaderCompute(colorSpace);
		} else {
			recreateShaderFragment(colorSpace);
		}
	}

	private void recreateShaderFragment(ColorSpace colorSpace) {
		try {
			String source = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/Iris_ColourManagement.csh"))), StandardCharsets.UTF_8);

			fragment = true;
			source = source.replace("PLACEHOLDER", colorSpace.name().toUpperCase(Locale.US));
			source = source.replace("430 core", "330 core");
			source = source.replace("PLACEHOLDER2", "#define FRAGMENT");
			source = JcppProcessor.glslPreprocessSource(source, Collections.EMPTY_LIST);
			Iris.logger.warn(source);

			String vertex = """
				#version 150 core

				in vec3 Position;
				in vec2 UV0;

				out vec2 texCoord;

				void main() {
					gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
					texCoord = UV0;
				]
			""";
			ProgramBuilder builder = ProgramBuilder.begin("colorSpace", vertex, null, source, ImmutableSet.of());
			builder.addDefaultSampler(() -> target, "mainImage");

			this.fallbackProgram = builder.build();
			if (swap != 0) {
				GlStateManager._deleteTexture(swap);
			}

			swap = GlStateManager._genTexture();
			IrisRenderSystem.texImage2D(swap, GL30C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA, width, height, 0, GL30C.GL_RGBA, GL30C.GL_UNSIGNED_BYTE, null);

			if (render != null) {
				render.destroy();
				render = null;
			}
			render = new GlFramebuffer();
			render.addColorAttachment(0, swap);

			if (swapFb != null) {
				swapFb.destroy();
				swapFb = null;
			}
			swapFb = new GlFramebuffer();
			swapFb.addColorAttachment(0, target);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void recreateShaderCompute(ColorSpace colorSpace) {
		try {
			fragment = false;
			String source = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/Iris_ColourManagement.csh"))), StandardCharsets.UTF_8);

			source = source.replace("PLACEHOLDER", colorSpace.name().toUpperCase(Locale.US));
			source = source.replace("PLACEHOLDER2", "");
			source = JcppProcessor.glslPreprocessSource(source, Collections.EMPTY_LIST);
			Iris.logger.warn(source);

			ProgramBuilder builder = ProgramBuilder.beginCompute("colorSpace", source, ImmutableSet.of());
			builder.addTextureImage(() -> target, InternalTextureFormat.RGBA8, "mainImage");

			this.program = builder.buildCompute();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void processColorSpace() {
		if (colorSpace == ColorSpace.SRGB) {
			// Packs output in SRGB by default.
			return;
		}

		if (fragment) {
			fallbackProgram.use();
			render.bind();
			FullScreenQuadRenderer.INSTANCE.render();
			RenderSystem.bindTexture(target);
			GlStateManager._glCopyTexSubImage2D(GL20C.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
		} else {
			program.use();
			IrisRenderSystem.dispatchCompute(width / 8, height / 8, 1);
			IrisRenderSystem.memoryBarrier(GL43C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL43C.GL_TEXTURE_FETCH_BARRIER_BIT);
			ComputeProgram.unbind();
		}
	}
}
