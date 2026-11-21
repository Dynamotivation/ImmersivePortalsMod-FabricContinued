package qouteall.imm_ptl.core.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Plugin of the optional compat mixin config (imm_ptl_compat.mixins.json, "required": false).
 * <p>
 * Iris mixins (package {@code compat.mixin.iris}) are only applied when the iris mod is loaded, its version is in
 * {@link #IRIS_SUPPORTED}, and the exact members they target exist in the (untransformed) target class. Without
 * Iris nothing Iris-related is applied, so no Iris class is ever loaded by a mixin. A missing member disables only
 * that mixin; PortalViewRenderer then falls back to the placeholder for shader-pack portals (it checks at runtime
 * that the mixins work, see IrisInterface.getPortalViewUnsupportedReason).
 */
public class IPCompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("IPCompatMixinPlugin");

    private static final String IRIS_MIXIN_PACKAGE = "qouteall.imm_ptl.core.compat.mixin.iris.";
    private static final String IRIS_SUPPORTED = ">=1.11.0 <1.12.0";

    private static final String WORLD_RENDERING_PIPELINE = "Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;";
    private static final String TRANSFORM_PATCHER = "net/irisshaders/iris/pipeline/transform/TransformPatcher";
    private static final String SHADER_CREATOR_CREATE_DESC =
        "(Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;Ljava/lang/String;Lnet/irisshaders/iris/pipeline/programs/ShaderKey;"
            + "Lnet/irisshaders/iris/shaderpack/programs/ProgramSource;Lnet/irisshaders/iris/shaderpack/loading/ProgramId;"
            + "Lnet/irisshaders/iris/gl/framebuffer/GlFramebuffer;Lnet/irisshaders/iris/gl/framebuffer/GlFramebuffer;"
            + "Lnet/irisshaders/iris/gl/blending/AlphaTest;Lcom/mojang/blaze3d/vertex/VertexFormat;"
            + "Lnet/irisshaders/iris/gl/state/ShaderAttributeInputs;Lnet/irisshaders/iris/uniforms/FrameUpdateNotifier;"
            + "Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;Ljava/util/function/Supplier;Lnet/irisshaders/iris/gl/state/FogMode;"
            + "ZZZZLnet/irisshaders/iris/uniforms/custom/CustomUniforms;Lnet/irisshaders/iris/pipeline/transform/Patch;)"
            + "Lnet/irisshaders/iris/pipeline/programs/ShaderSupplier;";
    private static final String PATCH_SODIUM_DESC =
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
            + "Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;Z)Ljava/util/Map;";
    private static final String PATCH_VANILLA_DESC =
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
            + "Lnet/irisshaders/iris/gl/blending/AlphaTest;ZZZLnet/irisshaders/iris/gl/state/ShaderAttributeInputs;"
            + "Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;";

    private static Boolean irisSupported = null;

    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {


        FabricLoader fabricLoader = FabricLoader.getInstance();
        if (mixinClassName.startsWith(IRIS_MIXIN_PACKAGE)) {
            return shouldApplyIrisMixin(targetClassName, mixinClassName);
        }

        // optional integrations from the 26.3 fork by Kurokekkon
        if (mixinClassName.contains("VoiceChat")) {
            return fabricLoader.isModLoaded("voicechat");
        }
        if (mixinClassName.contains("PolymerCore")) {
            return fabricLoader.isModLoaded("polymer-core");
        }
        if (mixinClassName.contains("Polymer")) {
            return fabricLoader.isModLoaded("polymer-virtual-entity");
        }

        if (mixinClassName.contains("IrisSodium")) {
            boolean sodiumLoaded = fabricLoader.isModLoaded("sodium");
            boolean irisLoaded = fabricLoader.isModLoaded("iris");
            return sodiumLoaded && irisLoaded;
        }

        if (mixinClassName.contains("Iris")) {
            boolean irisLoaded = fabricLoader.isModLoaded("iris");
            return irisLoaded;
        }

        if (mixinClassName.contains("Sodium")) {
            boolean sodiumLoaded = fabricLoader.isModLoaded("sodium");
            return sodiumLoaded;
        }

        if (mixinClassName.contains("Flywheel")) {
            boolean flywheelLoaded = fabricLoader.isModLoaded("flywheel");
            return flywheelLoaded;
        }

        if (mixinClassName.contains("CardinalComp")) {
            boolean cardinalCompLoaded = fabricLoader.isModLoaded("cardinal-components-base");
            return cardinalCompLoaded;
        }

        return false;
    }

    private static boolean isIrisSupported() {
        if (irisSupported == null) {
            Optional<ModContainer> iris = FabricLoader.getInstance().getModContainer("iris");
            if (iris.isEmpty()) {
                irisSupported = false;
            }
            else {
                try {
                    irisSupported = VersionPredicate.parse(IRIS_SUPPORTED).test(iris.get().getMetadata().getVersion());
                }
                catch (Throwable e) {
                    LOGGER.warn("Cannot check the Iris version", e);
                    irisSupported = false;
                }
                if (!irisSupported) {
                    LOGGER.warn(
                        "Iris {} is not in the supported range {}: portal views with shader packs are disabled",
                        iris.get().getMetadata().getVersion().getFriendlyString(), IRIS_SUPPORTED
                    );
                }
            }
        }
        return irisSupported;
    }

    private static boolean shouldApplyIrisMixin(String targetClassName, String mixinClassName) {
        if (!isIrisSupported()) {
            return false;
        }
        String simpleName = mixinClassName.substring(IRIS_MIXIN_PACKAGE.length());
        boolean ok;
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            ok = switch (simpleName) {
                case "MixinIrisPipelineManager" -> hasField(node, "pipeline", WORLD_RENDERING_PIPELINE)
                    && hasMethod(node, "preparePipeline",
                    "(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)" + WORLD_RENDERING_PIPELINE)
                    && hasMethod(node, "destroyPipeline", "()V");
                case "MixinIrisCommonUniforms" -> hasMethod(node, "addDynamicUniforms",
                    "(Lnet/irisshaders/iris/gl/uniform/DynamicUniformHolder;Lnet/irisshaders/iris/gl/state/FogMode;)V");
                case "MixinIrisShaderCreator" -> invokes(node, "create", SHADER_CREATOR_CREATE_DESC,
                    TRANSFORM_PATCHER, "patchSodium", PATCH_SODIUM_DESC)
                    && invokes(node, "create", SHADER_CREATOR_CREATE_DESC,
                    TRANSFORM_PATCHER, "patchVanilla", PATCH_VANILLA_DESC);
                case "MixinIrisHandRenderer" -> hasMethod(node, "canRender",
                    "(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;)Z");
                default -> false;
            };
        }
        catch (Throwable e) {
            LOGGER.warn("Cannot inspect {} for {}", targetClassName, mixinClassName, e);
            ok = false;
        }
        if (!ok) {
            LOGGER.warn("Not applying {}: its target members in {} do not match this Iris version", mixinClassName, targetClassName);
        }
        return ok;
    }

    private static boolean hasField(ClassNode node, String name, String desc) {
        for (FieldNode field : node.fields) {
            if (field.name.equals(name) && field.desc.equals(desc)) {
                return true;
            }
        }
        return false;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    private static boolean hasMethod(ClassNode node, String name, String desc) {
        return findMethod(node, name, desc) != null;
    }

    private static boolean invokes(
        ClassNode node, String name, String desc, String owner, String invokedName, String invokedDesc
    ) {
        MethodNode method = findMethod(node, name, desc);
        if (method == null) {
            return false;
        }
        int count = 0;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(invokedName) && call.desc.equals(invokedDesc)) {
                count++;
            }
        }
        return count == 1;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
