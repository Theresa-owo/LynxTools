package net.ciyuan.lynxtools.verification

import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.types.PrefixNode
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.GameType
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID
import com.mojang.authlib.GameProfile
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.players.PlayerList
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket

class TagServerProbe {
    private var ticks=0
    private var hadPlayer=false
    private val pending=mutableMapOf<UUID,Int>()
    private var visitor:ServerPlayer?=null
    private var requestAt=-1
    init {
        NeoForge.EVENT_BUS.addListener<ServerStartedEvent> { e ->
            val w=e.server.overworld()
            for(x in 2..16) for(z in 2..16) {
                w.setBlock(BlockPos(x,199,z),Blocks.STONE.defaultBlockState(),3)
                for(y in 200..205) w.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3)
            }
            e.server.commands.performPrefixedCommand(e.server.createCommandSourceStack(),"summon minecraft:cow 8.5 200 11.5 {CustomName:{text:\"NamedCow\"},CustomNameVisible:1b,NoAI:1b,PersistenceRequired:1b}")
        }
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedInEvent> { e ->
            val p=e.entity as? ServerPlayer ?: return@addListener
            hadPlayer=true; pending[p.uuid]=ticks+40
            val user=LuckPermsProvider.get().userManager.getUser(p.uuid)!!
            user.data().clear()
            p.setGameMode(GameType.SURVIVAL)
            if(System.getProperty("lynxtools.verification.requests")=="true") requestAt=ticks+80
        }
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedOutEvent> { e ->
            val fake=visitor
            if(fake!=null && e.entity.uuid!=fake.uuid) { visitor=null; fake.level().server.playerList.remove(fake) }
        }
        NeoForge.EVENT_BUS.addListener<ServerTickEvent.Post> { e ->
            ticks++
            for((id,at) in pending.toMap()) if(ticks>=at) {
                e.server.playerList.getPlayer(id)?.let { it.teleportTo(e.server.overworld(),8.5,200.0,8.5,emptySet(),0f,0f,true) }
                pending.remove(id)
            }
            if(requestAt>=0 && ticks>=requestAt) {
                requestAt=-1
                val receiver=e.server.playerList.players.firstOrNull()
                if(receiver!=null) {
                    val p=ServerPlayer(e.server,e.server.overworld(),GameProfile(UUID.randomUUID(),"LynxVisitor"),ClientInformation.createDefault())
                    LifecycleProbe.Sink(p).markClientLoaded(); p.setNoGravity(true); p.snapTo(5.5,200.0,8.5,0f,0f)
                    @Suppress("UNCHECKED_CAST") val list=PlayerList::class.java.getDeclaredField("players").apply { isAccessible=true }.get(e.server.playerList) as MutableList<ServerPlayer>
                    @Suppress("UNCHECKED_CAST") val map=PlayerList::class.java.getDeclaredField("playersByUUID").apply { isAccessible=true }.get(e.server.playerList) as MutableMap<UUID,ServerPlayer>
                    list.add(p); map[p.uuid]=p; visitor=p
                    LuckPermsProvider.get().userManager.loadUser(p.uuid).join()
                    e.server.playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(listOf(p)))
                    p.level().addNewPlayer(p)
                    e.server.commands.performPrefixedCommand(p.createCommandSourceStack(),"tpr ${receiver.gameProfile.name()}")
                }
            }
            if((hadPlayer && e.server.playerList.players.isEmpty() && ticks>200) || ticks>6000) e.server.halt(false)
        }
    }
}
