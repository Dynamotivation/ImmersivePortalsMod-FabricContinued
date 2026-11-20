/*
 * Adapted from the Immersive Portals port for Minecraft 26.3 (other-ports/ImmersivePortalsMod,
 * compat/iris_portal_view/mixin/MixinIrisShaderCreator.java), Copyright 2020 qouteall and contributors,
 * licensed under the Apache License, Version 2.0.
 * Changes for Iris 1.11.4: TransformPatcher.patchSodium/patchVanilla have no Set<?> textureOverrides parameter;
 * require = 0; sky programs are not clipped (like the sky pass without shaders, drawn with the plain projection).
 */
package qouteall.imm_ptl.core.compat.mixin.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.pipeline.programs.ShaderCreator;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.compat.iris_portal_view.IrisPortalClipping;

import java.util.Map;

/**
 * G-buffer programs of a shader pack clip portal views (see {@link IrisPortalClipping}).
 * {@code ShaderCreator.create} builds the G-buffer programs of both paths (vanilla and Sodium terrain); its
 * isShadowPass argument (third boolean) excludes shadow programs, so the destination still casts shadows from
 * behind the portal plane. {@code ShaderCreator.createShadow} is not touched. Optional, see MixinIrisPipelineManager.
 */
@Mixin(value = ShaderCreator.class, remap = false)
public class MixinIrisShaderCreator {
    @Unique
    private static boolean ip_shouldClip(ShaderKey shaderKey, boolean isShadowPass) {
        return !isShadowPass && (shaderKey == null || !shaderKey.name().startsWith("SKY"));
    }

    @WrapOperation(
        method = "create",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;Z)Ljava/util/Map;"
        )
    )
    private static Map<PatchShaderType, String> ip_clipSodium(
        String name, String vertex, String geometry, String tessControl, String tessEval, String fragment,
        AlphaTest alpha, Object2ObjectMap<?, ?> textureMap, boolean shadow,
        Operation<Map<PatchShaderType, String>> original,
        @Local(argsOnly = true) ShaderKey shaderKey,
        @Local(argsOnly = true, ordinal = 2) boolean isShadowPass
    ) {
        Map<PatchShaderType, String> result = original.call(
            name, vertex, geometry, tessControl, tessEval, fragment, alpha, textureMap, shadow
        );
        IrisPortalClipping.onShaderCreatorHooked();
        return shadow || !ip_shouldClip(shaderKey, isShadowPass) ? result : IrisPortalClipping.patch(name, result);
    }

    @WrapOperation(
        method = "create",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchVanilla(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;ZZZLnet/irisshaders/iris/gl/state/ShaderAttributeInputs;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"
        )
    )
    private static Map<PatchShaderType, String> ip_clipVanilla(
        String name, String vertex, String geometry, String tessControl, String tessEval, String fragment,
        AlphaTest alpha, boolean isLines, boolean isClouds, boolean hasChunkOffset, ShaderAttributeInputs inputs,
        Object2ObjectMap<?, ?> textureMap,
        Operation<Map<PatchShaderType, String>> original,
        @Local(argsOnly = true) ShaderKey shaderKey,
        @Local(argsOnly = true, ordinal = 2) boolean isShadowPass
    ) {
        Map<PatchShaderType, String> result = original.call(
            name, vertex, geometry, tessControl, tessEval, fragment, alpha, isLines, isClouds, hasChunkOffset, inputs,
            textureMap
        );
        IrisPortalClipping.onShaderCreatorHooked();
        return !ip_shouldClip(shaderKey, isShadowPass) ? result : IrisPortalClipping.patch(name, result);
    }
}
