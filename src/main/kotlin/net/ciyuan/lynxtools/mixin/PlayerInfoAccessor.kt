package net.ciyuan.lynxtools.mixin

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Mutable
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(ClientboundPlayerInfoUpdatePacket::class)
interface PlayerInfoAccessor {
    @Mutable @Accessor("entries")
    fun lynxEntries(entries: List<ClientboundPlayerInfoUpdatePacket.Entry>)
}
