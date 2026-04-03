package dev.stealthspec.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stealthspec.PacketUtil;
import dev.stealthspec.StealthSpec;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.EnumSet;
import java.util.List;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {
	@Shadow @Final protected ServerPlayerEntity player;
	@Shadow protected ServerWorld world;

	@WrapOperation(
		method = "changeGameMode",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/packet/Packet;)V")
	)
	private void stealthspec$sendPackets(PlayerManager playerManager, Packet<?> packet, Operation<Void> original) {
		if (this.player.isSpectator()) {
			var modifiedPacket = PacketUtil.copyPacketWithModifiedEntries(
				((PlayerListS2CPacket) packet),
				entry -> PacketUtil.cloneEntryWithGamemode(entry, GameMode.SURVIVAL)
			);

			for (ServerPlayerEntity viewer : playerManager.getPlayerList()) {
				viewer.networkHandler.sendPacket(StealthSpec.canPlayerSeeThatOtherIsSpectator(viewer, this.player) ? packet : modifiedPacket);
			}

			// Send all visible spectators to the player who just entered spectator.
			List<ServerPlayerEntity> visibleOtherSpectators = playerManager.getPlayerList().stream()
				.filter(other -> !other.equals(this.player) && other.isSpectator() && StealthSpec.canPlayerSeeThatOtherIsSpectator(this.player, other))
				.toList();

			if (!visibleOtherSpectators.isEmpty()) {
				this.player.networkHandler.sendPacket(new PlayerListS2CPacket(
					EnumSet.of(PlayerListS2CPacket.Action.UPDATE_GAME_MODE),
					visibleOtherSpectators
				));
			}
		} else {
			original.call(this.world.getServer().getPlayerManager(), packet);

			if (!StealthSpec.canSeeOtherSpectators(this.player)) {
				List<ServerPlayerEntity> pretendSurvival = playerManager.getPlayerList().stream()
					.filter(other -> !other.equals(this.player) && other.isSpectator())
					.toList();

				if (!pretendSurvival.isEmpty()) {
					PlayerListS2CPacket backToSurvivalPacket = PacketUtil.copyPacketWithModifiedEntries(
						new PlayerListS2CPacket(
							EnumSet.of(PlayerListS2CPacket.Action.UPDATE_GAME_MODE),
							pretendSurvival
						),
						entry -> PacketUtil.cloneEntryWithGamemode(entry, GameMode.SURVIVAL)
					);
					this.player.networkHandler.sendPacket(backToSurvivalPacket);
				}
			}
		}
	}
}

