package net.ciyuan.lynxtools.verification

import com.mojang.authlib.GameProfile
import io.netty.channel.ChannelFutureListener
import io.netty.channel.embedded.EmbeddedChannel
import net.ciyuan.lynxtools.*
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.Node
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.*
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.server.players.PlayerList
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent
import org.slf4j.LoggerFactory
import java.util.UUID

/** Native transformed packet listener + entities, recording at Connection, no claimed graphical client. */
class VanishProbe {
    private class Wire: Connection(PacketFlow.SERVERBOUND) {
        val packets=mutableListOf<Packet<*>>()
        init {
            Connection::class.java.getDeclaredField("channel").apply { isAccessible=true }.set(this,EmbeddedChannel())
            Connection::class.java.getDeclaredField("address").apply { isAccessible=true }.set(this,java.net.InetSocketAddress("127.0.0.1",0))
        }
        override fun send(packet: Packet<*>, listener: ChannelFutureListener?, flush: Boolean) { packets.add(packet) }
    }
    init { NeoForge.EVENT_BUS.addListener<ServerStartedEvent> { run() } }
    private fun run() {
        val app=ProbeState.app; val server=app.server; val level=server.overworld()
        @Suppress("UNCHECKED_CAST")
        val players=PlayerList::class.java.getDeclaredField("players").apply { isAccessible=true }.get(server.playerList) as MutableList<ServerPlayer>
        @Suppress("UNCHECKED_CAST")
        val byId=PlayerList::class.java.getDeclaredField("playersByUUID").apply { isAccessible=true }.get(server.playerList) as MutableMap<UUID,ServerPlayer>
        val made=mutableListOf<ServerPlayer>()
        fun player(name: String): Pair<ServerPlayer,Wire> {
            val p=ServerPlayer(server,level,GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault())
            val wire=Wire()
            ServerGamePacketListenerImpl(server,wire,p,CommonListenerCookie.createInitial(p.gameProfile,false)).markClientLoaded()
            p.snapTo(8.5,200.0,8.5,0f,0f); p.setNoGravity(true)
            players.add(p); byId[p.uuid]=p; made.add(p)
            level.addNewPlayer(p)
            LuckPermsProvider.get().userManager.loadUser(p.uuid).join()
            return p to wire
        }
        fun permission(p: ServerPlayer, value: Boolean) {
            val user=LuckPermsProvider.get().userManager.getUser(p.uuid)!!
            user.data().clear(); user.data().add(Node.builder("lynxtools.command.vanish").value(value).build())
        }
        try {
            val (actor,a)=player("VanishActor"); val (viewer,v)=player("VanishViewer"); val (staff,s)=player("VanishStaff")
            permission(actor,true); permission(staff,true); permission(viewer,false)
            app.vanishing.refresh()
            fun command(text: String,p: ServerPlayer=actor)=server.commands.performPrefixedCommand(p.createCommandSourceStack(),text)
            fun messages(w: Wire)=w.packets.filterIsInstance<ClientboundSystemChatPacket>().map { it.content().string }
            fun clear() { a.packets.clear(); v.packets.clear(); s.packets.clear() }
            command("vanish",viewer); check(messages(v).any { it.contains("权限") })
            actor.setGameMode(GameType.SURVIVAL); clear(); command("vanish")
            check(!app.vanishing.active(actor) && messages(a).any { it.contains("创造模式") })
            LuckPermsProvider.get().userManager.getUser(actor.uuid)!!.data().add(Node.builder("lynxtools.command.gm").value(true).build())
            clear(); command("gm c")
            check(actor.isCreative && messages(a).any { it.contains("游戏模式已设为创造") })
            check(messages(v).isEmpty() && messages(s).isEmpty())
            val user=LuckPermsProvider.get().userManager.getUser(actor.uuid)!!
            user.data().add(net.luckperms.api.node.types.PrefixNode.builder("[VIP]",100).build())
            user.data().add(net.luckperms.api.node.types.SuffixNode.builder("[❤]",100).build())
            val label=net.minecraft.network.chat.Component.literal("[VIP] VanishActor [❤]").withColor(0xFFB7D5)
            val joined=app.messages.presence(true,label)
            check(joined.string=="[+] [VIP] VanishActor [❤]")
            var markers=0
            joined.visit(net.minecraft.network.chat.FormattedText.StyledContentConsumer<Unit> { style,text ->
                if(text in listOf("[","+","]")) {
                    check(style.isBold); check(style.color!!.value==if(text=="+") 0x55FF55 else 0x555555); markers++
                }
                java.util.Optional.empty()
            },net.minecraft.network.chat.Style.EMPTY)
            check(markers==3)
            clear(); check(PresenceHooks.replace(actor,false))
            check(messages(v)==listOf("[-] [VIP] VanishActor [❤]"))
            clear(); command("vanish")
            check(app.vanishing.active(actor)); check(!actor.broadcastToPlayer(viewer)); check(actor.broadcastToPlayer(staff))
            check(messages(a).any { it.contains("已开启隐身") }); check(messages(v).isEmpty() && messages(s).isEmpty())
            check(v.packets.filterIsInstance<ClientboundPlayerInfoRemovePacket>().any { actor.uuid in it.profileIds() })
            clear(); check(PresenceHooks.replace(actor,false))
            check(messages(v).isEmpty() && messages(s)==listOf("[-] [VIP] VanishActor [❤]"))
            LoggerFactory.getLogger("LynxToolsPresence").info("PRESENCE_PROBE_PASSED: bold grey brackets, green plus, complete LP label, single quit message, vanished quit hidden from unauthorised viewer")
            val info=ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(listOf(actor,viewer,staff))
            clear(); viewer.connection.send(info); staff.connection.send(info)
            check(v.packets.filterIsInstance<ClientboundPlayerInfoUpdatePacket>().single().entries().none { it.profileId()==actor.uuid })
            check(s.packets.filterIsInstance<ClientboundPlayerInfoUpdatePacket>().single().entries().first { it.profileId()==actor.uuid }.listed())
            check(info.entries().first { it.profileId()==actor.uuid }.listed()) // Shared broadcast packet was not mutated.
            check("VanishActor" !in viewer.createCommandSourceStack().onlinePlayerNames)
            check("VanishActor" in staff.createCommandSourceStack().onlinePlayerNames)
            val unknown=UUID.randomUUID(); check(VanishVisibility.hidden(unknown,actor.uuid))
            clear()
            VanishEffects.scoped(actor) {
                val particle=ClientboundLevelParticlesPacket(ParticleTypes.FLAME,false,false,8.5,200.0,8.5,0f,0f,0f,0f,1)
                val sound=ClientboundSoundPacket(net.minecraft.core.Holder.direct(SoundEvents.GENERIC_SPLASH),SoundSource.PLAYERS,8.5,200.0,8.5,1f,1f,1L)
                viewer.connection.send(particle); viewer.connection.send(sound)
                staff.connection.send(particle); staff.connection.send(sound)
            }
            check(v.packets.none { it is ClientboundLevelParticlesPacket || it is ClientboundSoundPacket })
            check(s.packets.any { it is ClientboundLevelParticlesPacket } && s.packets.any { it is ClientboundSoundPacket })
            check(!VanishEffects.hiddenFrom(viewer.uuid))
            clear()
            ActorFeedback.only(actor.uuid) {
                val message=ClientboundSystemChatPacket(net.minecraft.network.chat.Component.literal("actor-only"),false)
                actor.connection.send(message); viewer.connection.send(message); staff.connection.send(message)
            }
            check(messages(a)==listOf("actor-only") && messages(v).isEmpty() && messages(s).isEmpty())
            clear()
            val chat=net.minecraft.network.chat.PlayerChatMessage.unsigned(actor.uuid,"visible public chat")
            val chatType=net.minecraft.network.chat.ChatType.bind(net.minecraft.network.chat.ChatType.CHAT,actor)
            viewer.connection.sendPlayerChatMessage(chat,chatType); staff.connection.sendPlayerChatMessage(chat,chatType)
            check(v.packets.none { it is ClientboundDisguisedChatPacket || it is ClientboundPlayerChatPacket })
            check(s.packets.any { it is ClientboundPlayerChatPacket })
            val pos=BlockPos(8,199,8); level.setBlock(pos,Blocks.CHEST.defaultBlockState(),3)
            val chest=level.getBlockEntity(pos) as ChestBlockEntity
            chest.startOpen(actor); check(ChestBlockEntity.getOpenCount(level,pos)==0)
            chest.startOpen(viewer); check(ChestBlockEntity.getOpenCount(level,pos)==1)
            chest.stopOpen(actor); check(ChestBlockEntity.getOpenCount(level,pos)==1)
            chest.stopOpen(viewer); check(ChestBlockEntity.getOpenCount(level,pos)==0)
            level.setBlock(pos,Blocks.SHULKER_BOX.defaultBlockState(),3)
            val shulker=level.getBlockEntity(pos) as ShulkerBoxBlockEntity
            shulker.startOpen(actor); shulker.stopOpen(actor)
            check(ShulkerBoxBlockEntity::class.java.getDeclaredField("openCount").apply { isAccessible=true }.getInt(shulker)==0)
            permission(viewer,true); app.vanishing.refresh(); check(actor.broadcastToPlayer(viewer))
            permission(viewer,false); app.vanishing.refresh(); check(!actor.broadcastToPlayer(viewer))
            actor.setGameMode(GameType.SURVIVAL); app.vanishing.refresh(); check(!app.vanishing.active(actor)); check(actor.broadcastToPlayer(viewer))
            actor.setGameMode(GameType.CREATIVE); command("vanish"); permission(actor,false); app.vanishing.refresh(); check(!app.vanishing.active(actor))
            LoggerFactory.getLogger("LynxToolsVanish").info("VANISH_PROBE_PASSED: creative-only, actor-only gm/vanish feedback, permission transitions, native broadcast visibility, per-viewer TAB without shared packet mutation, player-name suggestions, scoped sound/particles, silent chest/shulker counts")
        } finally {
            made.forEach { app.vanishing.logout(it); players.remove(it); byId.remove(it.uuid); it.connection.disconnect(net.minecraft.network.chat.Component.literal("probe complete")) }
            app.vanishing.refresh()
        }
    }
}
