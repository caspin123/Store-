package com.astra.physics.ship;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Construct-local snapshot of a vanilla or modded BlockEntity.
 *
 * <p>ASTRA keeps the original NBT so nothing is discarded during assembly, and mirrors
 * {@link Container} contents into a {@link SimpleContainer} so chests, barrels, furnaces and
 * hoppers stay usable while the construct is moving. Running the BlockEntity's real world tick
 * is a later ShipLevel milestone.
 *
 * <h2>Item safety</h2>
 * The mirror is padded up to the next multiple of nine because the chest UI can only display
 * whole rows. Earlier builds padded the mirror but only ever read back {@code logicalSlots}
 * entries, so anything a player placed into a padding slot — or anything held in a modded
 * container with more than 54 slots — was destroyed on break. The mirror now covers every
 * source slot and {@link #copyContents()} returns the whole thing, so a round trip through a
 * construct can no longer lose or duplicate an item.
 */
public final class ConstructBlockEntityData {
    /** Widest chest-style UI Minecraft can show. Larger mirrors keep their tail slots in NBT. */
    public static final int MAX_DISPLAYABLE_SLOTS = 54;

    public enum Kind {
        CHEST,
        FURNACE,
        HOPPER,
        GENERIC_CONTAINER,
        DATA_ONLY
    }

    private final Kind kind;
    private final String sourceClass;
    private final CompoundTag originalNbt;
    private final SimpleContainer inventory;
    private final int logicalSlots;

    private ConstructBlockEntityData(Kind kind, String sourceClass, CompoundTag originalNbt,
                                     SimpleContainer inventory, int logicalSlots) {
        this.kind = kind;
        this.sourceClass = sourceClass;
        this.originalNbt = originalNbt == null ? new CompoundTag() : originalNbt.copy();
        this.inventory = inventory;
        this.logicalSlots = logicalSlots;
    }

    public static ConstructBlockEntityData capture(BlockEntity blockEntity, ServerLevel level) {
        return fromBlockEntity(blockEntity, blockEntity.saveWithFullMetadata(level.registryAccess()));
    }

    public static ConstructBlockEntityData empty(BlockEntity blockEntity) {
        return fromBlockEntity(blockEntity, new CompoundTag());
    }

    private static ConstructBlockEntityData fromBlockEntity(BlockEntity blockEntity, CompoundTag nbt) {
        Kind kind = classify(blockEntity);
        if (blockEntity instanceof Container source) {
            int logicalSize = Math.max(0, source.getContainerSize());
            SimpleContainer mirror = new SimpleContainer(paddedSizeFor(logicalSize));
            for (int slot = 0; slot < logicalSize; slot++) {
                mirror.setItem(slot, source.getItem(slot).copy());
            }
            return new ConstructBlockEntityData(kind, blockEntity.getClass().getName(), nbt, mirror, logicalSize);
        }
        return new ConstructBlockEntityData(kind, blockEntity.getClass().getName(), nbt, null, 0);
    }

    /** Rounds up to whole rows of nine so the mirror can back a chest menu without gaps. */
    private static int paddedSizeFor(int logicalSize) {
        return Math.max(9, ((Math.max(1, logicalSize) + 8) / 9) * 9);
    }

    private static Kind classify(BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity) return Kind.CHEST;
        if (blockEntity instanceof AbstractFurnaceBlockEntity) return Kind.FURNACE;
        if (blockEntity instanceof HopperBlockEntity) return Kind.HOPPER;
        if (blockEntity instanceof Container) return Kind.GENERIC_CONTAINER;
        return Kind.DATA_ONLY;
    }

    public Kind kind() { return kind; }
    public String sourceClass() { return sourceClass; }
    public CompoundTag originalNbt() { return originalNbt.copy(); }
    public boolean hasInventory() { return inventory != null; }
    public SimpleContainer inventory() { return inventory; }

    /** Slot count of the real BlockEntity this snapshot came from. */
    public int logicalSlots() { return logicalSlots; }

    /** Rows the chest UI should show, clamped to what the vanilla menu types support. */
    public int displayRows() {
        if (inventory == null) return 0;
        int rows = Math.min(inventory.getContainerSize(), MAX_DISPLAYABLE_SLOTS) / 9;
        return Math.max(1, Math.min(6, rows));
    }

    /** True when the mirror holds more slots than a chest menu can display. */
    public boolean hasHiddenSlots() {
        return inventory != null && inventory.getContainerSize() > MAX_DISPLAYABLE_SLOTS;
    }

    /**
     * Every stack held by this snapshot, including padding slots and slots past the 54 a chest
     * menu can show. Callers use this when the block is broken, so nothing is ever left behind.
     */
    public ItemStack[] copyContents() {
        if (inventory == null) {
            return new ItemStack[0];
        }
        ItemStack[] result = new ItemStack[inventory.getContainerSize()];
        for (int i = 0; i < result.length; i++) {
            result[i] = inventory.getItem(i).copy();
        }
        return result;
    }

    /**
     * Pushes the mirror back into a freshly placed BlockEntity during disassembly.
     *
     * @return stacks that did not fit — because the player used a padding slot, or the mirror is
     *         wider than the real container — for the caller to drop in the world.
     */
    public List<ItemStack> restoreInto(Container target) {
        List<ItemStack> overflow = new ArrayList<>();
        if (inventory == null) {
            return overflow;
        }

        int restorable = Math.min(target.getContainerSize(), Math.min(logicalSlots, inventory.getContainerSize()));
        for (int slot = 0; slot < restorable; slot++) {
            target.setItem(slot, inventory.getItem(slot).copy());
        }
        for (int slot = restorable; slot < inventory.getContainerSize(); slot++) {
            ItemStack leftover = inventory.getItem(slot).copy();
            if (!leftover.isEmpty()) {
                overflow.add(leftover);
            }
        }
        target.setChanged();
        return overflow;
    }

    // ------------------------------------------------------------- persistence

    /**
     * Writes this snapshot to the construct save file.
     *
     * <p>The NBT blobs are embedded as opaque byte payloads so the save format never has to
     * parse a modded BlockEntity's tags — it only has to hand them back untouched on load.
     */
    public void write(DataOutput out, HolderLookup.Provider registries) throws IOException {
        out.writeUTF(kind.name());
        out.writeUTF(sourceClass == null ? "" : sourceClass);
        out.writeInt(logicalSlots);

        writeTag(out, originalNbt);

        out.writeBoolean(inventory != null);
        if (inventory != null) {
            out.writeInt(inventory.getContainerSize());
            var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                out.writeBoolean(!stack.isEmpty());
                if (!stack.isEmpty()) {
                    Tag encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                    if (!(encoded instanceof CompoundTag compound)) {
                        throw new IOException("ItemStack codec produced non-compound NBT for slot " + slot);
                    }
                    writeTag(out, compound);
                }
            }
        }
    }

    public static ConstructBlockEntityData read(DataInput in, HolderLookup.Provider registries) throws IOException {
        Kind kind = parseKind(in.readUTF());
        String sourceClass = in.readUTF();
        int logicalSlots = in.readInt();

        CompoundTag originalNbt = readTag(in);

        SimpleContainer inventory = null;
        if (in.readBoolean()) {
            int size = Math.max(1, Math.min(in.readInt(), 8192));
            inventory = new SimpleContainer(size);
            var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            for (int slot = 0; slot < size; slot++) {
                if (in.readBoolean()) {
                    CompoundTag stackTag = readTag(in);
                    ItemStack stack = ItemStack.CODEC.parse(ops, stackTag).getOrThrow();
                    inventory.setItem(slot, stack);
                }
            }
        }
        return new ConstructBlockEntityData(kind, sourceClass, originalNbt, inventory, logicalSlots);
    }

    private static Kind parseKind(String name) {
        for (Kind candidate : Kind.values()) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        return Kind.DATA_ONLY;
    }

    private static void writeTag(DataOutput out, CompoundTag tag) throws IOException {
        NbtIo.write(tag, out);
    }

    private static CompoundTag readTag(DataInput in) throws IOException {
        return NbtIo.read(in, NbtAccounter.unlimitedHeap());
    }
}
