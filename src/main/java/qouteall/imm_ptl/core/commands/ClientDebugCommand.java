package qouteall.imm_ptl.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEClientWorld;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.IPConfigGUI;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.ForceMainThreadRebuild;
import qouteall.imm_ptl.core.render.ImmPtlViewArea;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.MyTaskList;

import java.lang.ref.WeakReference;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class ClientDebugCommand {
    
    public static void register(
        CommandDispatcher<FabricClientCommandSource> dispatcher
    ) {
    }
    
    private static void registerPortalWandCommands(
        LiteralArgumentBuilder<FabricClientCommandSource> builder
    ) {
        builder.then(ClientCommands
            .literal("set_cursor_alignment")
            .then(ClientCommands.argument("alignment", IntegerArgumentType.integer(0))
                .executes(context -> {
                    int alignment = IntegerArgumentType.getInteger(context, "alignment");
                    
                    IPConfig config = IPConfig.getConfig();
                    config.portalWandCursorAlignment = alignment;
                    config.saveConfigFile();
                    
                    context.getSource().sendFeedback(Component.translatable("imm_ptl.wand.alignment_updated"));
                    
                    return 0;
                })
            )
        );
    }
    
    private static void printClassPath() {
        System.out.println(
            Arrays.stream(
                ((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs()
            ).map(
                url -> "\"" + url.getFile().substring(1).replace("%20", " ") + "\""
            ).collect(Collectors.joining(",\n"))
        );
    }
    
    private static void registerSwitchCommand(
        LiteralArgumentBuilder<FabricClientCommandSource> builder,
        String name,
        Consumer<Boolean> setFunction
    ) {
        builder = builder.then(ClientCommands
            .literal(name + "_enable")
            .executes(context -> {
                setFunction.accept(true);
                return 0;
            })
        );
        builder = builder.then(ClientCommands
            .literal(name + "_disable")
            .executes(context -> {
                setFunction.accept(false);
                return 0;
            })
        );
    }
    
    private static int isClientChunkLoaded(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        RemoteCallables.reportClientChunkLoadStatus(Minecraft.getInstance().level.dimension(), chunkX, chunkZ);
        return 0;
    }
    
    public static class RemoteCallables {
        public static void reportClientChunkLoadStatus(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
            ClientLevel world = ClientWorldLoader.getWorld(dimension);
            ChunkAccess chunk = world.getChunk(chunkX, chunkZ);
            CHelper.printChat(
                chunk != null && !(chunk instanceof EmptyLevelChunk) ?
                    "client loaded" : "client not loaded"
            );
        }
        
        public static void reportClientPlayerStatus() {
            LocalPlayer playerSP = Minecraft.getInstance().player;
            
            CHelper.printChat(
                String.format(
                    "On Client %s %s removal:%s added:%s age:%s",
                    playerSP.level().dimension().identifier(),
                    playerSP.blockPosition(),
                    playerSP.getRemovalReason(),
                    playerSP.level().getEntity(playerSP.getId()) != null,
                    playerSP.tickCount
                )
            );
        }
        
        public static void doListPortals() {
            StringBuilder result = new StringBuilder();
            
            result.append("Client Portals\n");
            ClientWorldLoader.getClientWorlds().forEach((world) -> {
                result.append(world.dimension().identifier().toString() + "\n");
                for (Entity e : world.entitiesForRendering()) {
                    if (e instanceof Portal) {
                        result.append(e.toString());
                        result.append("\n");
                    }
                }
            });
            
            CHelper.printChat(result.toString());
        }
        
        public static void reportResourceConsumption() {
            StringBuilder str = new StringBuilder();
            
            str.append("Client Chunk:\n");
            ClientWorldLoader.getClientWorlds().forEach(world -> {
                str.append(String.format(
                    "%s %s\n",
                    world.dimension().identifier(),
                    world.getChunkSource().getLoadedChunksCount()
                ));
            });
            
            
            str.append("Chunk Mesh Sections:\n");
            ClientWorldLoader.WORLD_RENDERER_MAP.forEach(
                (dimension, worldRenderer) -> {
                    str.append(String.format(
                        "%s %s\n",
                        dimension.identifier(),
                        ((ImmPtlViewArea) ((IEWorldRenderer) worldRenderer)
                            .ip_getBuiltChunkStorage()
                        ).getManagedSectionNum()
                    ));
                }
            );
            
            String result = str.toString();
            
            CHelper.printChat(result);
        }
        
        public static void setNoFog(boolean cond) {
            IPGlobal.debugDisableFog = cond;
        }
    }
    
    private static int setMaxPortalLayer(int m) {
        IPGlobal.maxPortalLayer = m;
        return 0;
    }
    
    
    public static class TestRemoteCallable {
        public static void serverToClient(
            String str, int integer, double doubleNum, Identifier identifier,
            ResourceKey<Level> dimension, ResourceKey<Biome> biomeKey,
            BlockPos blockPos, Vec3 vec3d
        ) {
            Helper.log(str + integer + doubleNum + identifier + dimension + biomeKey + blockPos + vec3d);
        }
        
        public static void clientToServer(
            ServerPlayer player,
            UUID uuid,
            Block block, BlockState blockState,
            Item item, ItemStack itemStack,
            CompoundTag compoundTag, Component text, int[] intArray
        ) {
            Helper.log(
                player.getName().getString() + uuid + block + blockState + item + itemStack
                    + compoundTag + text.getString() + Arrays.toString(intArray)
            );
        }
    }
    
    private static void testRemoteProcedureCall(ServerPlayer player) {
        Minecraft.getInstance().execute(() -> {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.put("test", IntTag.valueOf(7));
            McRemoteProcedureCall.tellServerToInvoke(
                "qouteall.imm_ptl.core.commands.ClientDebugCommand.TestRemoteCallable.clientToServer",
                new UUID(3, 3),
                Blocks.ACACIA_PLANKS,
                Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.Z),
                Items.COMPASS,
                new ItemStack(Items.ACACIA_LOG, 2),
                compoundTag,
                Component.literal("test"),
                new int[]{777, 765}
            );
        });
        
        player.level().getServer().execute(() -> {
            McRemoteProcedureCall.tellClientToInvoke(
                player,
                "qouteall.imm_ptl.core.commands.ClientDebugCommand.TestRemoteCallable.serverToClient",
                "string", 2, 3.5, McHelper.newResourceLocation("imm_ptl:oops"),
                Level.NETHER, Biomes.JUNGLE,
                new BlockPos(3, 5, 4),
                new Vec3(7, 4, 1)
            );
        });
    }
    
    
    public static void disableWarning() {
        IPConfig ipConfig = IPConfig.getConfig();
        ipConfig.enableWarning = false;
        ipConfig.saveConfigFile();
    }
    
    public static void disableWarningFor(String warningKey) {
        IPConfig ipConfig = IPConfig.getConfig();
        ipConfig.disabledWarnings.add(warningKey);
        ipConfig.saveConfigFile();
    }
    
    public static void disableUpdateCheck() {
        IPConfig ipConfig = IPConfig.getConfig();
        ipConfig.enableUpdateNotification = false;
        ipConfig.saveConfigFile();
    }
}
