package dev.stealthspec;

import dev.stealthspec.mixin.PlayerListS2CPacketAccessor;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.function.Function;

public final class PacketUtil {
	private PacketUtil() {}

	public static PlayerListS2CPacket copyPacketWithModifiedEntries(
		PlayerListS2CPacket packet,
		Function<PlayerListS2CPacket.Entry, PlayerListS2CPacket.Entry> mapper
	) {
		PlayerListS2CPacket newPacket = new PlayerListS2CPacket(packet.getActions(), List.of());
		((PlayerListS2CPacketAccessor) newPacket).stealthspec$setEntries(packet.getEntries().stream().map(mapper).toList());
		return newPacket;
	}

	public static PlayerListS2CPacket.Entry cloneEntryWithGamemode(PlayerListS2CPacket.Entry entry, GameMode newGameMode) {
		return new PlayerListS2CPacket.Entry(
			entry.profileId(),
			entry.profile(),
			entry.listed(),
			entry.latency(),
			newGameMode,
			entry.displayName(),
			entry.showHat(),
			entry.listOrder(),
			entry.chatSession()
		);
	}
}

