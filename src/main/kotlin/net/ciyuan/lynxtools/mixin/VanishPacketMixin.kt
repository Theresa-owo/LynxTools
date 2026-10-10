package net.ciyuan.lynxtools.mixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.netty.channel.ChannelFutureListener
import net.ciyuan.lynxtools.VanishVisibility
import net.ciyuan.lynxtools.VanishEffects
import net.ciyuan.lynxtools.ActorFeedback
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket
import net.minecraft.server.network.ServerCommonPacketListenerImpl
import net.minecraft.server.network.ServerGamePacketListenerImpl
import org.spongepowered.asm.mixin.Mixin

@Mixin(ServerCommonPacketListenerImpl::class)
abstract class VanishPacketMixin {
    @WrapMethod(method=["send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V"])
    private fun lynxSend(packet: Packet<*>, listener: ChannelFutureListener?, original: Operation<Void>) {
        val connection=(this as Any) as? ServerGamePacketListenerImpl
        if(connection==null) { original.call(packet,listener); return }
        if(packet is ClientboundSystemChatPacket && ActorFeedback.excludes(connection.player.uuid)) return
        if(VanishEffects.hiddenFrom(connection.player.uuid) && (packet is ClientboundSoundPacket || packet is ClientboundSoundEntityPacket || packet is ClientboundLevelParticlesPacket || packet is ClientboundSystemChatPacket || packet is ClientboundDisguisedChatPacket)) return
        var outgoing=packet
        if(packet is ClientboundPlayerInfoUpdatePacket) {
            val entries=packet.entries().filterNot { VanishVisibility.hidden(connection.player.uuid,it.profileId()) }
            if(entries.size!=packet.entries().size) {
                if(entries.isEmpty()) return
                outgoing=ClientboundPlayerInfoUpdatePacket(packet.actions(),emptyList())
                (outgoing as PlayerInfoAccessor).lynxEntries(entries)
            }
        }
        original.call(outgoing,listener)
    }
}
