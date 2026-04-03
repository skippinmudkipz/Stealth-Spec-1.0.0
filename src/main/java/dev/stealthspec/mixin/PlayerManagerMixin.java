package dev.stealthspec.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stealthspec.PacketUtil;
import dev.stealthspec.StealthSpec;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
	@Shadow public abstract java.util.List<ServerPlayerEntity> getPlayerList();
	@Shadow public abstract @Nullable ServerPlayerEntity getPlayer(UUID uuid);

	@WrapOperation(
		method = "onPlayerConnect",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V")
	)
	private void stealthspec$modifyJoinPackets(ServerPlayNetworkHandler instance, Packet<?> packet, Operation<Void> original, ClientConnection connection, ServerPlayerEntity joiningPlayer, ConnectedClientData clientData) {
		if (packet instanceof PlayerListS2CPacket playerList && !StealthSpec.canSeeOtherSpectators(joiningPlayer)) {
			PlayerListS2CPacket fake = PacketUtil.copyPacketWithModifiedEntries(playerList, entry -> PacketUtil.cloneEntryWithGamemode(entry, GameMode.SURVIVAL));
			original.call(instance, fake);
			return;
		}
		original.call(instance, packet);
	}

	@WrapOperation(
		method = "onPlayerConnect",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/packet/Packet;)V")
	)
	private void stealthspec$modifyBroadcastJoinPackets(PlayerManager instance, Packet<?> packet, Operation<Void> original, ClientConnection connection, ServerPlayerEntity joiningPlayer, ConnectedClientData clientData) {
		if (packet instanceof PlayerListS2CPacket playerList && !playerList.getEntries().isEmpty()) {
			PlayerListS2CPacket.Entry entry = playerList.getEntries().getFirst();
			ServerPlayerEntity other = getPlayer(entry.profileId());

			if (other != null && other.isSpectator()) {
				PlayerListS2CPacket fake = PacketUtil.copyPacketWithModifiedEntries(playerList, e -> PacketUtil.cloneEntryWithGamemode(e, GameMode.SURVIVAL));
				for (ServerPlayerEntity viewer : getPlayerList()) {
					viewer.networkHandler.sendPacket(StealthSpec.canPlayerSeeThatOtherIsSpectator(viewer, other) ? packet : fake);
				}
				return;
			}
		}

		original.call(instance, packet);
	}
}

