package com.astra.physics.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Stable on-disk encoding for a {@link BlockState}.
 *
 * <p>Network packets can use raw numeric state IDs because both ends of a connection share one
 * registry snapshot. A save file cannot: numeric IDs are assigned by registry order and shift the
 * moment a mod is added, removed or updated, which would silently turn every saved construct into
 * a different set of blocks. So the save format stores the block's registry name plus its property
 * values as text, and reports a clear error rather than loading something wrong.
 */
public final class BlockStateSerializer {
    /**
     * Name to block cache. Loading a 4096-block construct would otherwise walk the entire block
     * registry once per block; the registry is fixed after startup, so one map is enough.
     */
    private static volatile Map<Identifier, Block> blocksByName;

    private BlockStateSerializer() {}

    /** Drops the cache so a resource or registry reload cannot leave stale entries behind. */
    public static void invalidateCache() {
        blocksByName = null;
    }

    public static void write(DataOutput out, BlockState state) throws IOException {
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        out.writeUTF(key == null ? "minecraft:air" : key.toString());

        Collection<Property<?>> properties = state.getProperties();
        out.writeInt(properties.size());
        for (Property<?> property : properties) {
            out.writeUTF(property.getName());
            out.writeUTF(valueName(property, state));
        }
    }

    /**
     * @return the decoded state, or {@code null} when the block no longer exists in the registry
     *         so the caller can report which block was dropped instead of substituting air.
     */
    public static BlockState read(DataInput in) throws IOException {
        String blockName = in.readUTF();
        int propertyCount = Math.max(0, in.readInt());

        Block block = resolveBlock(blockName);
        BlockState state = block == null ? null : block.defaultBlockState();

        for (int i = 0; i < propertyCount; i++) {
            String propertyName = in.readUTF();
            String propertyValue = in.readUTF();
            if (state == null) {
                continue; // still consume the stream so the next block reads correctly
            }
            Property<?> property = block.getStateDefinition().getProperty(propertyName);
            if (property != null) {
                state = applyValue(state, property, propertyValue);
            }
        }
        return state;
    }

    private static Block resolveBlock(String blockName) {
        Identifier id = Identifier.tryParse(blockName);
        return id == null ? null : nameLookup().get(id);
    }

    private static Map<Identifier, Block> nameLookup() {
        Map<Identifier, Block> cached = blocksByName;
        if (cached != null) {
            return cached;
        }
        Map<Identifier, Block> built = new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier key = BuiltInRegistries.BLOCK.getKey(block);
            if (key != null) {
                built.put(key, block);
            }
        }
        blocksByName = built;
        return built;
    }

    private static <T extends Comparable<T>> BlockState applyValue(BlockState state, Property<T> property,
                                                                  String rawValue) {
        Optional<T> parsed = property.getValue(rawValue);
        // An unparseable value means the block changed its property set between versions. Keeping
        // the default for that one property is far better than discarding the whole block.
        return parsed.map(value -> state.setValue(property, value)).orElse(state);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String valueName(Property<T> property, BlockState state) {
        return property.getName((T) state.getValue(property));
    }
}
