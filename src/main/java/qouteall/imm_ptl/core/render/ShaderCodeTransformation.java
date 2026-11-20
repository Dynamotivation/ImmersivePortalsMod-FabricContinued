package qouteall.imm_ptl.core.render;

import com.mojang.logging.LogUtils;
import me.shedaniel.cloth.clothconfig.shadowed.org.yaml.snakeyaml.Yaml;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.Helper;

import java.util.List;
import java.util.Set;

public class ShaderCodeTransformation {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static enum ShaderType {
        vs, fs
    }
    
    private static boolean matches(ShaderType me, ShaderType type) {
        return false;
    }
    
    // snakeyaml does not allow passing generic type
    // so use another wrapper type to make list generic type work
    public static class ConfigsObj {
        public List<Config> configs;
    }
    
    public static class TransformationEntry {
        public String comment;
        public String pattern;
        public String replacement;
    }
    
    public static class Config {
        public String comment;
        public ShaderType type;
        public Set<String> affectedShaders;
        public List<TransformationEntry> transformations;
        public boolean debugOutput;
    }
    
    private static List<Config> configs;
    
    public static void init() {
        if (IPGlobal.enableClippingMechanism) {
            Yaml yaml = new Yaml();
            
            String yamlStr = McHelper.readTextResource(McHelper.newResourceLocation(
                "immersive_portals:shaders/shader_transformation.yaml"
            ));
            ConfigsObj configsObj = yaml.loadAs(yamlStr, ConfigsObj.class);
            
            configs = configsObj.configs;
            
            LOGGER.info("Loaded Shader Code Transformation");
        }
        else {
            LOGGER.info("Shader Transformation Disabled");
        }
    }
    
    public static String transform(ShaderType type, String shaderId, String inputCode) {
        return null;
    }
    
    @Nullable
    private static Config getConfig(ShaderType type, String shaderId) {
        return null;
    }
    
    public static boolean shouldAddUniform(String shaderName) {
        if (configs == null) {
            LOGGER.info("Shader Transform Skipping {} in shouldAddUniform", shaderName);
            return false;
        }
        
        return configs.stream().anyMatch(config -> config.affectedShaders.contains(shaderName));
    }
}
