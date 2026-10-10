package net.ciyuan.lynxtools

import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/** Attributes synchronous native sounds/particles to their actor, without guessing by coordinates. */
object VanishEffects {
    private val actor=ThreadLocal<UUID?>()
    fun hiddenFrom(viewer: UUID)=actor.get()?.let { VanishVisibility.hidden(viewer,it) } ?: false
    fun enter(entity: Any): UUID? {
        val before=actor.get()
        if(entity is ServerPlayer && VanishVisibility.active(entity)) actor.set(entity.uuid)
        return before
    }
    fun exit(before: UUID?) { if(before==null) actor.remove() else actor.set(before) }
    fun <T> scoped(entity: Any, block: () -> T): T {
        val before=actor.get()
        if(entity is ServerPlayer && VanishVisibility.active(entity)) actor.set(entity.uuid)
        try { return block() } finally { if(before==null) actor.remove() else actor.set(before) }
    }
}
