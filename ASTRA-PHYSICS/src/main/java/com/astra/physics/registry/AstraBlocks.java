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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.astra.physics.AstraPhysics;
import com.astra.physics.block.AltimeterBlock;
import com.astra.physics.block.BalloonBlock;
import com.astra.physics.block.CamberedWingBlock;
import com.astra.physics.block.EngineBlock;
import com.astra.physics.block.HelmBlock;
import com.astra.physics.block.GovernorBlock;
import com.astra.physics.block.GyroBlock;
import com.astra.physics.block.PropellerBlock;
import com.astra.physics.block.ReactionWheelBlock;
import com.astra.physics.block.SailBlock;
import com.astra.physics.block.StabilizerBlock;
import com.astra.physics.block.ThrusterBlock;
import com.astra.physics.block.WingBlock;

/**
 * The ASTRA component blocks.
 *
 * <p>They deliberately have no BlockEntities: assembly has to snapshot every block it absorbs,
 * and a component with no BlockEntity is trivially safe to move. All component animation is
 * driven from blockstate properties by the construct renderer instead.
 */
public final class AstraBlocks {
    private AstraBlocks() {}

    public static final Block HELM = register("helm", HelmBlock::new,
            wooden(MapColor.COLOR_BROWN, 2.5F));
    public static final Block ENGINE = register("engine", EngineBlock::new,
            metallic(MapColor.COLOR_GRAY, 4.0F));
    public static final Block PROPELLER = register("propeller", PropellerBlock::new,
            metallic(MapColor.COLOR_LIGHT_GRAY, 3.0F));
    public static final Block SAIL = register("sail", SailBlock::new,
            canvas(MapColor.WOOL, 1.2F));
    public static final Block WING = register("wing", WingBlock::new,
            metallic(MapColor.COLOR_LIGHT_GRAY, 2.5F));
    public static final Block THRUSTER = register("thruster", ThrusterBlock::new,
            metallic(MapColor.COLOR_BLACK, 4.0F));
    public static final Block REACTION_WHEEL = register("reaction_wheel", ReactionWheelBlock::new,
            metallic(MapColor.COLOR_CYAN, 3.5F));
    public static final Block BALLOON = register("balloon", BalloonBlock::new,
            canvas(MapColor.WOOL, 0.8F));
    public static final Block CAMBERED_WING = register("cambered_wing", CamberedWingBlock::new,
            metallic(MapColor.COLOR_LIGHT_GRAY, 2.5F));
    public static final Block STABILIZER = register("stabilizer", StabilizerBlock::new,
            metallic(MapColor.COLOR_LIGHT_GRAY, 2.0F));
    public static final Block ALTIMETER = register("altimeter", AltimeterBlock::new,
            metallic(MapColor.COLOR_BROWN, 2.0F));
    public static final Block GOVERNOR = register("governor", GovernorBlock::new,
            metallic(MapColor.COLOR_BROWN, 2.0F));
    public static final Block GYRO = register("gyro", GyroBlock::new,
            metallic(MapColor.COLOR_CYAN, 3.0F));

    /**
     * Earlier builds registered every component with a bare {@code Properties.of()}, which meant
     * zero hardness (they broke instantly, even by hand), no blast resistance, no step or break
     * sounds and no map colour. These give each component a material identity.
     */
    private static BlockBehaviour.Properties metallic(MapColor colour, float strength) {
        return BlockBehaviour.Properties.of()
                .mapColor(colour)
                .strength(strength, strength * 2.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }

    private static BlockBehaviour.Properties wooden(MapColor colour, float strength) {
        return BlockBehaviour.Properties.of()
                .mapColor(colour)
                .strength(strength, strength * 2.0F)
                .sound(SoundType.WOOD)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties canvas(MapColor colour, float strength) {
        return BlockBehaviour.Properties.of()
                .mapColor(colour)
                .strength(strength, strength)
                .sound(SoundType.WOOL)
                .noOcclusion();
    }

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

    /** Forces class initialisation so the registrations above run during mod startup. */
    public static void initialize() {
        AstraPhysics.LOGGER.info("Registered ASTRA component blocks.");
    }
}
