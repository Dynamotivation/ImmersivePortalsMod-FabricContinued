package qouteall.imm_ptl.core.portal.nether_portal;

import net.minecraft.world.level.storage.ValueInput;
import qouteall.q_misc_util.my_util.ValueTagBridge;
import net.minecraft.world.level.storage.ValueOutput;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import qouteall.imm_ptl.core.platform_specific.O_O;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.LimitedLogger;
import qouteall.q_misc_util.my_util.MyTaskList;

import java.util.List;
import java.util.UUID;

public abstract class BreakablePortalEntity extends Portal {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static record OverlayInfo(
        BlockState blockState,
        double opacity,
        double offset,
        @Nullable DQuaternion rotation
    ) {
    }
    
    public BlockPortalShape blockPortalShape;
    public UUID reversePortalId;
    public boolean unbreakable = false;
    private boolean isNotified = true;
    private boolean shouldBreakPortal = false;
    
    @Nullable
    protected OverlayInfo overlayInfo;
    
    public BreakablePortalEntity(
        EntityType<?> entityType_1,
        Level world_1
    ) {
        super(entityType_1, world_1);
    }
    
    
    @Override
    public boolean isPortalValid() {
        if (level().isClientSide()) {
            return super.isPortalValid();
        }
        return super.isPortalValid() && blockPortalShape != null && reversePortalId != null;
    }
    
    @Override
    protected void readAdditionalSaveData(ValueInput ip_input) {
        readAdditionalSaveDataTag(ValueTagBridge.toTag(ip_input));
    }

    // Also used by the client data sync (Portal.readPortalDataFromNbt / acceptDataSync), which only calls the tag
    // version: without this override reversePortalId stayed null on the client
    @Override
    protected void readAdditionalSaveDataTag(CompoundTag compoundTag) {
        super.readAdditionalSaveDataTag(compoundTag);
        if (compoundTag.contains("netherPortalShape")) {
            blockPortalShape = new BlockPortalShape(compoundTag.getCompoundOrEmpty("netherPortalShape"));
        }
        
        reversePortalId = Helper.getUuid(compoundTag, "reversePortalId");
        if (reversePortalId == null) {
            Helper.err("missing reverse portal id " + compoundTag);
            reversePortalId = Util.NIL_UUID;
        }
        
        unbreakable = compoundTag.getBooleanOr("unbreakable", false);
        
        if (compoundTag.contains("overlayBlockState")) {
            BlockState overlayBlockState = NbtUtils.readBlockState(
                level().holderLookup(Registries.BLOCK),
                compoundTag.getCompoundOrEmpty("overlayBlockState")
            );
            if (overlayBlockState.isAir()) {
                overlayInfo = null;
            }
            else {
                double overlayOpacity = compoundTag.getDoubleOr("overlayOpacity", 0.0);
                if (overlayOpacity == 0) {
                    overlayOpacity = 0.5;
                }
                double overlayOffset = compoundTag.getDoubleOr("overlayOffset", 0.0);
                DQuaternion rotation = Helper.getQuaternion(compoundTag, "overlayRotation");
                
                overlayInfo = new OverlayInfo(
                    overlayBlockState, overlayOpacity, overlayOffset, rotation
                );
            }
        }
        else {
            overlayInfo = null;
        }
    }
    
    @Override
    protected void addAdditionalSaveData(ValueOutput ip_output) {
        CompoundTag ip_tag = new CompoundTag();
        addAdditionalSaveDataTag(ip_tag);
        ValueTagBridge.writeTag(ip_output, ip_tag);
    }
    
    // CompoundTag based implementation kept from the 1.21 code, see ValueTagBridge
    protected void addAdditionalSaveDataTag(CompoundTag compoundTag) {
        super.addAdditionalSaveDataTag(compoundTag);
        if (blockPortalShape != null) {
            compoundTag.put("netherPortalShape", blockPortalShape.toTag());
        }
        Helper.putUuid(compoundTag, "reversePortalId", reversePortalId);
        compoundTag.putBoolean("unbreakable", unbreakable);
        
        if (overlayInfo != null) {
            compoundTag.put("overlayBlockState", NbtUtils.writeBlockState(overlayInfo.blockState));
            compoundTag.putDouble("overlayOpacity", overlayInfo.opacity);
            compoundTag.putDouble("overlayOffset", overlayInfo.offset);
            Helper.putQuaternion(compoundTag, "overlayRotation", overlayInfo.rotation);
        }
    }
    
    private void breakPortalOnThisSide() {
        if (isRemoved()) {
            return;
        }
        blockPortalShape.area.forEach(
            blockPos -> {
                if (level().getBlockState(blockPos).getBlock() == PortalPlaceholderBlock.instance) {
                    level().setBlockAndUpdate(
                        blockPos, Blocks.AIR.defaultBlockState()
                    );
                }
            }
        );
        this.remove(RemovalReason.KILLED);
        
        Helper.log("Broke " + this);
    }
    
    private boolean integrityCheckScheduled = false;
    
    public void notifyPlaceholderUpdate() {
        isNotified = true;
        
        // The check normally runs in the entity tick, but a portal in a dimension the player only looks into (through
        // a portal) may not be ticking. So run it from the server task list, after the current block update finished.
        if (level().isClientSide() || unbreakable || isRemoved() || integrityCheckScheduled) {
            return;
        }
        integrityCheckScheduled = true;
        ServerTaskList.of(level().getServer()).addTask(MyTaskList.withRetryNumberLimit(
            1,
            () -> {
                integrityCheckScheduled = false;
                if (isRemoved()) {
                    return true;
                }
                isNotified = false;
                checkPortalIntegrity();
                if (shouldBreakPortal) {
                    breakPortalOnThisSide();
                }
                return true;
            },
            () -> integrityCheckScheduled = false
        ));
    }
    
