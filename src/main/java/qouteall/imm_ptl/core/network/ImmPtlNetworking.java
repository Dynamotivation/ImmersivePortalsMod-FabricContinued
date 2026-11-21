package qouteall.imm_ptl.core.network;

import net.minecraft.world.entity.EntitySpawnReason;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.dimension.DimensionIntId;

import java.util.Objects;
import java.util.UUID;

public class ImmPtlNetworking {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // client to server
    public static record TeleportPacket(
        int dimensionId, Vec3 eyePosBeforeTeleportation, UUID portalId
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TeleportPacket> TYPE =
            new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath("imm_ptl", "teleport")
            );
        
        public static final StreamCodec<FriendlyByteBuf, TeleportPacket> CODEC = StreamCodec.of(
            (b, p) -> p.write(b), TeleportPacket::read
        );
        
        public static TeleportPacket read(FriendlyByteBuf buf) {
            int dimId = buf.readVarInt();
            Vec3 pos = new Vec3(
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble()
            );
            UUID portalId = buf.readUUID();
            return new TeleportPacket(dimId, pos, portalId);
        }
        
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(dimensionId);
            buf.writeDouble(eyePosBeforeTeleportation.x);
            buf.writeDouble(eyePosBeforeTeleportation.y);
            buf.writeDouble(eyePosBeforeTeleportation.z);
            buf.writeUUID(portalId);
        }
        
        public void handle(ServerPlayer player) {
            net.minecraft.server.MinecraftServer server = player.level().getServer();
            // an unknown dimension id from the client is rejected instead of throwing
            ResourceKey<Level> dim = DimensionIntId.getServerMap(server).fromIntegerIdNullable(dimensionId);

            if (dim == null) {
                ServerTeleportationManager.of(server).onInvalidTeleportRequest(
                    player, "unknown dimension id " + dimensionId
                );
                return;
            }

            ServerTeleportationManager.of(server).onPlayerTeleportedInClient(
                player, dim, eyePosBeforeTeleportation, portalId
            );
        }
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    // server to client
    public static record GlobalPortalSyncPacket(
        int dimensionId, CompoundTag data
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<GlobalPortalSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                McHelper.newResourceLocation("imm_ptl:upd_glb_ptl")
            );
        
        public static final StreamCodec<FriendlyByteBuf, GlobalPortalSyncPacket> CODEC = StreamCodec.of(
            (b, p) -> p.write(b), GlobalPortalSyncPacket::read
        );
        
        public static GlobalPortalSyncPacket read(FriendlyByteBuf buf) {
            int dimId = buf.readVarInt();
            CompoundTag compoundTag = buf.readNbt();
            return new GlobalPortalSyncPacket(dimId, compoundTag);
        }
        
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(dimensionId);
            buf.writeNbt(data);
        }
        
        @Environment(EnvType.CLIENT)
        public void handle() {
            ResourceKey<Level> dim = PortalAPI.clientIntToDimKey(dimensionId);
            
            GlobalPortalStorage.receiveGlobalPortalSync(dim, data);
        }
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    /**
     * server to client
     * {@link ClientboundAddEntityPacket}
     * This packet is redirected, so there is no need to contain dimension id
     */
    private static final boolean DEBUG_PORTAL_SYNC = System.getProperty("ip.debugportals") != null;
    
    public static record PortalSyncPacket(
        int id,
        UUID uuid,
        EntityType<?> entityType,
        int dimensionId,
        double x,
        double y,
        double z,
        CompoundTag extraData
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PortalSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                McHelper.newResourceLocation("imm_ptl:spawn_portal")
            );
        
        public static final StreamCodec<RegistryFriendlyByteBuf, PortalSyncPacket> CODEC = StreamCodec.of(
            (b, p) -> p.write(b), PortalSyncPacket::read
        );
        
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUUID(uuid);
            ByteBufCodecs.registry(Registries.ENTITY_TYPE).encode(buf, entityType);
            buf.writeVarInt(dimensionId);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeNbt(extraData);
        }
        
        public static PortalSyncPacket read(RegistryFriendlyByteBuf buf) {
            int id = buf.readVarInt();
            UUID uuid = buf.readUUID();
            EntityType<?> type = ByteBufCodecs.registry(Registries.ENTITY_TYPE).decode(buf);
            int dimensionId = buf.readVarInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            CompoundTag extraData = buf.readNbt();
            return new PortalSyncPacket(id, uuid, type, dimensionId, x, y, z, extraData);
        }
        
        /**
         * {@link ClientPacketListener#handleAddEntity(ClientboundAddEntityPacket)}
         */
        @Environment(EnvType.CLIENT)
        public void handle() {
//            Helper.LOGGER.info("PortalSyncPacket handle {}", RenderStates.frameIndex);
            
            ResourceKey<Level> dimension = PortalAPI.clientIntToDimKey(dimensionId);
            ClientLevel world = ClientWorldLoader.getWorld(dimension);
            
            Entity existing = world.getEntity(id);
            
            if (existing instanceof Portal existingPortal) {
                // update existing portal (handles default animation)
                if (!Objects.equals(existingPortal.getUUID(), uuid)) {
                    LOGGER.error("UUID mismatch when syncing portal {} {}", existingPortal, uuid);
                    return;
                }
                
                if (existingPortal.getType() != entityType) {
                    LOGGER.error(
                        "Entity type mismatch when syncing portal {} {}", existingPortal, entityType
                    );
                    return;
                }
                
                if (DEBUG_PORTAL_SYNC) {
                    LOGGER.info(
                        "[PortalSync] update {} from dest {} {} to pos {},{},{} data {}",
                        existingPortal.getId(), existingPortal.getDestDim().identifier(),
                        existingPortal.getDestPos(), x, y, z, extraData
                    );
                }
                existingPortal.acceptDataSync(new Vec3(x, y, z), extraData);
                if (DEBUG_PORTAL_SYNC) {
                    LOGGER.info(
                        "[PortalSync] after update {} dest {} {} origin {}",
                        existingPortal.getId(), existingPortal.getDestDim().identifier(),
                        existingPortal.getDestPos(), existingPortal.getOriginPos()
                    );
                }
            }
            else {
                // spawn new portal
                if (DEBUG_PORTAL_SYNC) {
                    LOGGER.info("[PortalSync] spawn {} {} in {} data {}", id, entityType, dimension.identifier(), extraData);
                }
                Entity entity = entityType.create(world, EntitySpawnReason.MOB_SUMMONED);
                Validate.notNull(entity, "Entity type is null");
                
                if (!(entity instanceof Portal portal)) {
                    LOGGER.error("Spawned entity is not a portal. {} {}", entity, entityType);
                    return;
                }
                
                entity.setId(id);
                entity.setUUID(uuid);
                entity.syncPacketPositionCodec(x, y, z);
                entity.snapTo(x, y, z);
                
                portal.readPortalDataFromNbt(extraData);
                
                world.addEntity(entity);
                
                ClientWorldLoader.getWorld(portal.getDestDim());
                Portal.CLIENT_PORTAL_SPAWN_EVENT.invoker().accept(portal);
                
                if (IPGlobal.clientPortalLoadDebug) {
                    LOGGER.info("Portal loaded to client {}", portal);
                }
            }
        }
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    /**
     * 26.x made {@link net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket} a record
     * with a fixed codec, so the dimension can no longer be appended to it.
     * This payload is sent right before the position packet and carries the dimension
     * for the upcoming teleport id.
     */
    public static record PlayerPositionDimensionPacket(
        int teleportId, ResourceKey<Level> dimension
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PlayerPositionDimensionPacket> TYPE =
            new CustomPacketPayload.Type<>(
                McHelper.newResourceLocation("imm_ptl:player_position_dimension")
            );
        
        public static final StreamCodec<FriendlyByteBuf, PlayerPositionDimensionPacket> CODEC =
            StreamCodec.of(
                (b, p) -> {
                    b.writeVarInt(p.teleportId);
                    b.writeResourceKey(p.dimension);
                },
                b -> new PlayerPositionDimensionPacket(
                    b.readVarInt(), b.readResourceKey(Registries.DIMENSION)
                )
            );
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
        
        @Environment(EnvType.CLIENT)
        public void handleClient() {
            pendingPositionDimensions.put(teleportId, dimension);
        }
    }
    
    // client side: teleport id -> dimension of the pending position packet
    @Environment(EnvType.CLIENT)
    public static final java.util.Map<Integer, ResourceKey<Level>> pendingPositionDimensions =
        new java.util.concurrent.ConcurrentHashMap<>();
    
    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(
            TeleportPacket.TYPE, TeleportPacket.CODEC
        );
        
        PayloadTypeRegistry.clientboundPlay().register(
            PlayerPositionDimensionPacket.TYPE, PlayerPositionDimensionPacket.CODEC
        );
        
        PayloadTypeRegistry.clientboundPlay().register(
            GlobalPortalSyncPacket.TYPE, GlobalPortalSyncPacket.CODEC
        );
        
        PayloadTypeRegistry.clientboundPlay().register(
            PortalSyncPacket.TYPE, PortalSyncPacket.CODEC
        );
        
        ServerPlayNetworking.registerGlobalReceiver(
            TeleportPacket.TYPE,
            (packet, c) -> packet.handle(c.player())
        );
    }
    
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(
            PlayerPositionDimensionPacket.TYPE,
            (packet, c) -> packet.handleClient()
        );
        
        ClientPlayNetworking.registerGlobalReceiver(
            GlobalPortalSyncPacket.TYPE,
            (packet, c) -> packet.handle()
        );
        
        ClientPlayNetworking.registerGlobalReceiver(
            PortalSyncPacket.TYPE,
            (packet, c) -> packet.handle()
        );
    }
    
}
