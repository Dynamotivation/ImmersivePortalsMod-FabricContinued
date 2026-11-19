package qouteall.imm_ptl.core.render;

import net.minecraft.util.profiling.Profiler;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap;
import qouteall.imm_ptl.core.ducks.IERenderSection;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.GcMonitor;
import qouteall.q_misc_util.Helper;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.function.LongConsumer;

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
        ImmPtlClientChunkMap.clientChunkUnloadSignal.connect(section -> {
            ResourceKey<Level> dimension = section.getLevel().dimension();
            
            LevelRenderer worldRenderer = ClientWorldLoader.WORLD_RENDERER_MAP.get(dimension);
            
            if (worldRenderer != null) {
                ViewArea viewArea = ((IEWorldRenderer) worldRenderer).ip_getBuiltChunkStorage();
                if (viewArea instanceof ImmPtlViewArea immPtlViewArea) {
                    immPtlViewArea.onChunkUnload(section.getPos().x(), section.getPos().z());
                }
            }
        });
        
        IPGlobal.POST_CLIENT_TICK_EVENT.register(() -> {
            if (ClientWorldLoader.getIsInitialized()) {
                for (ClientLevel world : ClientWorldLoader.getClientWorlds()) {
                    LevelRenderer worldRenderer =
                        ClientWorldLoader.getWorldRenderer(world.dimension());
                    ViewArea viewArea = ((IEWorldRenderer) worldRenderer).ip_getBuiltChunkStorage();
                    if (viewArea instanceof ImmPtlViewArea immPtlViewArea) {
                        immPtlViewArea.tick();
                    }
                }
            }
        });
    }
    
    public ImmPtlViewArea(
        SectionRenderDispatcher sectionBuilder,
        Level world,
        int r,
        LevelRenderer worldRenderer
    ) {
    }
    
    @Override
    protected void createSections(SectionRenderDispatcher sectionBuilder_1) {
    }
    
    @Override
    public void releaseAllBuffers() {
    }
    
    /**
     * It will only be called during vanilla outer world rendering
     * Won't be called in portal rendering
     * In {@link net.minecraft.client.renderer.SectionOcclusionGraph#initializeQueueForFullUpdate(Camera, Queue)} it reads the RenderChunks in another thread.
     */
    @Override
    public void repositionCamera(double playerX, double playerZ) {
    }
    
    @Override
    public void setDirty(int cx, int cy, int cz, boolean isImportant) {
    }
    
    public RenderSection provideBuiltChunkByChunkPos(int cx, int cy, int cz) {
        return null;
    }
    
    /**
     * {@link ViewArea#repositionCamera(double, double)}
     */
    private Preset createPresetByChunkPos(int sectionX, int sectionZ) {
        return null;
    }
    
    /**
     * {@link ViewArea#repositionCamera(double, double)}
     */
    private void foreachPresetCoveredChunkPoses(
        int centerChunkX, int centerChunkZ,
        LongConsumer func
    ) {
    }
    
    //copy because private
    private int getChunkIndex(int x, int y, int z) {
        return 0;
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
    
    private boolean shouldDropPreset(long dropTime, long currentTime, Preset preset) {
        return false;
    }
    
    private Set<RenderSection> getAllActiveBuiltChunks() {
        return null;
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
    
    // NOTE it may be accessed from another thread
    @Nullable
    @Override
    protected RenderSection getRenderSectionAt(BlockPos pos) {
        return null;
    }
    
    @Nullable
    public RenderSection rawFetch(int cx, int cy, int cz, long timeMark) {
        if (cy < minSectionY || cy >= endSectionY) {
            return null;
        }
        
        long l = ChunkPos.pack(cx, cz);
        Column column = provideColumn(l);
        
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
