package net.ciyuan.lynxtools

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID

class StorageConfigTest {
    @TempDir lateinit var root: Path
    private val place=Place("minecraft:the_nether",3.5,68.0,-4.5,125f,-12f)
    private fun settings()=Settings.load(root)
    private fun store(s: Settings=settings())=Store(root.resolve("Data.db"),s).also { it.open(true).join() }
    @Test fun `homes overwrite by UUID and survive reopening`() {
        val uuid=UUID.randomUUID().toString()
        var db=store()
        assertEquals(Saved.CREATED,db.save("home",uuid,place,true,"OldName").join())
        assertEquals(Saved.UPDATED,db.save("home",uuid,place.copy(x=8.0),true,"NewName").join())
        db.close(); db=store()
        assertEquals(place.copy(x=8.0),db.get("home",uuid).join()!!.place)
        assertNull(db.get("home",UUID.randomUUID().toString()).join())
        db.close()
    }
    @Test fun `warp rejection does not overwrite and explicit overwrite reports update`() {
        val db=store()
        assertEquals(Saved.CREATED,db.save("warp","spawn",place,false).join())
        assertEquals(Saved.EXISTS,db.save("warp","spawn",place.copy(y=90.0),false).join())
        assertEquals(place,db.get("warp","spawn").join()!!.place)
        assertEquals(Saved.UPDATED,db.save("warp","spawn",place.copy(y=90.0),true).join())
        assertEquals(listOf("spawn"),db.names().join()); db.close()
    }
    @Test fun `consuming an older death cannot delete a newer death`() {
        val db=store(); val uuid=UUID.randomUUID().toString()
        db.save("death",uuid,place,true).join()
        val first=db.get("death",uuid).join()!!
        db.save("death",uuid,place.copy(dimension="minecraft:overworld"),true).join()
        assertEquals(0,db.consumeDeath(uuid,first.revision).join())
        val recent=db.get("death",uuid).join()!!
        db.close()
        val reopened=store()
        assertEquals(recent,reopened.get("death",uuid).join())
        assertEquals(1,reopened.consumeDeath(uuid,recent.revision).join())
        assertNull(reopened.get("death",uuid).join()); reopened.close()
    }
    @Test fun `names containing SQL syntax remain data`() {
        val db=store(); val uuid=UUID.randomUUID().toString()
        db.save("home",uuid,place,true,"x'); DROP TABLE places;--").join()
        assertEquals(place,db.get("home",uuid).join()!!.place); db.close()
    }
    @Test fun `locked database fails the future and does not claim persistence`() {
        val s=settings().let { it.copy(values=it.values+("storage.busy_timeout_ms" to 25)) }
        val db=store(s)
        DriverManager.getConnection("jdbc:sqlite:${root.resolve("Data.db")}").use { other ->
            other.createStatement().use { it.execute("BEGIN IMMEDIATE") }
            assertThrows(Exception::class.java) { db.save("warp","locked",place,false).join() }
            other.createStatement().use { it.execute("ROLLBACK") }
        }
        assertNull(db.get("warp","locked").join()); db.close()
    }
    @Test fun `corrupt database is never replaced`() {
        val path=root.resolve("Data.db"); val bytes="not a database; preserve me".toByteArray()
        Files.write(path,bytes)
        val db=Store(path,settings())
        assertThrows(Exception::class.java) { db.open(true).join() }
        db.close(); assertArrayEquals(bytes,Files.readAllBytes(path))
    }
    @Test fun `identity mode mismatch refuses conversion`() {
        val db=store(); db.save("home","uuid",place,true).join(); db.close()
        val other=Store(root.resolve("Data.db"),settings())
        assertThrows(Exception::class.java) { other.open(false).join() }; other.close()
        val reopened=store(); assertEquals(place,reopened.get("home","uuid").join()!!.place); reopened.close()
    }
    @Test fun `close drains accepted writes and rejects later writes`() {
        val db=store()
        val futures=(1..30).map { db.save("warp","w$it",place,false) }
        db.close(); futures.forEach { assertEquals(Saved.CREATED,it.join()) }
        assertThrows(Exception::class.java) { db.save("warp","late",place,false).join() }
        val reopened=store(); assertEquals(30,reopened.names().join().size); reopened.close()
    }
    @Test fun `configuration defaults are safe and invalid edits fail`() {
        val initial=settings()
        val allowed = setOf("warp", "suicide", "tpr", "tpa", "tpd")
        initial.values.keys.filter { it.startsWith("permissions.") && it != "permissions.console_reload" }.forEach { key ->
            assertEquals(key.removePrefix("permissions.") in allowed, initial.bool(key), key)
        }
        val path=root.resolve("plugins/LynxTools/Config.toml")
        Files.writeString(path,"[teleport]\nsearch_radius = 999\n")
        assertThrows(IllegalArgumentException::class.java) { settings() }
        assertEquals(8,initial.int("teleport.search_radius"))
        Files.writeString(path,"[permissions]\ngmm = true\n")
        assertThrows(IllegalArgumentException::class.java) { settings() }
    }
    @Test fun `existing permission configuration is preserved and missing keys use new defaults`() {
        settings()
        val path=root.resolve("plugins/LynxTools/Config.toml")
        val existing="[permissions]\nhome = true\nsuicide = false\n"
        Files.writeString(path,existing)
        val loaded=settings()
        assertTrue(loaded.bool("permissions.home"))
        assertFalse(loaded.bool("permissions.suicide"))
        assertFalse(loaded.bool("permissions.sethome"))
        assertTrue(loaded.bool("permissions.tpr"))
        assertEquals(existing,Files.readString(path))
    }
    @Test fun `language interpolation is literal and rejects unknown placeholders`() {
        val s=settings()
        val input="<gradient:red:blue>{name}&c"
        assertTrue(s.language.text("warp_created",mapOf("name" to input)).contains(input))
        Files.writeString(root.resolve("plugins/LynxTools/Lang/zh_cn.toml"),"[messages]\nwarp_created = '{unknown}'\n")
        assertThrows(IllegalArgumentException::class.java) { settings() }
    }
    @Test fun `nonfinite positions never enter storage`() {
        val db=store()
        assertThrows(Exception::class.java) { db.save("home","uuid",place.copy(x=Double.NaN),true).join() }
        assertNull(db.get("home","uuid").join()); db.close()
    }
    @Test fun `directory used as database fails without altering contents`() {
        val path=root.resolve("dbdir"); Files.createDirectory(path); Files.writeString(path.resolve("keep"),"original")
        val db=Store(path,settings()); assertThrows(Exception::class.java) { db.open(true).join() }; db.close()
        assertEquals("original",Files.readString(path.resolve("keep")))
    }
}
