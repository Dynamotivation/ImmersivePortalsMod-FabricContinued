package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEGameRendererPortal;
import qouteall.imm_ptl.core.render.portalview.PortalViewRenderer;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer_Portal implements IEGameRendererPortal {
    @Shadow
    @Final
    private FogRenderer fogRenderer;
    
    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;
    
    @Shadow
    @Final
    private Minecraft minecraft;
    
    @Shadow
    @Final
    private Lightmap lightmap;
    
    @Shadow
    @Final
    private LightmapRenderStateExtractor lightmapRenderStateExtractor;
    
    @Shadow
    public abstract net.minecraft.client.renderer.state.GameRenderState gameRenderState();
    
    @Shadow
    @Final
    private net.minecraft.client.renderer.GlobalSettingsUniform globalSettingsUniform;
    
    @Unique
    private final ProjectionMatrixBuffer[] ip_portalProjectionBuffers = new ProjectionMatrixBuffer[2];
    
    @Shadow
    @Final
    private net.minecraft.client.Camera mainCamera;

    @Shadow
    private float spinningEffectTime;
    @Shadow
    private float spinningEffectSpeed;

    @Shadow
    private void bobHurt(
        net.minecraft.client.renderer.state.level.CameraRenderState cameraState, com.mojang.blaze3d.vertex.PoseStack poseStack
    ) {
    }

    @Shadow
    private void bobView(
        net.minecraft.client.renderer.state.level.CameraRenderState cameraState, com.mojang.blaze3d.vertex.PoseStack poseStack
    ) {
    }

    @Override
    public Matrix4f ip_applyScreenEffects(
        Matrix4f projection, net.minecraft.client.renderer.state.level.CameraRenderState cameraState, float worldPartialTicks
    ) {
        // same as the start of GameRenderer.renderLevel
        net.minecraft.client.renderer.state.OptionsRenderState options = gameRenderState().optionsRenderState;
        Matrix4f result = new Matrix4f(projection);

        com.mojang.blaze3d.vertex.PoseStack bobStack = new com.mojang.blaze3d.vertex.PoseStack();
        bobHurt(cameraState, bobStack);
        if (options.bobView) {
            bobView(cameraState, bobStack);
        }
        result.mul(bobStack.last().pose());

        net.minecraft.client.player.LocalPlayer player = minecraft.player;
        if (player != null) {
            float screenEffectScale = options.screenEffectScale;
            float portalIntensity = net.minecraft.util.Mth.lerp(
                worldPartialTicks, player.oPortalEffectIntensity, player.portalEffectIntensity
            );
            float nauseaIntensity = player.getEffectBlendFactor(
                net.minecraft.world.effect.MobEffects.NAUSEA, worldPartialTicks
            );
            float spinningEffectIntensity = Math.max(portalIntensity, nauseaIntensity) * (screenEffectScale * screenEffectScale);
            if (spinningEffectIntensity > 0.0F) {
                float skew = 5.0F / (spinningEffectIntensity * spinningEffectIntensity + 5.0F) - spinningEffectIntensity * 0.04F;
                skew *= skew;
                org.joml.Vector3f axis = new org.joml.Vector3f(0.0F, net.minecraft.util.Mth.SQRT_OF_TWO / 2.0F, net.minecraft.util.Mth.SQRT_OF_TWO / 2.0F);
                float angle = (this.spinningEffectTime + worldPartialTicks * this.spinningEffectSpeed) * (float) (Math.PI / 180.0);
                result.rotate(angle, axis);
                result.scale(1.0F / skew, 1.0F, 1.0F);
                result.rotate(-angle, axis);
            }
        }
        return result;
    }

    // Teleportation must be checked every FRAME, before the camera is updated for the frame. Checking only once per
    // tick lets the interpolated camera sit past the portal plane (still in the old dimension) for several frames,
    // which shows as a culled window and a wobble when standing on the portal plane.
    @Inject(method = "update", at = @At("HEAD"))
    private void ip_teleportBeforeCameraUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (minecraft.isGameLoadFinished() && minecraft.level != null && minecraft.player != null) {
            qouteall.imm_ptl.core.render.context_management.RenderStates.setPartialTick(
                mainCamera.getCameraEntityPartialTicks(deltaTracker)
            );
            qouteall.imm_ptl.core.teleportation.ClientTeleportationManager.manageTeleportation(false);
        }
    }

    // the destinations of portals are rendered before the main level is extracted,
    // as every extraction overwrites the shared level render state
    @Inject(method = "extract", at = @At("HEAD"))
    private void ip_onBeforeExtract(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (minecraft.isGameLoadFinished() && advanceGameTime && minecraft.level != null && minecraft.player != null) {
            // portal wand wireframes (vanilla gizmos, drawn with the level)
            qouteall.imm_ptl.peripheral.wand.PortalWandItem.renderMarkingGizmos(mainCamera);
        }
        if (minecraft.isGameLoadFinished() && advanceGameTime && minecraft.level != null) {
            PortalViewRenderer.onBeforeExtract((GameRenderer) (Object) this, deltaTracker);
        }
    }

    // Around the main LevelRenderer.render (anchored on the profiler sections, not on the render call, which Iris wraps
    // with a WrapOperation): with a shader pack Iris' final pass overwrites the main target at the end of
    // LevelRenderer.render, so the portal panes are drawn after it. require = 0: if missing, the panes stay in the
    // level's frame graph (PortalViewRenderer.shouldAddViewPass).
    @Inject(
        method = "renderLevel",
        at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=level"),
        require = 0
    )
    private void ip_beforeMainLevelRender(DeltaTracker deltaTracker, CallbackInfo ci) {
        PortalViewRenderer.onBeforeMainLevelRender();
    }

    @Inject(
        method = "renderLevel",
        at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=hand"),
        require = 0
    )
    private void ip_afterMainLevelRender(DeltaTracker deltaTracker, CallbackInfo ci) {
        PortalViewRenderer.onAfterMainLevelRender((GameRenderer) (Object) this);
    }

    // make the level renderer draw into the offscreen target when rendering a portal destination
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void ip_overrideMainRenderTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget override = PortalViewRenderer.getActiveTargetOverride();
        RenderTarget target = override != null ? override : mainRenderTarget;
        // SkyRenderer draws into the RenderTarget it captured when it was created (not the frame graph's target).
        // LevelRenderer.render asks for mainRenderTarget() first, so keep the current renderer's sky on that target.
        qouteall.imm_ptl.core.render.SkyRendererTargets.sync(minecraft.levelRenderer, target);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    @Shadow
    @Final
    private RenderTarget mainRenderTarget;

    @Override
    public void ip_onPlayerChangedDimension(net.minecraft.client.multiplayer.ClientLevel toWorld, net.minecraft.world.phys.Vec3 eyePos) {
        // The camera's environment probe (fog/sky/cloud colour, sun angle, lightmap factors) still holds the OLD level and
        // position and is only re-ticked in the next client tick: the first frame(s) in the new dimension were rendered with
        // the old dimension's values (Nether -> Overworld: SKY_COLOR 0 = black sky, dark red fog ending at 96, sky light 0).
        net.minecraft.world.attribute.EnvironmentAttributeProbe probe = mainCamera.attributeProbe();
        probe.reset();
        probe.tick(toWorld, eyePos);
        // the main lightmap is only re-extracted once per tick as well
        ((IELightmapRenderStateExtractor) lightmapRenderStateExtractor).ip_setNeedsUpdate(true);
    }
    
    @Override
    public FogRenderer ip_getFogRenderer() {
        return fogRenderer;
    }
    
    @Override
    public GraphicsResourceAllocator ip_getResourcePool() {
        return resourcePool;
    }
    
    @Override
    public GpuBufferSlice ip_getPortalProjectionBuffer(Matrix4f projectionMatrix, int slot) {
        if (ip_portalProjectionBuffers[slot] == null) {
            ip_portalProjectionBuffers[slot] = new ProjectionMatrixBuffer("immersive portals view " + slot);
        }
        return ip_portalProjectionBuffers[slot].getBuffer(projectionMatrix);
    }

    @Override
    public void ip_renderLightmap(Lightmap destLightmap, net.minecraft.client.renderer.state.LightmapRenderState state) {
        IELightmapRenderStateExtractor access = (IELightmapRenderStateExtractor) lightmapRenderStateExtractor;
        access.ip_setNeedsUpdate(true);
        lightmapRenderStateExtractor.extract(state, 1.0F);
        destLightmap.render(state);
        // the extraction consumed the flag, the main view must still update its own lightmap this frame
        access.ip_setNeedsUpdate(true);
    }
    
    // sample the lightmap of the dimension that is being rendered into a portal view
    @Inject(method = "lightmap", at = @At("HEAD"), cancellable = true)
    private void ip_overrideLightmap(CallbackInfoReturnable<com.mojang.blaze3d.textures.GpuTextureView> cir) {
        com.mojang.blaze3d.textures.GpuTextureView override = PortalViewRenderer.getActiveLightmapOverride();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
    
    @Override
    public void ip_updateGlobalSettingsForView(DeltaTracker deltaTracker, net.minecraft.world.phys.Vec3 cameraPos) {
        net.minecraft.client.renderer.state.GameRenderState state = gameRenderState();
        globalSettingsUniform.update(
            state.windowRenderState.width, state.windowRenderState.height,
            state.optionsRenderState.glintStrength,
            minecraft.level == null ? 0L : minecraft.level.getGameTime(),
            deltaTracker,
            state.optionsRenderState.menuBackgroundBlurriness,
            cameraPos,
            state.optionsRenderState.textureFiltering == net.minecraft.client.TextureFilteringMethod.RGSS
        );
    }
}
