package net.ciyuan.lynxtools

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands as VanillaCommands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.gamerules.GameRules
import net.neoforged.neoforge.event.CommandEvent
import java.util.Locale

class Commands(private val runtime: () -> Runtime?) {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        for((command,_) in nodes) {
            dispatcher.register(VanillaCommands.literal(command).requires { allowed(it,command) }
                .executes { execute(it.source,command,"") }
                .then(VanillaCommands.argument("args",StringArgumentType.greedyString())
                    .suggests { c,b ->
                        val app=runtime(); val p=c.source.entity as? ServerPlayer
                        val raw=b.remaining
                        val parts=raw.split(' ')
                        val tail=b.createOffset(b.start + (raw.lastIndexOf(' ')+1))
                        val suggestions=if(app==null || !allowed(c.source,command)) emptyList() else when(command) {
                            "warp" -> if(parts.size==1) app.warpNames.filter { p!=null && app.permissions.warp(p,it) } else emptyList()
                            "setwarp" -> if(p==null) emptyList() else when(parts.size) {
                                1 -> app.warpNames.filter { app.permissions.warp(p,it) }
                                2 -> listOf(p.x.toString())
                                3 -> listOf(p.y.toString())
                                4 -> listOf(p.z.toString())
                                5 -> listOf(p.yRot.toString())
                                6 -> listOf(p.xRot.toString())
                                else -> emptyList()
                            }
                            "gm" -> if(parts.size==1) listOf("0","1","2","3","s","c","a","p") else emptyList()
                            "fly" -> if(parts.size==1 && p!=null) app.server.playerList.players.filter {
                                app.permissions.command(p,if(it.uuid==p.uuid) "fly" else "fly_others")
                            }.map { it.gameProfile.name() } else emptyList()
                            "lynxtools" -> if(parts.size==1) listOf("reload") else emptyList()
                            "suicide" -> if(parts.size==1 && p!=null && !p.level().gameRules.get(GameRules.KEEP_INVENTORY) && app.permissions.command(p,"suicide_keepinventory")) listOf("KeepInventory") else emptyList()
                            "tpr" -> if(parts.size==1 && p!=null) app.server.playerList.players.filter { it!==p && app.teleports.state(it) }.map { it.gameProfile.name() } else emptyList()
                            "tpa","tpd" -> if(parts.size==1 && p!=null) app.requests.suggestions(p) else if(command=="tpd" && parts.size==2) listOf("ignore") else emptyList()
                            else -> emptyList()
                        }
                        SharedSuggestionProvider.suggest(suggestions,tail)
                    }.executes { execute(it.source,command,StringArgumentType.getString(it,"args")) }))
        }
        for((alias,canonical) in aliases) dispatcher.register(VanillaCommands.literal(alias).requires { allowed(it,canonical) }
            .executes { execute(it.source,canonical,"") }.redirect(dispatcher.root.getChild(canonical)))
    }
    private fun allowed(source: CommandSourceStack, command: String): Boolean {
        val app=runtime() ?: return false
        val p=source.entity as? ServerPlayer
        if(p==null) return command=="lynxtools" && source.source===app.server && app.config.bool("permissions.console_reload")
        if(p.isFakePlayer || source.source!==p.commandSource()) return false
        return (command=="lynxtools" || app.config.module(command)) && (app.permissions.command(p,nodes.getValue(command)) || command=="fly" && app.permissions.command(p,"fly_others"))
    }
    /** Preserve hidden command trees while giving explicit, configurable feedback for typed denied commands. */
    fun intercept(event: CommandEvent) {
        val app=runtime() ?: return
        val input=event.parseResults.reader.string.trimStart('/').trim()
        val typed=input.substringBefore(' ')
        val command=aliases[typed] ?: typed
        if(command !in nodes) return
        val source=event.parseResults.context.source
        val p=source.entity as? ServerPlayer
        val failure=when {
            (p==null || source.source!==p.commandSource()) && command!="lynxtools" -> "players_only"
            command!="lynxtools" && !app.config.module(command) -> "disabled"
            !allowed(source,command) -> "denied"
            else -> null
        }
        if(failure!=null) { event.isCanceled=true; app.messages.send(source,failure,"error") }
    }
    private fun execute(source: CommandSourceStack, command: String, input: String): Int {
        val app=runtime() ?: return 0
        if(!allowed(source,command)) { app.messages.send(source,"denied","error"); return 0 }
        val args=input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        fun usage(): Int { app.messages.send(source,"usage","error","usage" to app.config.language.text("usage_$command")); return 0 }
        if(command=="lynxtools") { if(args!=listOf("reload")) return usage(); app.reload(source); return 1 }
        val p=source.entity as? ServerPlayer ?: run { app.messages.send(source,"players_only","error"); return 0 }
        try {
            when(command) {
                "warp" -> {
                    if(args.size!=1) return usage()
                    val name=warpName(app,p,args[0]) ?: return 0
                    if(!app.permissions.warp(p,name)) { app.messages.send(p,"denied","error"); return 0 }
                    app.teleports.request(p,"warp",name)
                }
                "setwarp" -> {
                    if(args.size !in setOf(1,4,5,6)) return usage()
                    val name=warpName(app,p,args[0]) ?: return 0
                    if(!app.teleports.state(p)) { app.messages.send(p,"state","error"); return 0 }
                    val place=if(args.size==1) app.place(p) else {
                        val coords=args.drop(1).map { it.toDoubleOrNull() ?: return usage() }
                        Place(app.place(p).dimension,coords[0],coords[1],coords[2],coords.getOrElse(3) { app.config.double("warp.default_yaw") }.toFloat(),coords.getOrElse(4) { app.config.double("warp.default_pitch") }.toFloat())
                    }
                    if(!app.teleports.bounds(p.level(),place)) { app.messages.send(p,"invalid_position","error"); return 0 }
                    app.complete(app.store.save("warp",name,place,app.config.bool("warp.overwrite")),"save warp=$name actor=${p.uuid}") { result,error ->
                        if(error==null && result!=Saved.EXISTS) app.warpNames.add(name)
                        if(app.online(p)) app.messages.send(p,if(error!=null) "storage" else when(result) { Saved.CREATED -> "warp_created"; Saved.UPDATED -> "warp_updated"; else -> "warp_exists" },if(error!=null || result==Saved.EXISTS) "error" else "success","name" to name)
                    }
                }
                "home","back" -> { if(args.isNotEmpty()) return usage(); app.teleports.request(p,command,command) }
                "sethome" -> {
                    if(args.isNotEmpty()) return usage()
                    if(!app.teleports.state(p)) { app.messages.send(p,"state","error"); return 0 }
                    app.complete(app.store.save("home",p.uuid.toString(),app.place(p),true,p.gameProfile.name()),"save home uuid=${p.uuid}") { result,error ->
                        if(app.online(p)) app.messages.send(p,if(error!=null) "storage" else if(result==Saved.UPDATED) "home_updated" else "home_created",if(error==null) "success" else "error")
                    }
                }
                "gm" -> {
                    if(args.size!=1) return usage()
                    val mode=when(args[0].lowercase(Locale.ROOT)) { "0","s" -> GameType.SURVIVAL; "1","c" -> GameType.CREATIVE; "2","a" -> GameType.ADVENTURE; "3","p" -> GameType.SPECTATOR; else -> return usage() }
                    if(!p.isAlive || p.isChangingDimension) { app.messages.send(p,"state","error"); return 0 }
                    val before=p.gameMode.gameModeForPlayer
                    val changed=if(before==mode) true else p.setGameMode(mode)
                    val actual=p.gameMode.gameModeForPlayer
                    Runtime.LOG.info("Game mode actor={} name={} from={} requested={} result={} accepted={}",p.uuid,p.gameProfile.name(),before,mode,actual,changed)
                    if(!changed || actual!=mode) app.messages.send(p,"state","error")
                    app.flights.reconcile(p)
                }
                "fly" -> {
                    if(args.size>1) return usage()
                    val target=if(args.isEmpty()) p else app.server.playerList.getPlayerByName(args[0])
                    if(target==null) { app.messages.send(p,"missing_player","error","name" to args[0]); return 0 }
                    val node=if(target.uuid==p.uuid) "fly" else "fly_others"
                    if(!app.permissions.command(p,node)) { app.messages.send(p,"denied","error"); return 0 }
                    app.flights.toggle(p,target)
                }
                "suicide" -> {
                    if(args.size>1 || args.size==1 && !args[0].equals("KeepInventory",ignoreCase=true)) return usage()
                    if(args.isNotEmpty() && p.level().gameRules.get(GameRules.KEEP_INVENTORY)) { app.messages.send(p,"suicide_already_kept","warning"); return 0 }
                    if(args.isNotEmpty() && !app.permissions.command(p,"suicide_keepinventory")) { app.messages.send(p,"denied","error"); return 0 }
                    Suicides.execute(app,p,args.isNotEmpty())
                }
                "tpr" -> {
                    if(args.size!=1) return usage()
                    val target=app.server.playerList.getPlayerByName(args[0])
                    if(target==null) { app.messages.send(p,"missing_player","error","name" to args[0]); return 0 }
                    app.requests.send(p,target)
                }
                "tpa" -> { if(args.size>1) return usage(); app.requests.reply(p,args.firstOrNull(),true) }
                "tpd" -> {
                    if(args.size>2 || args.size==2 && !args[1].equals("ignore",true)) return usage()
                    app.requests.reply(p,args.firstOrNull(),false,args.size==2)
                }
            }
            return 1
        } catch(e: Exception) { Runtime.LOG.error("Command failed command={} actor={}",command,p.uuid,e); app.messages.send(p,"internal","error"); return 0 }
    }
    private fun warpName(app: Runtime,p: ServerPlayer,input: String): String? {
        if(input.length !in 1..app.config.int("warp.max_name_length") || !input.matches(Regex("[a-zA-Z0-9_-]+"))) {
            app.messages.send(p,"invalid_name","error","max" to app.config.int("warp.max_name_length").toString()); return null
        }
        return input.lowercase(Locale.ROOT)
    }
    companion object {
        val nodes=linkedMapOf("warp" to "warp","setwarp" to "setwarp","home" to "home","sethome" to "sethome","back" to "back","gm" to "gm","fly" to "fly","suicide" to "suicide","tpr" to "tpr","tpa" to "tpa","tpd" to "tpd","lynxtools" to "reload")
        val aliases=mapOf("tprequest" to "tpr","tpaccept" to "tpa","tpdecline" to "tpd")
    }
}
