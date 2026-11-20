package qouteall.imm_ptl.core.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap;
import qouteall.imm_ptl.core.ducks.IERenderSection;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.GcMonitor;

/**
 * A portal-aware view area.
 *
 * TODO(port): 26.x rebuilt {@link ViewArea} around {@code RotatingSectionStorage} plus a
 * {@code SectionOcclusionGraph}, and moved section discovery into the extract phase.
 * The portal-specific section storage is kept as a shell until the renderer redesign;
 * everything here is currently inert.
 */
@Environment(EnvType.CLIENT)
public class ImmPtlViewArea extends ViewArea {
    
    public static class Column {
        public long mark = 0;
        public RenderSection[] sections;
        
        public Column(RenderSection[] sections) {
            this.sections = sections;
        }
    }
    
    public static class Preset {
        public final RenderSection[] data;
        public long lastActiveTime = 0;
        
        public Preset(
            RenderSection[] data
        ) {
            this.data = data;
        }
    }
    
    private final SectionRenderDispatcher factory;
    private final Long2ObjectOpenHashMap<Column> columnMap = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Preset> presets = new Long2ObjectOpenHashMap<>();
    private Preset currentPreset = null;
    
    public final int minSectionY;
    public final int endSectionY;
    
    private boolean isAlive = true;
    
    public static void init() {
        // TODO(26.2): the portal-aware section storage (column presets for remote dimensions) is not ported.
        // Each dimension's LevelRenderer uses the vanilla ViewArea instead.
    }
    
    public ImmPtlViewArea(
        SectionRenderDispatcher sectionBuilder,
        Level world,
        int r,
        LevelRenderer worldRenderer
    ) {
        super(
            sectionBuilder,
            world.getMinY(),
            world.getMaxY(),
            world.getMinSectionY(),
            world.getMaxSectionY(),
            r,
            worldRenderer.sectionOcclusionGraph()
        );
        this.factory = sectionBuilder;
        this.minSectionY = world.getMinSectionY();
        this.endSectionY = world.getMaxSectionY() + 1;
    }
    
    public RenderSection provideBuiltChunkByChunkPos(int cx, int cy, int cz) {
        return null;
    }
    
    public Column provideColumn(long sectionPos) {
        return columnMap.computeIfAbsent(sectionPos, this::createColumn);
    }
    
    private Column createColumn(long sectionPos) {
        return null;
    }
    
    private void tick() {
        if (!isAlive) {
            return;
        }
        
        ClientLevel worldClient = Minecraft.getInstance().level;
        if (worldClient != null) {
            if (GcMonitor.isMemoryNotEnough()) {
                if (worldClient.getGameTime() % 3 == 0) {
                    purge();
                }
            }
            else {
                if (worldClient.getGameTime() % 213 == 66) {
                    purge();
                }
            }
        }
    }
    
    private void purge() {
    }
    
    public int getManagedSectionNum() {
        return 0;
    }
    
    public String getDebugString() {
        return String.format(
            "Built Section Storage Columns:%s",
            columnMap.size()
        );
    }
    
    public int getRadius() {
        return 0;
    }
    
    public boolean isRegionActive(int cxStart, int czStart, int cxEnd, int czEnd) {
        for (int cx = cxStart; cx <= cxEnd; cx++) {
            for (int cz = czStart; cz <= czEnd; cz++) {
                if (columnMap.containsKey(ChunkPos.pack(cx, cz))) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    public void onChunkUnload(int sectionX, int sectionZ) {
        long sectionPos = ChunkPos.pack(sectionX, sectionZ);
        Column column = columnMap.get(sectionPos);
        if (column != null) {
            for (RenderSection builtChunk : column.sections) {
                ((IERenderSection) builtChunk).portal_fullyReset();
            }
        }
    }
    
    public RenderSection getSectionFromRawArray(
        BlockPos sectionOrigin, RenderSection[] sections
    ) {
        return null;
    }
    
    @Nullable
    public RenderSection rawFetch(int cx, int cy, int cz, long timeMark) {
        if (cy < minSectionY || cy >= endSectionY) {
            return null;
        }
        
        long l = ChunkPos.pack(cx, cz);
        Column column = provideColumn(l);
        
        if (column == null) {
            return null;
        }
        
        column.mark = timeMark;
        
        int yOffset = cy - minSectionY;
        
        return column.sections[yOffset];
    }
    
    @Nullable
    public RenderSection rawGet(int cx, int cy, int cz) {
        if (cy < minSectionY || cy >= endSectionY) {
            return null;
        }
        
        long l = ChunkPos.pack(cx, cz);
        Column column = columnMap.get(l);
        
        if (column == null) {
            return null;
        }
        
        int yOffset = cy - minSectionY;
        
        return column.sections[yOffset];
    }
}
