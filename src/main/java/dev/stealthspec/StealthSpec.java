package dev.stealthspec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class StealthSpec implements ModInitializer {
	public static final String MOD_ID = "stealthspec";
	public static final Logger LOGGER = LoggerFactory.getLogger("Stealth-Spec");
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("stealth-spec.json");
	private static final Object LOCK = new Object();
	private static Set<String> WHITELISTED_VIEWERS = new HashSet<>();

	@Override
	public void onInitialize() {
		loadConfig();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			CommandManager.literal("stealthspec")
				.requires(src -> src.getPermissions().hasPermission(new Permission.Level(PermissionLevel.OWNERS)))
				.then(CommandManager.literal("whitelist")
					.then(CommandManager.literal("add")
						.then(CommandManager.argument("player", StringArgumentType.word())
							.executes(ctx -> addViewer(ctx, StringArgumentType.getString(ctx, "player")))))
					.then(CommandManager.literal("remove")
						.then(CommandManager.argument("player", StringArgumentType.word())
							.executes(ctx -> removeViewer(ctx, StringArgumentType.getString(ctx, "player")))))
					.then(CommandManager.literal("list").executes(StealthSpec::listViewers))
					.then(CommandManager.literal("reload").executes(StealthSpec::reloadConfig))
				)
		));
	}

	public static boolean canSeeOtherSpectators(ServerPlayerEntity viewer) {
		if (viewer.getPermissions().hasPermission(new Permission.Level(PermissionLevel.OWNERS))) return true; // OP level 4
		if (isWhitelistedViewer(viewer.getGameProfile().name())) return true;
		return false;
	}

	public static boolean canPlayerSeeThatOtherIsSpectator(ServerPlayerEntity viewer, ServerPlayerEntity other) {
		if (viewer.equals(other)) return true;
		return canSeeOtherSpectators(viewer);
	}

	public static boolean isWhitelistedViewer(String name) {
		String key = normalizeName(name);
		synchronized (LOCK) {
			return WHITELISTED_VIEWERS.contains(key);
		}
	}

	public static Set<String> getWhitelistedViewersSnapshot() {
		synchronized (LOCK) {
			return Collections.unmodifiableSet(new HashSet<>(WHITELISTED_VIEWERS));
		}
	}

	private static int addViewer(CommandContext<ServerCommandSource> ctx, String name) {
		String key = normalizeName(name);
		synchronized (LOCK) {
			boolean added = WHITELISTED_VIEWERS.add(key);
			saveConfig();
			ctx.getSource().sendFeedback(() -> Text.literal(added
				? ("Added " + name + " to Stealth-Spec viewer whitelist.")
				: (name + " is already on the Stealth-Spec viewer whitelist.")
			), true);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int removeViewer(CommandContext<ServerCommandSource> ctx, String name) {
		String key = normalizeName(name);
		synchronized (LOCK) {
			boolean removed = WHITELISTED_VIEWERS.remove(key);
			saveConfig();
			ctx.getSource().sendFeedback(() -> Text.literal(removed
				? ("Removed " + name + " from Stealth-Spec viewer whitelist.")
				: (name + " was not on the Stealth-Spec viewer whitelist.")
			), true);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int listViewers(CommandContext<ServerCommandSource> ctx) {
		Set<String> snapshot = getWhitelistedViewersSnapshot();
		ctx.getSource().sendFeedback(() -> Text.literal("Stealth-Spec viewer whitelist (" + snapshot.size() + "): " + snapshot), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int reloadConfig(CommandContext<ServerCommandSource> ctx) {
		loadConfig();
		ctx.getSource().sendFeedback(() -> Text.literal("Reloaded Stealth-Spec config from disk."), true);
		return Command.SINGLE_SUCCESS;
	}

	private static void loadConfig() {
		synchronized (LOCK) {
			if (!Files.exists(CONFIG_PATH)) {
				WHITELISTED_VIEWERS = new HashSet<>();
				saveConfig();
				return;
			}
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
				Config cfg = GSON.fromJson(reader, Config.class);
				Set<String> next = new HashSet<>();
				if (cfg != null && cfg.viewerWhitelist != null) {
					for (String n : cfg.viewerWhitelist) next.add(normalizeName(n));
				}
				WHITELISTED_VIEWERS = next;
			} catch (Exception e) {
				LOGGER.error("Failed to load {}. Using empty whitelist.", CONFIG_PATH, e);
				WHITELISTED_VIEWERS = new HashSet<>();
			}
		}
	}

	private static void saveConfig() {
		synchronized (LOCK) {
			try {
				Files.createDirectories(CONFIG_PATH.getParent());
				try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
					Config cfg = new Config();
					cfg.viewerWhitelist = WHITELISTED_VIEWERS.stream().sorted().toList();
					GSON.toJson(cfg, writer);
				}
			} catch (IOException e) {
				LOGGER.error("Failed to save {}.", CONFIG_PATH, e);
			}
		}
	}

	private static String normalizeName(String name) {
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	private static final class Config {
		java.util.List<String> viewerWhitelist = java.util.List.of();
	}
}

