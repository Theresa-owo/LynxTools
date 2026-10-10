package net.ciyuan.lynxtools

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

class PresenceMessages(private val app:Runtime) {
    fun onNative(player:ServerPlayer,joining:Boolean):Boolean {
        if(!app.config.module("presence")) return false
        if(app.stopping || !app.config.bool(if(joining) "presence.join" else "presence.quit")) return true
        if(joining) {
            // Vanilla broadcasts before adding the player and firing the login event. Wait for current LP contexts.
            app.defer { if(app.online(player)) broadcast(player,true) }
        } else broadcast(player,false) // Must precede LP unload and vanish logout cleanup.
        return true
    }
    private fun broadcast(player:ServerPlayer,joining:Boolean) {
        val name=Component.empty()
        for(span in app.names.label(player)) name.append(Component.literal(span.text).withStyle { style ->
            style.withColor(span.rgb).withBold(span.flags and 1!=0).withItalic(span.flags and 2!=0)
                .withUnderlined(span.flags and 4!=0).withStrikethrough(span.flags and 8!=0)
        })
        val message=app.messages.presence(joining,name)
        for(viewer in app.server.playerList.players) if(app.vanishing.visible(viewer,player)) viewer.sendSystemMessage(message)
        Runtime.LOG.info("Player {} uuid={} name={}",if(joining) "joined" else "left",player.uuid,player.gameProfile.name())
    }
}

object PresenceHooks {
    var handler:((ServerPlayer,Boolean)->Boolean)?=null
    fun replace(player:ServerPlayer,joining:Boolean):Boolean=try { handler?.invoke(player,joining) ?: false }
        catch(e:Exception) { Runtime.LOG.error("Connection announcement failed uuid={} joining={}; retaining vanilla message",player.uuid,joining,e); false }
}
