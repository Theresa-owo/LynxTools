package net.ciyuan.lynxtools

import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.player.Abilities
import net.neoforged.neoforge.common.NeoForgeMod
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.minecraft.world.damagesource.DamageTypes
import java.util.UUID

class Flights(private val app: Runtime) {
    private data class Grant(val actor: UUID, val other: Boolean)
    private val grants = mutableMapOf<UUID,Grant>()
    private val protection = mutableMapOf<UUID,Long>()
    private val lastNativeMode = mutableMapOf<UUID,Boolean>()
    private val id = Identifier.fromNamespaceAndPath("lynxtools","flight")
    private val modifier = AttributeModifier(id,1.0,AttributeModifier.Operation.ADD_VALUE)

    fun toggle(actor: ServerPlayer, target: ServerPlayer) {
        if(!target.isAlive || target.isChangingDimension || target.isSleeping) { app.messages.send(actor,"state","error"); return }
        if(grants.containsKey(target.uuid)) {
            revoke(target,true)
            app.messages.send(actor,"fly_disabled","success","name" to target.gameProfile.name())
        } else {
            grants[target.uuid] = Grant(actor.uuid,actor.uuid != target.uuid)
            target.getAttribute(NeoForgeMod.CREATIVE_FLIGHT)!!.addTransientModifier(modifier)
            lastNativeMode[target.uuid] = native(target)
            sync(target)
            app.messages.send(actor,"fly_enabled","success","name" to target.gameProfile.name())
        }
        Runtime.LOG.info("Flight actor={} target={} enabled={}", actor.uuid, target.uuid, grants.containsKey(target.uuid))
    }
    fun tick() {
        for((uuid,grant) in grants.toMap()) {
            val player = app.server.playerList.getPlayer(uuid) ?: run { grants.remove(uuid); continue }
            if (!player.isAlive) { clear(player); continue }
            if(app.tick % app.config.int("fly.recheck_ticks") == 0L) {
                val actor = app.server.playerList.getPlayer(grant.actor)
                val allowed = app.config.module("fly") && actor != null && app.permissions.command(actor,if(grant.other) "fly_others" else "fly")
                if(!allowed) { revoke(player,true); app.messages.send(player,"fly_revoked","warning"); continue }
            }
            val mode = native(player)
            if(lastNativeMode.put(uuid,mode) != mode) sync(player)
            // Do not re-add a modifier removed by another mod every tick: stop ownership instead.
            if(!player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT)!!.hasModifier(id)) revoke(player,true)
        }
        protection.entries.removeIf { (uuid,until) ->
            val p = app.server.playerList.getPlayer(uuid)
            p == null || !p.isAlive || p.onGround() || app.tick > until
        }
    }
    fun reconcile(player: ServerPlayer) { if(grants.containsKey(player.uuid)) sync(player) }
    fun clear(player: ServerPlayer) { revoke(player,false); protection.remove(player.uuid) }
    fun reload() { for(uuid in grants.keys.toList()) app.server.playerList.getPlayer(uuid)?.let { p ->
        val g=grants.getValue(uuid); val actor=app.server.playerList.getPlayer(g.actor)
        if(!app.config.module("fly") || actor == null || !app.permissions.command(actor,if(g.other) "fly_others" else "fly")) { revoke(p,true); app.messages.send(p,"fly_revoked","warning") }
    } }
    fun stop() { grants.keys.toList().forEach { app.server.playerList.getPlayer(it)?.let { p -> revoke(p,false) } }; protection.clear() }
    private fun revoke(player: ServerPlayer, protect: Boolean) {
        val tracked=grants.remove(player.uuid)!=null
        lastNativeMode.remove(player.uuid)
        val attribute=player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT) ?: return
        val owned=attribute.hasModifier(id)
        attribute.removeModifier(id)
        // Another mod may already have removed our modifier. The tracked grant still needs cleanup/sync.
        if(!player.mayFly() && (owned || tracked)) {
            val airborne=!player.onGround()
            player.abilities.flying=false
            if(protect && airborne && app.config.string("fly.disable_in_air") == "protect") {
                player.fallDistance=0.0
                protection[player.uuid]=app.tick + app.config.int("fly.fall_protection_ticks")
            }
        }
        if(owned || tracked) sync(player)
    }
    fun damage(event: LivingDamageEvent.Pre) {
        val player=event.entity as? ServerPlayer ?: return
        val until=protection[player.uuid] ?: return
        if(event.source.`is`(DamageTypes.FALL) && app.tick <= until) {
            protection.remove(player.uuid); event.newDamage=0f; player.fallDistance=0.0
        }
    }
    private fun native(player: ServerPlayer) = player.isCreative || player.isSpectator
    private fun sync(player: ServerPlayer) {
        player.onUpdateAbilities()
        // Attributes grant flight to NeoForge clients; the standard abilities packet also supports vanilla clients.
        val view=Abilities()
        view.apply(player.abilities.pack())
        @Suppress("DEPRECATION")
        view.mayfly=player.mayFly()
        player.connection.send(ClientboundPlayerAbilitiesPacket(view))
    }
}
