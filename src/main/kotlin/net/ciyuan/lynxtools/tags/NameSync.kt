package net.ciyuan.lynxtools.tags

import net.ciyuan.lynxtools.Runtime
import net.ciyuan.lynxtools.protocol.TagUpdate
import net.ciyuan.lynxtools.protocol.TagSpan
import net.luckperms.api.LuckPermsProvider
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

class NameSync(private val app:Runtime) {
    private val pending=linkedSetOf<UUID>()
    private val rendered=mutableMapOf<UUID,List<TagSpan>>()
    private val seen=mutableMapOf<UUID,MutableMap<UUID,List<TagSpan>>>()
    private val invalid=mutableSetOf<UUID>()
    fun dirty(id:UUID) { pending.add(id) }
    fun login(p:ServerPlayer) { seen.remove(p.uuid); pending.addAll(app.server.playerList.players.map { it.uuid }) }
    fun logout(p:ServerPlayer) {
        pending.remove(p.uuid); rendered.remove(p.uuid); invalid.remove(p.uuid); seen.remove(p.uuid)
        for(viewer in app.server.playerList.players) if(viewer!==p && viewer.connection.hasChannel(TagUpdate.TYPE)) {
            PacketDistributor.sendToPlayer(viewer,TagUpdate(p.uuid,emptyList())); seen[viewer.uuid]?.remove(p.uuid)
        }
    }
    fun reset() {
        for(viewer in app.server.playerList.players) if(viewer.connection.hasChannel(TagUpdate.TYPE))
            rendered.keys.forEach { PacketDistributor.sendToPlayer(viewer,TagUpdate(it,emptyList())) }
        seen.clear(); rendered.clear(); pending.clear(); invalid.clear()
        if(app.config.bool("nametags.enabled")) pending.addAll(app.server.playerList.players.map { it.uuid })
    }
    fun tick() {
        if(!app.config.bool("nametags.enabled")) return
        if(app.tick % app.config.int("nametags.refresh_ticks")==0L) pending.addAll(app.server.playerList.players.map { it.uuid })
        val viewers=app.server.playerList.players.filter { it.connection.hasChannel(TagUpdate.TYPE) }
        if(viewers.isEmpty()) return
        repeat(app.config.int("nametags.updates_per_tick")) {
            val id=pending.firstOrNull() ?: return
            pending.remove(id)
            val p=app.server.playerList.getPlayer(id) ?: return@repeat
            val api=LuckPermsProvider.get()
            val user=api.userManager.getUser(id) ?: return@repeat
            val meta=user.cachedData.getMetaData(api.contextManager.getQueryOptions(p))
            val spans=try {
                TagFormatting.format(meta.prefix ?: "",p.gameProfile.name(),meta.suffix ?: "",app.config.string("nametags.name_format"),app.config.int("nametags.max_characters"))
                    .also { invalid.remove(id) }
            } catch(e:Exception) {
                if(invalid.add(id)) Runtime.LOG.warn("Invalid LuckPerms nametag format uuid={}; using plain username",id,e)
                listOf(TagSpan(p.gameProfile.name(),0xFFFFFF,0))
            }
            rendered[id]=spans
            for(viewer in viewers) {
                val cache=seen.getOrPut(viewer.uuid) { mutableMapOf() }
                if(cache[id]!=spans) { PacketDistributor.sendToPlayer(viewer,TagUpdate(id,spans)); cache[id]=spans }
            }
        }
    }
}
