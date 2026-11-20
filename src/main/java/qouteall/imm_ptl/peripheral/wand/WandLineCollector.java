package qouteall.imm_ptl.peripheral.wand;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 26.x removed the immediate-mode line buffers the portal wand used for its wireframes. The wand rendering code still
 * writes line vertices into {@link VertexConsumer}s (camera-relative, through a PoseStack), so this collects them and
 * turns them into vanilla {@link Gizmos} lines (world space), which the level renderer draws with its own pipelines.
 * Must be used inside a {@code Gizmos.withCollector(...)} scope.
 */
@Environment(EnvType.CLIENT)
public class WandLineCollector {
    private static final float LINE_WIDTH = 3.0f;

    private final Vec3 cameraPos;
    private final LineConsumer lines = new LineConsumer(false);
    private final LineConsumer lineStrip = new LineConsumer(true);

    public WandLineCollector(Vec3 cameraPos) {
        this.cameraPos = cameraPos;
    }

    /** vertices are pairs: start, end */
    public VertexConsumer lines() {
        return lines;
    }

    /** consecutive vertices are connected, a vertex with alpha 0 jumps (no line is drawn to it) */
    public VertexConsumer lineStrip() {
        return lineStrip;
    }

    /** emit the collected lines as gizmos */
    public void flush() {
        lines.flush();
        lineStrip.flush();
    }

    private class LineConsumer implements VertexConsumer {
        private record Vertex(double x, double y, double z, int argb) {}

        private final boolean isStrip;
        private final List<Vertex> vertices = new ArrayList<>();

        private double x, y, z;
        private int color = 0xFFFFFFFF;
        private boolean hasCurrent = false;

        private LineConsumer(boolean isStrip) {
            this.isStrip = isStrip;
        }

        private void commit() {
            if (hasCurrent) {
                vertices.add(new Vertex(x, y, z, color));
                hasCurrent = false;
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            commit();
            // the wand code works camera-relative
            this.x = x + cameraPos.x;
            this.y = y + cameraPos.y;
            this.z = z + cameraPos.z;
            this.color = 0xFFFFFFFF;
            this.hasCurrent = true;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.color = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            this.color = color;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        private void flush() {
            commit();

            if (isStrip) {
                for (int i = 1; i < vertices.size(); i++) {
                    Vertex a = vertices.get(i - 1);
                    Vertex b = vertices.get(i);
                    if ((b.argb() >>> 24) == 0) {
                        continue; // jump
                    }
                    emit(a, b);
                }
            }
            else {
                for (int i = 0; i + 1 < vertices.size(); i += 2) {
                    emit(vertices.get(i), vertices.get(i + 1));
                }
            }
            vertices.clear();
        }

        private static void emit(Vertex a, Vertex b) {
            Gizmos.line(
                new Vec3(a.x(), a.y(), a.z()), new Vec3(b.x(), b.y(), b.z()),
                b.argb(), LINE_WIDTH
            );
        }
    }
}
