package net.ciyuan.lynxtools.protocol

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import java.util.UUID

/** Wire v1: UUID, bounded count, then UTF-8 text/RGB/decoration bits. Empty list removes the override. */
data class TagSpan(val text: String, val rgb: Int, val flags: Int)
data class TagUpdate(val player: UUID, val spans: List<TagSpan>) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        const val MAX_CHARS = 256
        const val MAX_SPANS = 256
        val TYPE = CustomPacketPayload.Type<TagUpdate>(Identifier.fromNamespaceAndPath("lynxtags", "name_v1"))
        fun validate(spans: List<TagSpan>) {
            require(spans.size <= MAX_SPANS)
            require(spans.sumOf { it.text.codePointCount(0, it.text.length) } <= MAX_CHARS)
            require(spans.all { it.rgb in 0..0xFFFFFF && it.flags in 0..15 && it.text.none { ch -> Character.isISOControl(ch) } })
        }
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, TagUpdate> = object : StreamCodec<RegistryFriendlyByteBuf, TagUpdate> {
            override fun decode(buf: RegistryFriendlyByteBuf): TagUpdate {
                val id = buf.readUUID()
                val count = buf.readVarInt()
                require(count in 0..MAX_SPANS)
                val parts = ArrayList<TagSpan>(count)
                var chars = 0
                repeat(count) {
                    val text = buf.readUtf(MAX_CHARS * 2)
                    chars += text.codePointCount(0, text.length)
                    require(chars <= MAX_CHARS)
                    parts.add(TagSpan(text, buf.readInt(), buf.readUnsignedByte().toInt()))
                }
                validate(parts)
                return TagUpdate(id, parts)
            }
            override fun encode(buf: RegistryFriendlyByteBuf, value: TagUpdate) {
                validate(value.spans)
                buf.writeUUID(value.player)
                buf.writeVarInt(value.spans.size)
                value.spans.forEach { buf.writeUtf(it.text, MAX_CHARS * 2); buf.writeInt(it.rgb); buf.writeByte(it.flags) }
            }
        }
    }
}
