package net.ciyuan.lynxtools

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID
import java.util.concurrent.*

data class Place(val dimension: String, val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) {
    fun valid() = dimension.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) && listOf(x,y,z,yaw.toDouble(),pitch.toDouble()).all { it.isFinite() } && pitch in -90f..90f
}
data class Death(val place: Place, val revision: String)
enum class Saved { CREATED, UPDATED, EXISTS }

/** Owns one connection. No Minecraft objects may enter this worker. */
class Store(private val path: Path, private val settings: Settings) {
    private lateinit var connection: Connection
    private val closed = CompletableFuture<Unit>()
    private val executor = object : ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(settings.int("storage.queue_capacity")), ThreadFactory { r -> Thread(r,"LynxTools-SQLite").apply { isDaemon = true } }) {
        override fun terminated() {
            try { if(::connection.isInitialized && !connection.isClosed) connection.close(); closed.complete(Unit) }
            catch(e: Exception) { closed.completeExceptionally(e) }
        }
    }
    @Volatile private var closing = false
    private fun <T> submit(block: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        if (closing) return result.also { it.completeExceptionally(IllegalStateException("Database closing")) }
        try { executor.execute { try { result.complete(block()) } catch (e: Exception) { result.completeExceptionally(e) } } }
        catch (e: RejectedExecutionException) { result.completeExceptionally(e) }
        return result
    }
    fun open(onlineMode: Boolean) = submit {
        Files.createDirectories(path.toAbsolutePath().parent)
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
        try {
            connection.createStatement().use { s ->
                s.execute("PRAGMA busy_timeout=${settings.int("storage.busy_timeout_ms")}")
                s.executeQuery("PRAGMA quick_check").use { require(it.next() && it.getString(1) == "ok") { "SQLite integrity check failed: $path" } }
                val version = s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
                require(version in 0..1) { "Unsupported SQLite schema $version at $path" }
                transaction {
                    if (version == 0) {
                        s.execute("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
                        s.execute("CREATE TABLE players (uuid TEXT PRIMARY KEY, name TEXT NOT NULL)")
                        s.execute("CREATE TABLE places (kind TEXT NOT NULL, owner TEXT NOT NULL, dimension TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, revision TEXT NOT NULL, PRIMARY KEY(kind,owner))")
                        connection.prepareStatement("INSERT INTO metadata(key,value) VALUES('online_mode',?)").use { it.setString(1,onlineMode.toString()); it.executeUpdate() }
                        s.execute("PRAGMA user_version=1")
                    }
                    s.executeQuery("SELECT value FROM metadata WHERE key='online_mode'").use { require(it.next() && it.getString(1) == onlineMode.toString()) { "online-mode differs from database identity policy; refusing UUID conversion" } }
                }
                s.execute("PRAGMA journal_mode=WAL")
                s.execute("PRAGMA synchronous=FULL")
            }
        } catch (e: Exception) { try { connection.close() } catch (close: Exception) { e.addSuppressed(close) }; throw e }
    }
    private fun <T> transaction(block: () -> T): T {
        connection.autoCommit = false
        try { val result = block(); connection.commit(); return result }
        catch(e: Exception) { try { connection.rollback() } catch(r: Exception) { e.addSuppressed(r) }; throw e }
        finally { connection.autoCommit = true }
    }
    fun names() = submit { connection.prepareStatement("SELECT owner FROM places WHERE kind='warp' ORDER BY owner").use { s -> s.executeQuery().use { r -> buildList { while(r.next()) add(r.getString(1)) } } } }
    fun get(kind: String, owner: String) = submit { read(kind,owner) }
    private fun read(kind: String, owner: String): Death? = connection.prepareStatement("SELECT dimension,x,y,z,yaw,pitch,revision FROM places WHERE kind=? AND owner=?").use { s ->
        s.setString(1,kind); s.setString(2,owner)
        s.executeQuery().use { r -> if(r.next()) Death(place(r),r.getString("revision")) else null }
    }
    private fun place(r: ResultSet) = Place(r.getString("dimension"),r.getDouble("x"),r.getDouble("y"),r.getDouble("z"),r.getFloat("yaw"),r.getFloat("pitch"))
    fun save(kind: String, owner: String, place: Place, overwrite: Boolean, playerName: String? = null) = submit {
        require(place.valid()) { "Invalid persisted position" }
        require(kind in setOf("warp","home","death"))
        transaction {
            val exists = read(kind,owner) != null
            if (exists && !overwrite) Saved.EXISTS else {
                if(playerName != null) connection.prepareStatement("INSERT INTO players(uuid,name) VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name").use { it.setString(1,owner); it.setString(2,playerName); it.executeUpdate() }
                connection.prepareStatement("INSERT INTO places(kind,owner,dimension,x,y,z,yaw,pitch,revision) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(kind,owner) DO UPDATE SET dimension=excluded.dimension,x=excluded.x,y=excluded.y,z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch,revision=excluded.revision").use { s ->
                    s.setString(1,kind); s.setString(2,owner); s.setString(3,place.dimension)
                    s.setDouble(4,place.x); s.setDouble(5,place.y); s.setDouble(6,place.z); s.setFloat(7,place.yaw); s.setFloat(8,place.pitch); s.setString(9,UUID.randomUUID().toString()); s.executeUpdate()
                }
                if(exists) Saved.UPDATED else Saved.CREATED
            }
        }
    }
    fun consumeDeath(owner: String, revision: String) = submit {
        connection.prepareStatement("DELETE FROM places WHERE kind='death' AND owner=? AND revision=?").use { it.setString(1,owner); it.setString(2,revision); it.executeUpdate() }
    }
    /** Shutdown happens after game work stops. Drain accepted writes before closing on the owner thread. */
    fun close() {
        closing = true
        executor.shutdown()
        check(executor.awaitTermination(settings.int("storage.shutdown_timeout_seconds").toLong(),TimeUnit.SECONDS)) { "Database worker did not drain; connection left to worker/process shutdown, no data deleted: $path" }
        closed.join()
    }
}
