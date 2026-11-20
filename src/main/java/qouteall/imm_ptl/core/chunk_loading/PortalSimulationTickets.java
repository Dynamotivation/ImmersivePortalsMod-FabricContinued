package qouteall.imm_ptl.core.chunk_loading;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.List;
import java.util.WeakHashMap;

/**
 * Makes a small area around portal destinations SIMULATED (entity ticking).
 * <p>
 * Since 1.21.5 a chunk only ticks entities when it has a ticket with {@link TicketType#FLAG_SIMULATION}
 * (DistanceManager.inEntityTickingRange reads only the SimulationChunkTracker) and its LOADING level is
 * ENTITY_TICKING (otherwise the entity section is not TICKING and the entity is not in ServerLevel.entityTickList).
 * The ImmPtl loading ticket ({@link ImmPtlChunkTickets#TICKET_TYPE}) is loading-only, so in a dimension without
 * players everything that comes through a portal was frozen (and its changes were never sent: EntitySync.tick
 * also checks inEntityTickingRange) until a player entered that dimension.
 * <p>
 * Two sources, both with a timeout ticket (re-adding the same ticket resets its timer, so no explicit release is needed):
 * - a player within {@link #VIEW_RANGE_BLOCKS} of a portal: radius {@link #VIEW_SIMULATION_RADIUS} around its destination,
 * - a non-player entity teleported by a portal: radius {@link #TELEPORT_SIMULATION_RADIUS} around where it arrived.
 */
public class PortalSimulationTickets {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int TIMEOUT_TICKS = 200;

    // loading + simulation + keep the dimension active (ServerLevel.emptyTime gate)
    public static final TicketType TICKET_TYPE = Registry.register(
        BuiltInRegistries.TICKET_TYPE,
        McHelper.newResourceLocation("imm_ptl:portal_simulation"),
        new TicketType(
            TIMEOUT_TICKS,
            TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE
        )
    );

    // chunks within this chunk distance of the centre are entity ticking
    public static final int VIEW_SIMULATION_RADIUS = 2;
    public static final int TELEPORT_SIMULATION_RADIUS = 1;
    public static final double VIEW_RANGE_BLOCKS = 48;

    // for logging only: centre chunk -> game time when its ticket expires (approximately)
    private static final WeakHashMap<ServerLevel, Long2LongOpenHashMap> ACTIVE = new WeakHashMap<>();

    public static void init() {
        // loads the class -> registers the ticket type during mod init
        IPGlobal.SERVER_CLEANUP_EVENT.register(PortalSimulationTickets::cleanup);
    }

    /**
     * Ticket level for "entity ticking within radius": ENTITY_TICKING is level 31, FULL is 33;
     * addTicketWithRadius uses level 33 - radius.
     */
    private static int toTicketRadius(int entityTickingRadius) {
        return entityTickingRadius + 2;
    }

    public static void addSimulation(ServerLevel world, ChunkPos center, int entityTickingRadius, String reason) {
        if (!world.getServer().isRunning()) {
            return; // never add tickets while the server is stopping/saving
        }

        world.getChunkSource().addTicketWithRadius(TICKET_TYPE, center, toTicketRadius(entityTickingRadius));

        long now = world.getGameTime();
        Long2LongOpenHashMap active = ACTIVE.computeIfAbsent(world, k -> new Long2LongOpenHashMap());
        long key = center.pack();
        long oldExpiry = active.getOrDefault(key, Long.MIN_VALUE);
        active.put(key, now + TIMEOUT_TICKS);
        if (oldExpiry < now) {
            LOGGER.info(
                "[ImmPtl] Portal simulation: chunk {} in {} entity-ticking (radius {}) because {}",
                center, world.dimension().identifier(), entityTickingRadius, reason
            );
            if (active.size() > 256) {
                active.long2LongEntrySet().removeIf(e -> e.getLongValue() < now);
            }
        }
    }

    /**
     * Called from {@link ImmPtlChunkTracking#updateForPlayer(ServerPlayer)} (every ~13 ticks per player).
     */
    public static void updateForPlayer(ServerPlayer player) {
        if (!IPConfig.getConfig().enableImmPtlChunkLoading) {
            return;
        }

        ServerLevel world = (ServerLevel) player.level();
        MinecraftServer server = world.getServer();

        List<Portal> portals = McHelper.findEntitiesRough(
            Portal.class, world, player.position(),
            (int) Math.ceil(VIEW_RANGE_BLOCKS / 16) + 1,
            p -> !p.getIsGlobal() && p.broadcastToPlayer(player)
        );

        for (Portal portal : portals) {
            if (portal.getDistanceToNearestPointInPortal(player.position()) > VIEW_RANGE_BLOCKS) {
                continue;
            }
            ServerLevel destWorld = server.getLevel(portal.getDestDim());
            if (destWorld == null) {
                continue;
            }
            addSimulation(
                destWorld,
                ChunkPos.containing(BlockPos.containing(portal.getDestPos())),
                VIEW_SIMULATION_RADIUS,
                "player " + player.getName().getString() + " is near portal " + portal.getUUID()
            );
        }
    }

    public static void onEntityTeleported(Entity entity, Portal portal) {
        if (!(entity.level() instanceof ServerLevel destWorld)) {
            return;
        }
        addSimulation(
            destWorld,
            entity.chunkPosition(),
            TELEPORT_SIMULATION_RADIUS,
            entity.getType().toShortString() + " teleported through portal " + portal.getUUID()
        );
    }

    private static void cleanup(MinecraftServer server) {
        ACTIVE.clear();
    }
}
