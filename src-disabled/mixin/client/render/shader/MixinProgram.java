package qouteall.imm_ptl.core.mixin.client.render.shader;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.ShaderCodeTransformation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;

@Mixin(value = Program.class)
public class MixinProgram {
    // The redirect uses method arguments.

    private static final ThreadLocal<String> ip_programName = new ThreadLocal<>();
    
    @Inject(
        method = "compileShaderInternal",
        at = @At("HEAD")
    )
    private static void onBeginCompileShaderInternal(
        Program.Type type, String name, InputStream shaderData,
        String sourceName, GlslPreprocessor preprocessor, CallbackInfoReturnable<Integer> cir
    ) {
        Validate.isTrue(ip_programType.get() == null);
        Validate.isTrue(ip_programName.get() == null);
        ip_programType.set(type);
        ip_programName.set(name);
    }
    
    @Inject(
        method = "compileShaderInternal",
        at = @At("RETURN")
    )
    private static void onEndCompileShaderInternal(
        Program.Type type, String name, InputStream shaderData,
        String sourceName, GlslPreprocessor preprocessor, CallbackInfoReturnable<Integer> cir
    ) {
        Validate.isTrue(ip_programType.get() == type);
        Validate.isTrue(Objects.equals(ip_programName.get(), name));
        ip_programType.set(null);
        ip_programName.set(null);
    }
    
    @Redirect(
        method = "compileShaderInternal",
        at = @At(
            value = "INVOKE",
            target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/InputStream;Ljava/nio/charset/Charset;)Ljava/lang/String;",
            remap = false
        )
    )
    private static String redirectReadShaderSource(
        InputStream inputStream, Charset charset
    ) throws IOException {
        return null;
    }
}
