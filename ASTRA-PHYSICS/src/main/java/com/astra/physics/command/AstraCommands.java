package com.astra.physics.command;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec3;

import com.astra.physics.config.AstraConfig;
import com.astra.physics.ship.PhysicsConstruct;
import com.astra.physics.ship.PhysicsConstructManager;
import com.astra.physics.util.AstraText;

/**
 * Administration commands.
 *
 * <p>Assembled constructs are not world blocks and not entities, so none of the usual tools can
 * see or clean them up. Without a command an operator has no way to find a runaway construct,
 * turn one back into blocks, or delete one that ended up somewhere unreachable.
 */
public final class AstraCommands {
    private static final int OPERATOR_LEVEL = 2;
    /** How far from the caller {@code /astra} looks when no construct id is given. */
    private static final double TARGET_SEARCH_RANGE = 64.0;

    private AstraCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> build(dispatcher));
    }

    private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("astra")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("list").executes(AstraCommands::list))
                .then(Commands.literal("info").executes(context -> info(context, null))
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(context -> info(context, StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("disassemble").executes(context -> disassemble(context, null))
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(context -> disassemble(context, StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(context -> remove(context, StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("save").executes(AstraCommands::save))
                .then(Commands.literal("reload").executes(AstraCommands::reload)));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Collection<PhysicsConstruct> constructs = PhysicsConstructManager.constructsIn(level);

        if (constructs.isEmpty()) {
            source.sendSuccess(() -> AstraText.info("command.list.empty"), false);
            return 0;
        }

        source.sendSuccess(() -> AstraText.info("command.list.header", constructs.size()), false);
        for (PhysicsConstruct construct : constructs) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  %s  %d blocks  @ %.1f %.1f %.1f",
                    shortId(construct.id()), construct.blockCount(),
                    construct.x(), construct.y(), construct.z()))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return constructs.size();
    }

    private static int info(CommandContext<CommandSourceStack> context, String rawId) {
        CommandSourceStack source = context.getSource();
        PhysicsConstruct construct = resolve(source, rawId);
        if (construct == null) {
            return 0;
        }

        source.sendSuccess(() -> AstraText.info("command.info.header", shortId(construct.id())), false);
        line(source, "command.info.position", String.format(Locale.ROOT,
                "%.2f %.2f %.2f", construct.x(), construct.y(), construct.z()));
        line(source, "command.info.velocity", String.format(Locale.ROOT,
                "%.4f %.4f %.4f", construct.vx(), construct.vy(), construct.vz()));
        line(source, "command.info.size", construct.sizeX() + "x" + construct.sizeY() + "x" + construct.sizeZ());
        line(source, "command.info.blocks", String.valueOf(construct.blockCount()));
        line(source, "command.info.mass", String.format(Locale.ROOT, "%.1f", construct.mass()));
        line(source, "command.info.submerged", String.format(Locale.ROOT,
                "%.0f%%", construct.submergedFraction() * 100.0));
        line(source, "command.info.engine", construct.engineMode().name() + " " + construct.enginePowerPercent() + "%");
        line(source, "command.info.components", String.format(Locale.ROOT,
                "helm=%d engine=%d propeller=%d sail=%d wing=%d thruster=%d",
                construct.helmCount(), construct.engineCount(), construct.propellerCount(),
                construct.sailCount(), construct.wingCount(), construct.thrusterCount()));
        return 1;
    }

    private static int disassemble(CommandContext<CommandSourceStack> context, String rawId) {
        CommandSourceStack source = context.getSource();
        PhysicsConstruct construct = resolve(source, rawId);
        if (construct == null) {
            return 0;
        }

        int placed = PhysicsConstructManager.disassemble(
                source.getLevel(), construct.id(), source.getPlayer());
        if (placed < 0) {
            source.sendFailure(AstraText.warning("command.not_found"));
            return 0;
        }
        source.sendSuccess(() -> AstraText.success("command.disassemble.done", placed), true);
        return placed;
    }

    private static int remove(CommandContext<CommandSourceStack> context, String rawId) {
        CommandSourceStack source = context.getSource();
        PhysicsConstruct construct = resolve(source, rawId);
        if (construct == null) {
            return 0;
        }

        // Deliberately separate from disassemble: this deletes the blocks instead of returning
        // them, so it is only ever what an operator explicitly asked for.
        int blocks = construct.blockCount();
        PhysicsConstructManager.deleteConstruct(source.getLevel(), construct.id());
        source.sendSuccess(() -> AstraText.warning("command.remove.done", shortId(construct.id()), blocks), true);
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        PhysicsConstructManager.saveAll(source.getServer());
        source.sendSuccess(() -> AstraText.success("command.save.done"), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        AstraConfig.load();
        context.getSource().sendSuccess(() -> AstraText.success("command.reload.done"), true);
        return 1;
    }

    // --------------------------------------------------------------- helpers

    private static void line(CommandSourceStack source, String key, String value) {
        source.sendSuccess(() -> Component.literal("  ")
                .append(AstraText.plain(key).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": " + value).withStyle(ChatFormatting.WHITE)), false);
    }

    /**
     * Resolves an explicit id, or falls back to the construct nearest the caller. The id may be
     * abbreviated to any unique prefix so operators do not have to type a full UUID.
     */
    private static PhysicsConstruct resolve(CommandSourceStack source, String rawId) {
        ServerLevel level = source.getLevel();

        if (rawId == null || rawId.isBlank()) {
            ServerPlayer player = source.getPlayer();
            Vec3 origin = player != null ? player.getEyePosition() : source.getPosition();
            PhysicsConstruct nearest = PhysicsConstructManager.nearestTo(level, origin, TARGET_SEARCH_RANGE);
            if (nearest == null) {
                source.sendFailure(AstraText.warning("command.no_target", (int) TARGET_SEARCH_RANGE));
            }
            return nearest;
        }

        String needle = rawId.toLowerCase(Locale.ROOT);
        PhysicsConstruct match = null;
        int matches = 0;
        for (PhysicsConstruct construct : PhysicsConstructManager.constructsIn(level)) {
            if (construct.id().toString().toLowerCase(Locale.ROOT).startsWith(needle)) {
                match = construct;
                matches++;
            }
        }

        if (matches == 0) {
            source.sendFailure(AstraText.warning("command.not_found"));
            return null;
        }
        if (matches > 1) {
            source.sendFailure(AstraText.warning("command.ambiguous", matches));
            return null;
        }
        return match;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
