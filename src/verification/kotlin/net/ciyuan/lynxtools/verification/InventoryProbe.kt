package net.ciyuan.lynxtools.verification

import com.mojang.authlib.GameProfile
import net.ciyuan.lynxtools.Suicides
import net.ciyuan.lynxtools.Settings
import net.ciyuan.lynxtools.Permissions
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.Node
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ClientInformation
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.gamerules.GameRules
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.LoggerFactory
import java.util.UUID
import java.nio.file.Path

/** Exercise the transformed native drop/XP/restore methods, without pretending a fake player is a real client. */
@Mod(value="lynxtools_verification",dist=[Dist.DEDICATED_SERVER])
class InventoryProbe {
    private var measuring=false
    private var itemDrops=0
    private var ticks=0
    init {
        if(System.getProperty("lynxtools.verification.vanish")=="true") VanishProbe()
        if(System.getProperty("lynxtools.verification.tags")=="true") TagServerProbe()
        if(System.getProperty("lynxtools.verification.lifecycle")=="true") LifecycleProbe()
        NeoForge.EVENT_BUS.addListener<ServerStartedEvent> { event -> if(System.getProperty("lynxtools.verification.tags")!="true" && System.getProperty("lynxtools.verification.vanish")!="true") run(event) }
        NeoForge.EVENT_BUS.addListener<EntityJoinLevelEvent> { event -> if(measuring && event.entity is ItemEntity) itemDrops++ }
        NeoForge.EVENT_BUS.addListener<ServerTickEvent.Post> { event ->
            if(System.getProperty("lynxtools.verification.lifecycle")!="true" && System.getProperty("lynxtools.verification.autoStop")=="true" && ++ticks==100) event.server.halt(false)
        }
    }
    private class CommandPlayer(level: ServerLevel, profile: GameProfile) : ServerPlayer(level.server,level,profile,ClientInformation.createDefault()) {
        val messages=mutableListOf<String>()
        init { connection=FakePlayer(level,profile).connection }
        override fun sendSystemMessage(component: Component) { messages.add(component.string) }
        override fun sendSystemMessage(component: Component, overlay: Boolean) { messages.add(component.string) }
    }
    private class Player(level: ServerLevel) : FakePlayer(level,GameProfile(UUID.randomUUID(),"InventoryProbe")) {
        fun nativeDrop() = super.dropEquipment(level())
        fun nativeXp() = super.getBaseExperienceReward(level())
    }
    private fun run(event: ServerStartedEvent) {
        val server=event.server; val world=server.overworld(); val rules=world.gameRules
        val original=rules.get(GameRules.KEEP_INVENTORY)
        val log=LoggerFactory.getLogger("LynxToolsVerification")
        try {
            rules.set(GameRules.KEEP_INVENTORY,false,server)
            val kept=Player(world)
            kept.inventory.setItem(0,ItemStack(Items.DIAMOND,7))
            kept.setItemSlot(EquipmentSlot.HEAD,ItemStack(Items.DIAMOND_HELMET))
            kept.setItemSlot(EquipmentSlot.OFFHAND,ItemStack(Items.SHIELD))
            kept.experienceLevel=12; kept.totalExperience=180; kept.experienceProgress=0.3f
            kept.persistentData.putBoolean("lynxtools:suicide_keep_inventory",true)
            measuring=true; itemDrops=0
            kept.nativeDrop()
            check(itemDrops==0)
            check(kept.inventory.getItem(0).count==7)
            check(kept.getItemBySlot(EquipmentSlot.HEAD).`is`(Items.DIAMOND_HELMET))
            check(kept.getItemBySlot(EquipmentSlot.OFFHAND).`is`(Items.SHIELD))
            check(kept.nativeXp()==0)
            check(!rules.get(GameRules.KEEP_INVENTORY))
            val restored=Player(world)
            restored.restoreFrom(kept,false)
            check(restored.inventory.getItem(0).count==7)
            check(restored.getItemBySlot(EquipmentSlot.HEAD).`is`(Items.DIAMOND_HELMET))
            check(restored.getItemBySlot(EquipmentSlot.OFFHAND).`is`(Items.SHIELD))
            check(restored.experienceLevel==12 && restored.totalExperience==180)
            check(!Suicides.keeps(kept) && !Suicides.keeps(restored))
            val ordinary=Player(world)
            ordinary.inventory.setItem(0,ItemStack(Items.DIRT,3)); ordinary.experienceLevel=12
            ordinary.nativeDrop()
            check(ordinary.inventory.getItem(0).isEmpty)
            check(itemDrops>0)
            measuring=false
            check(ordinary.nativeXp()==84)
            rules.set(GameRules.KEEP_INVENTORY,true,server)
            val global=Player(world)
            global.inventory.setItem(0,ItemStack(Items.GOLD_INGOT,5)); global.experienceLevel=12
            global.nativeDrop()
            check(global.inventory.getItem(0).count==5 && global.nativeXp()==0)
            log.info("INVENTORY_PROBES_PASSED: per-player keep, native drops, armor/offhand, XP, restore, marker cleanup, unchanged global rule")
            rules.set(GameRules.KEEP_INVENTORY,false,server)
            val p=CommandPlayer(world,GameProfile(UUID.randomUUID(),"CommandProbe"))
            val api=LuckPermsProvider.get()
            val user=api.userManager.loadUser(p.uuid).join()
            val settings=Settings.load(Path.of("."))
            val permissions=Permissions(api) { settings }
            check(!permissions.command(p,"home"))
            check(permissions.command(p,"warp"))
            check(permissions.command(p,"suicide"))
            check(!permissions.command(p,"gm"))
            val dispatcher=server.commands.dispatcher
            val source=p.createCommandSourceStack()
            fun suggestions(input: String)=dispatcher.getCompletionSuggestions(dispatcher.parse(input,source)).join().list.map { it.text }
            // Minecraft prunes command packets using canUse. Raw Brigadier root suggestions do not filter it.
            check(!dispatcher.root.getChild("gm").canUse(source))
            val grant=Node.builder("lynxtools.command.gm").value(true).build()
            user.data().add(grant)
            check(permissions.command(p,"gm"))
            check(dispatcher.root.getChild("gm").canUse(source))
            server.commands.performPrefixedCommand(source,"gm c")
            check(p.isCreative && p.messages.any { it.contains("你的游戏模式已设为创造") })
            user.data().remove(grant)
            check(!permissions.command(p,"gm"))
            user.data().add(Node.builder("lynxtools.command.home").value(false).build())
            check(!permissions.command(p,"home"))
            user.data().add(Node.builder("lynxtools.command.warp").value(false).build())
            check(!permissions.command(p,"warp"))
            user.data().add(Node.builder("lynxtools.command.gm").value(true).withContext("lynxtools-probe","unmatched").build())
            check(!permissions.command(p,"gm"))
            user.data().add(Node.builder("lynxtools.command.suicide").value(true).build())
            check("KeepInventory" !in suggestions("suicide "))
            user.data().add(Node.builder("lynxtools.command.suicide.keepinventory").value(true).build())
            check("KeepInventory" in suggestions("suicide "))
            rules.set(GameRules.KEEP_INVENTORY,true,server)
            check("KeepInventory" !in suggestions("suicide "))
            server.commands.performPrefixedCommand(source,"suicide KeepInventory")
            check(p.isAlive && p.messages.last().contains("无需指定"))
            server.commands.performPrefixedCommand(server.createCommandSourceStack(),"gm 1")
            server.commands.performPrefixedCommand(server.createCommandSourceStack(),"suicide")
            server.commands.performPrefixedCommand(server.createCommandSourceStack(),"lynxtools reload")
            log.info("COMMAND_PROBES_PASSED: LuckPerms grant/deny/context, hidden commands, gm actor feedback, KeepInventory permission and gamerule-aware Tab, redundant flag rejected")
        } catch(e: Throwable) { log.error("INVENTORY_PROBES_FAILED",e) }
        finally { measuring=false; rules.set(GameRules.KEEP_INVENTORY,original,server) }
    }
}
