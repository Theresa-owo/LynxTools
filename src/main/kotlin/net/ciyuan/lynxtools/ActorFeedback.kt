package net.ciyuan.lynxtools

import java.util.UUID

/** Scopes native system announcements produced by a self-only command; never changes gamerules. */
object ActorFeedback {
    private val actor=ThreadLocal<UUID?>()
    fun excludes(viewer: UUID)=actor.get()?.let { it!=viewer } ?: false
    fun <T> only(id: UUID, action: () -> T): T {
        val before=actor.get(); actor.set(id)
        try { return action() } finally { if(before==null) actor.remove() else actor.set(before) }
    }
}
