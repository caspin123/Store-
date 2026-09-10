package com.astra.physics.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.astra.physics.AstraPhysics;
import com.astra.physics.block.AstraFacingBlock;
import com.astra.physics.block.HelmBlock;

/**
 * Simple ASTRA component blocks for the early physics prototype.
 * They deliberately do not use BlockEntities yet so assembly remains data-safe.
 */
public final class AstraBlocks {
    private AstraBlocks() {}

    public static final Block HELM = register("helm", HelmBlock::new, BlockBehaviour.Properties.of().noOcclusion());
    public static final Block ENGINE = register("engine", AstraFacingBlock::new, BlockBehaviour.Properties.of().noOcclusion());
    public static final Block PROPELLER = register("propeller", AstraFacingBlock::new, BlockBehaviour.Properties.of().noOcclusion());
    public static final Block SAIL = register("sail", AstraFacingBlock::new, BlockBehaviour.Properties.of().noOcclusion());
    public static final Block WING = register("wing", AstraFacingBlock::new, BlockBehaviour.Properties.of().noOcclusion());
    public static final Block THRUSTER = register("thruster", AstraFacingBlock::new, BlockBehaviour.Properties.of().noOcclusion());

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

        Block block = factory.apply(properties.setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

        BlockItem blockItem = new BlockItem(
                block,
                new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()
        );
        Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);
        return block;
    }

    /** Forces class initialization/registration during mod startup. */
    public static void initialize() {
        AstraPhysics.LOGGER.info("Registered ASTRA prototype component blocks.");
    }
}