    private BreakablePortalEntity getReversePortal() {
        
        ServerLevel world = level().getServer().getLevel(getDestDim());
        Entity entity = world.getEntity(reversePortalId);
        if (entity instanceof BreakablePortalEntity) {
            return (BreakablePortalEntity) entity;
        }
        
        // the stored id can be stale (relinking, regeneration): find the reverse portal by geometry
        if (isOtherSideChunkLoaded()) {
            List<BreakablePortalEntity> revs = findReversePortals(this);
            if (!revs.isEmpty()) {
                return revs.get(0);
            }
        }
        return null;
    }
    
    // break the partner immediately instead of only flagging it: a flag is only handled in the partner's own tick,
    // which does not happen when the partner's chunk is only loaded for portal rendering
    private void breakReverse(BreakablePortalEntity reversePortal) {
        syncBrokenFrameBlocks(reversePortal);
        reversePortal.shouldBreakPortal = true;
        reversePortal.breakPortalOnThisSide();
    }
    
    // A frame block that was mined on this side is also broken on the other side, so the same border block does not
    // have to be mined twice. The matching block is found by transforming the block centre through the portal.
    private void syncBrokenFrameBlocks(BreakablePortalEntity reversePortal) {
        if (blockPortalShape == null || reversePortal.blockPortalShape == null || level().isClientSide()) {
            return;
        }
        if (!(level() instanceof net.minecraft.server.level.ServerLevel) || !(reversePortal.level() instanceof net.minecraft.server.level.ServerLevel otherLevel)) {
            return;
        }
        for (BlockPos pos : blockPortalShape.frameAreaWithoutCorner) {
            if (O_O.isObsidian(level().getBlockState(pos))) {
                continue;
            }
            BlockPos other = BlockPos.containing(transformPoint(net.minecraft.world.phys.Vec3.atCenterOf(pos)));
            if (reversePortal.blockPortalShape.frameAreaWithoutCorner.contains(other)
                && O_O.isObsidian(otherLevel.getBlockState(other))
            ) {
                otherLevel.destroyBlock(other, false);
            }
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (level().isClientSide()) {
            addSoundAndParticle();
        }
        else {
            if (!unbreakable) {
                if (isNotified || level().getGameTime() % 233 == getId() % 233) {
                    isNotified = false;
                    checkPortalIntegrity();
                }
                if (shouldBreakPortal) {
                    breakPortalOnThisSide();
                }
            }
        }
        
    }
    
    private void checkPortalIntegrity() {
        Validate.isTrue(!level().isClientSide());
        
        if (!isPortalValid()) {
            remove(RemovalReason.KILLED);
            return;
        }
        
        if (!isPortalIntactOnThisSide()) {
            markShouldBreak();
        }
        else if (!isPortalPaired()) {
            Helper.err("Break portal because of abnormal pairing");
            markShouldBreak();
        }
    }
    
    
    protected abstract boolean isPortalIntactOnThisSide();
    
    @Environment(EnvType.CLIENT)
    protected abstract void addSoundAndParticle();
    
    private static final LimitedLogger limitedLogger = new LimitedLogger(20);
    
    public boolean isPortalPaired() {
        Validate.isTrue(!level().isClientSide());
        
        if (isOneWay()) {
            return true;
        }
        
        if (!isOtherSideChunkLoaded()) {
            return true;
        }
        
        List<BreakablePortalEntity> revs = findReversePortals(this);
        if (revs.size() == 1) {
            BreakablePortalEntity reversePortal = revs.get(0);
            if (reversePortal.getDestPos().distanceToSqr(getOriginPos()) > 1) {
                return false;
            }
            else {
                return true;
            }
        }
        else if (revs.size() > 1) {
            return false;
        }
        else {
//            limitedLogger.err("Missing Reverse Portal " + this);
            return true;
        }
    }
    
    public void markShouldBreak() {
        shouldBreakPortal = true;
        
        if (isOneWay()) {
            return;
        }
        
        BreakablePortalEntity reversePortal = getReversePortal();
        if (reversePortal != null) {
            breakReverse(reversePortal);
        }
        else {
            ServerTaskList.of(level().getServer()).addTask(MyTaskList.withRetryNumberLimit(
                30,
                () -> {
                    if (this.isRemoved()) {
                        return true;
                    }
                    
                    if (!this.isOtherSideChunkLoaded()) {
                        LOGGER.info("The other side chunk is not loaded. Delay finding reverse portal of {}.", this);
                        return false;
                    }
                    
                    BreakablePortalEntity reversePortal1 = getReversePortal();
                    if (reversePortal1 != null) {
                        breakReverse(reversePortal1);
                    }
                    
                    return true;
                },
                () -> {}
            ));
        }
    }
    
    
    public static <T extends Portal> List<T> findReversePortals(T portal) {
        List<T> revs = McHelper.findEntitiesByBox(
            (Class<T>) portal.getClass(),
            portal.getDestinationWorld(),
            new AABB(BlockPos.containing(portal.getDestPos())),
            10,
            e -> (e.getOriginPos().distanceToSqr(portal.getDestPos()) < 0.1) &&
                e.getContentDirection().dot(portal.getNormal()) > 0.6
        );
        return revs;
    }
    
    public boolean isOneWay() {
        // null: the entity is not fully initialized yet (e.g. on the client before the sync data was read)
        return reversePortalId != null && reversePortalId.equals(Util.NIL_UUID);
    }
    
    public void markOneWay() {
        reversePortalId = Util.NIL_UUID;
    }
    
    public OverlayInfo getActualOverlay() {
        return overlayInfo;
    }
}
