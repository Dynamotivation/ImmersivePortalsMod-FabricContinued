package qouteall.imm_ptl.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Since 1.21.2 items and blocks must know their registry key when they are constructed
 * (Item.Properties#setId / BlockBehaviour.Properties#setId), because the translation key and
 * model location are derived from it. Immersive Portals creates its items/blocks in static
 * initializers and registers them later, so the ids are attached here.
 */
public class IPRegistryHelper {
    public static final String NAMESPACE = "immersive_portals";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static Item.Properties itemProps(String path) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(path)));
    }

    public static BlockBehaviour.Properties blockProps(String path) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id(path)));
    }
}
