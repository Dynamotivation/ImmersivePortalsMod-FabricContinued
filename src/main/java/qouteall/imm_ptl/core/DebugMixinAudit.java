package qouteall.imm_ptl.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * DEVELOPMENT ONLY. Run with -Dip.mixinaudit=true.
 * Loads every class targeted by Immersive Portals' mixins, so that Mixin applies (and reports failures of)
 * all of them in one run instead of failing at the first broken one during startup.
 */
public class DebugMixinAudit {
    private static final Logger LOGGER = LoggerFactory.getLogger("IP-MixinAudit");

    private static final String[] CONFIGS = {
        "imm_ptl.mixins.json", "imm_ptl_compat.mixins.json", "imm_ptl_fabric.mixins.json",
        "imm_ptl_peripheral.mixins.json", "q_misc_util.mixins.json"
    };

    public static void runIfEnabled() {
        if (System.getProperty("ip.mixinaudit") == null) {
            return;
        }
        ClassLoader loader = DebugMixinAudit.class.getClassLoader();
        Set<String> targets = new LinkedHashSet<>();
        for (String config : CONFIGS) {
            try (InputStream in = loader.getResourceAsStream(config)) {
                if (in == null) continue;
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
                String pkg = root.get("package").getAsString();
                for (String key : new String[]{"mixins", "client", "server"}) {
                    if (!root.has(key)) continue;
                    JsonArray arr = root.getAsJsonArray(key);
                    for (JsonElement e : arr) {
                        collectTargets(loader, pkg, e.getAsString(), targets);
                    }
                }
            } catch (Throwable t) {
                LOGGER.error("Cannot read {}", config, t);
            }
        }
        LOGGER.info("Auditing {} mixin target classes", targets.size());
        int ok = 0, fail = 0, skipped = 0;
        for (String target : targets) {
            try {
                Class.forName(target, false, loader);
                ok++;
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                skipped++;
                LOGGER.info("skipped (not on this side/missing): {}", target);
            } catch (Throwable t) {
                fail++;
                LOGGER.error("TARGET FAILED: {} -> {}", target, rootMessage(t));
            }
        }
        LOGGER.info("Mixin audit done: ok={} failed={} skipped={}", ok, fail, skipped);
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    private static void collectTargets(ClassLoader loader, String pkg, String mixinName, Set<String> out) {
        String path = pkg.replace('.', '/') + "/" + mixinName.replace('.', '/') + ".class";
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                LOGGER.warn("mixin class file not found: {}", path);
                return;
            }
            ClassReader cr = new ClassReader(in);
            List<String> found = new ArrayList<>();
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(descriptor)) return null;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String n, Object value) {
                                    if (value instanceof Type type) {
                                        found.add(type.getClassName());
                                    } else if (value instanceof String s) {
                                        found.add(s.replace('/', '.'));
                                    }
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            out.addAll(found);
        } catch (Throwable t) {
            LOGGER.warn("cannot read mixin {}: {}", path, t.toString());
        }
    }
}
