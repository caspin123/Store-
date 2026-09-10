package com.astra.physics.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Every player-facing string goes through here.
 *
 * <p>Earlier builds used {@code Component.literal("ASTRA: ...")} everywhere, which meant the
 * Arabic language file could only ever translate the seven block names while all feedback,
 * warnings and errors stayed English. Routing through translation keys makes the whole mod
 * localisable, and keeps the "ASTRA" prefix and colour consistent in one place.
 */
public final class AstraText {
    private static final String PREFIX = "message.astra_physics.";

    private AstraText() {}

    /** Neutral feedback, e.g. selection progress. */
    public static MutableComponent info(String key, Object... args) {
        return prefixed(key, ChatFormatting.AQUA, args);
    }

    /** A completed action, e.g. a construct was assembled. */
    public static MutableComponent success(String key, Object... args) {
        return prefixed(key, ChatFormatting.GREEN, args);
    }

    /** A rejected action. Always explains what to do instead. */
    public static MutableComponent warning(String key, Object... args) {
        return prefixed(key, ChatFormatting.RED, args);
    }

    /** A bare translated component with no prefix, for tooltips and menu titles. */
    public static MutableComponent plain(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    public static void sendActionBar(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    public static void sendChat(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }

    private static MutableComponent prefixed(String key, ChatFormatting colour, Object... args) {
        return Component.translatable("message.astra_physics.prefix")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.translatable(PREFIX + key, args).withStyle(colour));
    }
}
