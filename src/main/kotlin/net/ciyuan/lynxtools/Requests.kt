package net.ciyuan.lynxtools

import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

class Requests(private val app:Runtime) {
    private data class Request(val id:String,val sender:UUID,val senderName:String,val target:UUID,val targetName:String,val expires:Long)
    private val pending=linkedMapOf<String,Request>()
    private val cooldown=mutableMapOf<UUID,Long>()
    fun suggestions(p:ServerPlayer)=pending.values.filter { it.target==p.uuid && it.expires>app.tick }.map { it.senderName }
    fun send(p:ServerPlayer,target:ServerPlayer) {
        if(p.uuid==target.uuid) { app.messages.send(p,"tpr_self","error"); return }
        if(!app.teleports.state(p) || !app.teleports.state(target)) { app.messages.send(p,"state","error"); return }
        val left=(cooldown[p.uuid] ?: 0)-app.tick
        if(left>0) { app.messages.send(p,"cooldown","warning","seconds" to ((left+19)/20).toString()); return }
        val own=pending.values.filter { it.sender==p.uuid }
        val retained=pending.values.filter { it.sender!=p.uuid }
        if(retained.size>=app.config.int("requests.max_pending") || retained.count { it.target==target.uuid }>=app.config.int("requests.max_incoming")) {
            app.messages.send(p,"tpr_limit","warning"); return
        }
        own.forEach { remove(it,"tpr_cancelled") }
        app.teleports.cancel(p.uuid,"cancel_repeat")
        val seconds=app.config.int("requests.timeout_seconds")
        val r=Request(UUID.randomUUID().toString(),p.uuid,p.gameProfile.name(),target.uuid,target.gameProfile.name(),app.tick+seconds*20L)
        pending[r.id]=r
        cooldown[p.uuid]=app.tick+app.config.int("requests.cooldown_seconds")*20L
        app.messages.send(p,"tpr_sent","success","name" to r.targetName,"seconds" to seconds.toString())
        val card=app.messages.component("tpr_received","info",mapOf("name" to r.senderName,"seconds" to seconds.toString()))
            .append(Component.literal("\n  "))
        val lang=app.config.language
        fun button(key:String,tone:String,command:String,hover:String):Component = Component.literal(lang.text(key)).withStyle { style ->
            style.withColor(lang.values.getValue("${tone}_color").removePrefix("#").toInt(16)).withBold(true)
                .withClickEvent(ClickEvent.RunCommand(command))
                .withHoverEvent(HoverEvent.ShowText(Component.literal(lang.text(hover,mapOf("name" to r.senderName)))))
        }
        val separator=Component.literal(lang.text("tpr_button_separator")).withStyle { it.withColor(lang.values.getValue("info_color").removePrefix("#").toInt(16)) }
        card.append(button("tpr_button_accept","success","/tpa ${r.id}","tpr_accept_hover"))
            .append(separator).append(button("tpr_button_decline","error","/tpd ${r.id}","tpr_decline_hover"))
            .append(separator.copy()).append(button("tpr_button_ignore","warning","/tpd ${r.id} ignore","tpr_ignore_hover"))
        target.sendSystemMessage(card)
    }
    fun reply(target:ServerPlayer,selector:String?,accept:Boolean,ignore:Boolean=false) {
        val incoming=pending.values.filter { it.target==target.uuid && it.expires>app.tick }
        val r=if(selector==null) {
            if(incoming.size>1) { app.messages.send(target,"tpr_choose","warning","names" to incoming.joinToString(", ") { it.senderName }); return }
            incoming.singleOrNull()
        } else incoming.firstOrNull { it.id==selector || it.senderName.equals(selector,true) }
        if(r==null) { app.messages.send(target,"tpr_missing","warning"); return }
        val sender=app.server.playerList.getPlayer(r.sender)
        if(sender==null) { remove(r,"tpr_cancelled"); return }
        if(accept) {
            if(!app.permissions.command(sender,"tpr")) { remove(r,"tpr_cancelled"); return }
            if(!app.teleports.state(target)) { app.messages.send(target,"tpr_accept_failed","warning","name" to r.senderName); return }
            if(!app.teleports.requestTo(sender,target)) { app.messages.send(target,"tpr_accept_failed","warning","name" to r.senderName); return }
            pending.remove(r.id)
            app.messages.send(sender,"tpr_accepted_sender","success","name" to r.targetName)
            app.messages.send(target,"tpr_accepted_target","success","name" to r.senderName)
        } else {
            pending.remove(r.id)
            app.messages.send(target,if(ignore) "tpr_ignored_target" else "tpr_declined_target","info","name" to r.senderName)
            if(!ignore) app.messages.send(sender,"tpr_declined_sender","warning","name" to r.targetName)
        }
    }
    private fun remove(r:Request,message:String) {
        pending.remove(r.id)
        app.server.playerList.getPlayer(r.sender)?.let { app.messages.send(it,message,"warning","name" to r.targetName) }
        app.server.playerList.getPlayer(r.target)?.let { app.messages.send(it,message,"warning","name" to r.senderName) }
    }
    fun tick() {
        pending.values.toList().forEach { r ->
            if(app.tick>=r.expires) remove(r,"tpr_expired")
            else if(app.server.playerList.getPlayer(r.sender)==null || app.server.playerList.getPlayer(r.target)==null) remove(r,"tpr_cancelled")
        }
    }
    fun cancelFor(id:UUID) { pending.values.filter { it.sender==id || it.target==id }.toList().forEach { remove(it,"tpr_cancelled") }; cooldown.remove(id) }
    fun clear(notify:Boolean) { if(notify) pending.values.toList().forEach { remove(it,"tpr_cancelled") }; pending.clear(); cooldown.clear() }
}
