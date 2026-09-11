package com.astra.physics.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

import com.astra.physics.AstraPhysics;

public final class AstraItems {
    private AstraItems() {}

    public static final Item PHYSICS_WAND = register(
            "physics_wand",
            Item::new,
            new Item.Properties().stacksTo(1)
    );

    public static final ResourceKey<CreativeModeTab> ASTRA_TAB_KEY = ResourceKey.create(
            BuiltInRegistries.CREATIVE_MODE_TAB.key(),
            Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, "astra_physics")
    );

    public static final CreativeModeTab ASTRA_TAB = FabricItemGroup.builder()
            .icon(() -> new ItemStack(PHYSICS_WAND))
            .title(Component.translatable("itemGroup.astra_physics"))
            .displayItems((params, output) -> {
                output.accept(PHYSICS_WAND);
                output.accept(AstraBlocks.HELM);
                output.accept(AstraBlocks.ENGINE);
                output.accept(AstraBlocks.PROPELLER);
                output.accept(AstraBlocks.SAIL);
                output.accept(AstraBlocks.WING);
                output.accept(AstraBlocks.THRUSTER);
                output.accept(AstraBlocks.REACTION_WHEEL);
                output.accept(AstraBlocks.BALLOON);
                output.accept(AstraBlocks.CAMBERED_WING);
                output.accept(AstraBlocks.STABILIZER);
                output.accept(AstraBlocks.ALTIMETER);
                output.accept(AstraBlocks.GOVERNOR);
                output.accept(AstraBlocks.GYRO);
            })
            .build();

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory,
                                                Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(AstraPhysics.MOD_ID, name)
        );
        T item = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        return item;
    }

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ASTRA_TAB_KEY, ASTRA_TAB);
        AstraPhysics.LOGGER.info("Registered ASTRA Physics creative tab.");
    }
}
