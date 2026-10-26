package com.openfy.data

import com.openfy.core.Playlist
import com.openfy.core.Track
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * Banco local unico (SQLite, WAL). Tudo que o app sabe sobre musica mora aqui:
 * metadados em cache, curtidas, playlists, historico e preferencias.
 */
class Db(file: Path) : AutoCloseable {

    private val conn: Connection

    init {
        Files.createDirectories(file.parent)
        Class.forName("org.sqlite.JDBC")
        conn = DriverManager.getConnection("jdbc:sqlite:$file")
        conn.createStatement().use { st ->
            st.execute("pragma journal_mode=WAL")
            st.execute("pragma synchronous=NORMAL")
            st.execute(
                """
                create table if not exists track(
                    id text primary key,
                    title text not null,
                    artist text not null,
                    album text not null default '',
                    duration integer not null default 0,
                    thumbnail text not null default '',
                    updated_at integer not null
                )
                """.trimIndent()
            )
            st.execute("create table if not exists liked(id text primary key, at integer not null)")
            st.execute(
                "create table if not exists playlist(" +
                    "id text primary key, name text not null, created_at integer not null)"
            )
            st.execute(
                "create table if not exists playlist_item(" +
                    "playlist_id text not null, id text not null, position integer not null, " +
                    "primary key(playlist_id, id))"
            )
            st.execute(
                "create table if not exists play_event(" +
                    "seq integer primary key autoincrement, id text not null, at integer not null)"
            )
            st.execute("create table if not exists setting(k text primary key, v text not null)")

            // Migracoes incrementais: user_version marca o ponto ate onde o
            // esquema foi atualizado. Toda mudanca de tabela entra aqui, nunca
            // num "create table if not exists" solto.
            val version = st.executeQuery("pragma user_version").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
            if (version < 1) {
                st.execute(
                    "create table if not exists search_term(" +
                        "id integer primary key autoincrement, term text not null, at integer not null)"
                )
                st.execute("pragma user_version=1")
            }
            if (version < 2) {
                st.execute(
                    "create table if not exists download(" +
                        "id text primary key, title text not null, artist text not null, " +
                        "album text not null default '', duration integer not null default 0, " +
                        "thumbnail text not null default '', path text not null, " +
                        "size integer not null default 0, at integer not null)"
                )
                st.execute("pragma user_version=2")
            }
            if (version < 3) {
                fun hasColumn(table: String, column: String): Boolean =
                    st.executeQuery("pragma table_info($table)").use { rs ->
                        var found = false
                        while (rs.next()) {
                            if (rs.getString("name") == column) {
                                found = true
                                break
                            }
                        }
                        found
                    }
                // Metadados prontos para sync: updated_at + soft delete (tombstone).
                if (!hasColumn("playlist", "cover")) {
                    st.execute("alter table playlist add column cover text not null default ''")
                }
                if (!hasColumn("playlist", "updated_at")) {
                    st.execute("alter table playlist add column updated_at integer not null default 0")
                }
                if (!hasColumn("playlist", "deleted")) {
                    st.execute("alter table playlist add column deleted integer not null default 0")
                }
                if (!hasColumn("liked", "updated_at")) {
                    st.execute("alter table liked add column updated_at integer not null default 0")
                }
                if (!hasColumn("liked", "deleted")) {
                    st.execute("alter table liked add column deleted integer not null default 0")
                }
                if (!hasColumn("playlist_item", "updated_at")) {
                    st.execute("alter table playlist_item add column updated_at integer not null default 0")
                }
                if (!hasColumn("playlist_item", "deleted")) {
                    st.execute("alter table playlist_item add column deleted integer not null default 0")
                }
                st.execute(
                    "create table if not exists track_bpm(" +
                        "id text primary key, bpm real not null, at integer not null)"
                )
                st.execute("update playlist set updated_at=created_at where updated_at=0")
                st.execute("update liked set updated_at=at where updated_at=0")
                st.execute(
                    "update playlist_item set updated_at=" +
                        "coalesce((select created_at from playlist p where p.id=playlist_item.playlist_id), 0) " +
                        "where updated_at=0"
                )
                st.execute("pragma user_version=3")
            }
        }
    }

    // ------------------------------------------------------------------ track cache
    @Synchronized
    fun remember(track: Track) {
        conn.prepareStatement(
            """
            insert into track(id, title, artist, album, duration, thumbnail, updated_at)
            values(?,?,?,?,?,?,?)
            on conflict(id) do update set
                title=excluded.title, artist=excluded.artist, album=excluded.album,
                duration=excluded.duration, thumbnail=excluded.thumbnail, updated_at=excluded.updated_at
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, track.id)
            ps.setString(2, track.title)
            ps.setString(3, track.artist)
            ps.setString(4, track.album)
            ps.setInt(5, track.durationSec)
            ps.setString(6, track.thumbnail)
            ps.setLong(7, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    // ------------------------------------------------------------------ likes
    @Synchronized
    fun isLiked(id: String): Boolean =
        conn.prepareStatement("select 1 from liked where id=? and deleted=0").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> rs.next() }
        }

    @Synchronized
    fun toggleLike(id: String): Boolean {
        val liked = isLiked(id)
        val now = System.currentTimeMillis()
        if (liked) {
            // Soft delete: o sync precisa saber que a curtida foi removida.
            conn.prepareStatement("update liked set deleted=1, updated_at=? where id=?").use { ps ->
                ps.setLong(1, now); ps.setString(2, id); ps.executeUpdate()
            }
        } else {
            conn.prepareStatement(
                "insert into liked(id, at, updated_at, deleted) values(?,?,?,0) " +
                    "on conflict(id) do update set at=excluded.at, updated_at=excluded.updated_at, deleted=0"
            ).use { ps ->
                ps.setString(1, id); ps.setLong(2, now); ps.setLong(3, now); ps.executeUpdate()
            }
        }
        return !liked
    }

    @Synchronized
    fun likedTracks(limit: Int = 500): List<Track> =
        queryTracks(
            "select t.* from track t join liked l on l.id = t.id " +
                "where l.deleted=0 order by l.at desc limit ?",
            limit,
        )

    /** Curtidas que ainda nao estao no cache de metadados aparecem pelo id. */
    @Synchronized
    fun likedIds(): List<String> =
        conn.prepareStatement("select id from liked where deleted=0 order by at desc").use { ps ->
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }

    // ------------------------------------------------------------------ playlists
    @Synchronized
    fun playlists(): List<Playlist> =
        conn.prepareStatement(
            "select p.id, p.name, " +
                "(select count(*) from playlist_item i where i.playlist_id = p.id and i.deleted=0) as c, " +
                "coalesce(nullif(p.cover,''), (" +
                "  select t.thumbnail from playlist_item i join track t on t.id = i.id " +
                "  where i.playlist_id = p.id and i.deleted=0 order by i.position limit 1" +
                "), '') as cover " +
                "from playlist p where p.deleted=0 order by p.created_at asc"
        ).use { ps ->
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            Playlist(
                                id = rs.getString("id"),
                                name = rs.getString("name"),
                                count = rs.getInt("c"),
                                cover = rs.getString("cover") ?: "",
                            )
                        )
                    }
                }
            }
        }

    @Synchronized
    fun createPlaylist(name: String): Playlist {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        conn.prepareStatement(
            "insert into playlist(id, name, created_at, cover, updated_at, deleted) " +
                "values(?,?,?,'',?,0)"
        ).use { ps ->
            ps.setString(1, id); ps.setString(2, name); ps.setLong(3, now); ps.setLong(4, now)
            ps.executeUpdate()
        }
        return Playlist(id, name, 0, "")
    }

    @Synchronized
    fun deletePlaylist(id: String) {
        val now = System.currentTimeMillis()
        conn.prepareStatement("update playlist set deleted=1, updated_at=? where id=?").use { ps ->
            ps.setLong(1, now); ps.setString(2, id); ps.executeUpdate()
        }
        conn.prepareStatement(
            "update playlist_item set deleted=1, updated_at=? where playlist_id=?"
        ).use { ps ->
            ps.setLong(1, now); ps.setString(2, id); ps.executeUpdate()
        }
    }

    @Synchronized
    fun renamePlaylist(id: String, name: String) {
        conn.prepareStatement("update playlist set name=?, updated_at=? where id=?").use { ps ->
            ps.setString(1, name); ps.setLong(2, System.currentTimeMillis()); ps.setString(3, id)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun setPlaylistCover(id: String, path: String) {
        conn.prepareStatement("update playlist set cover=?, updated_at=? where id=?").use { ps ->
            ps.setString(1, path); ps.setLong(2, System.currentTimeMillis()); ps.setString(3, id)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun playlistCover(id: String): String? =
        conn.prepareStatement("select cover from playlist where id=? and deleted=0").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    @Synchronized
    fun addToPlaylist(playlistId: String, track: Track): Boolean {
        remember(track)
        val existing = conn.prepareStatement(
            "select deleted from playlist_item where playlist_id=? and id=?"
        ).use { ps ->
            ps.setString(1, playlistId); ps.setString(2, track.id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else -1 }
        }
        if (existing == 0) return false
        val position = conn.prepareStatement(
            "select coalesce(max(position), 0) + 1 from playlist_item " +
                "where playlist_id=? and deleted=0"
        ).use { ps ->
            ps.setString(1, playlistId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 1 }
        }
        conn.prepareStatement(
            "insert into playlist_item(playlist_id, id, position, updated_at, deleted) " +
                "values(?,?,?,?,0) " +
                "on conflict(playlist_id, id) do update set " +
                "position=excluded.position, updated_at=excluded.updated_at, deleted=0"
        ).use { ps ->
            ps.setString(1, playlistId); ps.setString(2, track.id); ps.setInt(3, position)
            ps.setLong(4, System.currentTimeMillis()); ps.executeUpdate()
        }
        return true
    }

    @Synchronized
    fun removeFromPlaylist(playlistId: String, trackId: String) {
        conn.prepareStatement(
            "update playlist_item set deleted=1, updated_at=? where playlist_id=? and id=?"
        ).use { ps ->
            ps.setLong(1, System.currentTimeMillis())
            ps.setString(2, playlistId)
            ps.setString(3, trackId)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun playlistTracks(playlistId: String): List<Track> =
        conn.prepareStatement(
            "select t.* from playlist_item i join track t on t.id = i.id " +
                "where i.playlist_id=? and i.deleted=0 order by i.position asc"
        ).use { ps ->
            ps.setString(1, playlistId)
            ps.executeQuery().use { rs -> readTracks(rs) }
        }

    @Synchronized
    fun playlistName(id: String): String? =
        conn.prepareStatement("select name from playlist where id=? and deleted=0").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    // ------------------------------------------------------------------ history
    @Synchronized
    fun recordPlay(id: String) {
        conn.prepareStatement("insert into play_event(id, at) values(?,?)").use { ps ->
            ps.setString(1, id); ps.setLong(2, System.currentTimeMillis()); ps.executeUpdate()
        }
    }

    @Synchronized
    fun history(limit: Int = 200): List<Track> =
        conn.prepareStatement(
            "select t.* from track t where t.id in " +
                "(select id from play_event order by at desc limit ?) " +
                "order by (select max(at) from play_event e where e.id = t.id) desc"
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs -> readTracks(rs) }
        }

    @Synchronized
    fun recentlyPlayed(limit: Int = 12): List<Track> =
        conn.prepareStatement(
            "select t.* from track t join " +
                "(select id, max(at) as last from play_event group by id order by last desc limit ?) e " +
                "on e.id = t.id order by e.last desc"
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs -> readTracks(rs) }
        }

    @Synchronized
    fun playCount(id: String): Int =
        conn.prepareStatement("select count(*) from play_event where id=?").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

    /** Faixas mais tocadas, para o download automatico em segundo plano. */
    @Synchronized
    fun topPlayed(limit: Int = 12): List<Track> =
        conn.prepareStatement(
            "select t.* from track t join " +
                "(select id, count(*) c from play_event group by id order by c desc limit ?) e " +
                "on e.id = t.id order by e.c desc"
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { readTracks(it) }
        }

    /** Curtir sem descurtir (importacao/backup). */
    @Synchronized
    fun like(id: String) {
        val now = System.currentTimeMillis()
        conn.prepareStatement(
            "insert into liked(id, at, updated_at, deleted) values(?,?,?,0) " +
                "on conflict(id) do update set deleted=0, updated_at=excluded.updated_at"
        ).use { ps ->
            ps.setString(1, id); ps.setLong(2, now); ps.setLong(3, now); ps.executeUpdate()
        }
    }

    // ------------------------------------------------------------------ bpm (modo DJ)
    @Synchronized
    fun bpm(id: String): Double? =
        conn.prepareStatement("select bpm from track_bpm where id=?").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getDouble(1) else null }
        }

    @Synchronized
    fun setBpm(id: String, bpm: Double) {
        conn.prepareStatement("insert or replace into track_bpm(id, bpm, at) values(?,?,?)").use { ps ->
            ps.setString(1, id); ps.setDouble(2, bpm); ps.setLong(3, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun totalPlays(): Int =
        conn.createStatement().use { st ->
            st.executeQuery("select count(*) from play_event").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }

    @Synchronized
    fun totalListenedSeconds(): Long =
        conn.createStatement().use { st ->
            st.executeQuery(
                "select coalesce(sum(t.duration),0) from play_event e join track t on t.id = e.id"
            ).use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }

    @Synchronized
    fun topArtists(limit: Int = 5): List<Pair<String, Int>> =
        conn.prepareStatement(
            "select t.artist, count(*) c from play_event e join track t on t.id = e.id " +
                "group by t.artist order by c desc limit ?"
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) to rs.getInt(2)) }
            }
        }

    // ------------------------------------------------------------------ settings
    @Synchronized
    fun get(key: String): String? =
        conn.prepareStatement("select v from setting where k=?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    @Synchronized
    fun put(key: String, value: String) {
        conn.prepareStatement("insert or replace into setting(k, v) values(?,?)").use { ps ->
            ps.setString(1, key); ps.setString(2, value); ps.executeUpdate()
        }
    }

    // ------------------------------------------------------------------ search history
    /** Guarda o termo para sugestoes; reposiciona termos repetidos no topo. */
    @Synchronized
    fun addSearchTerm(term: String) {
        val clean = term.trim()
        if (clean.isEmpty()) return
        conn.prepareStatement("delete from search_term where lower(term)=lower(?)").use { ps ->
            ps.setString(1, clean); ps.executeUpdate()
        }
        conn.prepareStatement("insert into search_term(term, at) values(?,?)").use { ps ->
            ps.setString(1, clean); ps.setLong(2, System.currentTimeMillis()); ps.executeUpdate()
        }
        // Manter o historico enxuto: so as 24 buscas mais recentes.
        conn.createStatement().use { st ->
            st.execute(
                "delete from search_term where id not in " +
                    "(select id from search_term order by at desc limit 24)"
            )
        }
    }

    @Synchronized
    fun recentSearchTerms(limit: Int = 12): List<String> =
        conn.prepareStatement("select term from search_term order by at desc limit ?").use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }

    // ------------------------------------------------------------------ downloads
    @Synchronized
    fun addDownload(track: Track, path: String, size: Long) {
        conn.prepareStatement(
            """
            insert into download(id, title, artist, album, duration, thumbnail, path, size, at)
            values(?,?,?,?,?,?,?,?,?)
            on conflict(id) do update set
                path=excluded.path, size=excluded.size, at=excluded.at
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, track.id)
            ps.setString(2, track.title)
            ps.setString(3, track.artist)
            ps.setString(4, track.album)
            ps.setInt(5, track.durationSec)
            ps.setString(6, track.thumbnail)
            ps.setString(7, path)
            ps.setLong(8, size)
            ps.setLong(9, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun downloadPath(id: String): String? =
        conn.prepareStatement("select path from download where id=?").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    @Synchronized
    fun isDownloaded(id: String): Boolean =
        conn.prepareStatement("select 1 from download where id=?").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> rs.next() }
        }

    /** Remove o registro e devolve o caminho do arquivo para a exclusao. */
    @Synchronized
    fun removeDownload(id: String): String? {
        val path = downloadPath(id)
        conn.prepareStatement("delete from download where id=?").use { ps ->
            ps.setString(1, id); ps.executeUpdate()
        }
        return path
    }

    @Synchronized
    fun downloadedIds(): List<String> =
        conn.prepareStatement("select id from download order by at desc").use { ps ->
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }

    @Synchronized
    fun downloadedSize(): Long =
        conn.createStatement().use { st ->
            st.executeQuery("select coalesce(sum(size),0) from download").use { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
        }

    @Synchronized
    fun downloadedTracks(limit: Int = 500): List<Track> =
        conn.prepareStatement(
            "select id, title, artist, album, duration, thumbnail " +
                "from download order by at desc limit ?"
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            Track(
                                id = rs.getString("id"),
                                title = rs.getString("title"),
                                artist = rs.getString("artist"),
                                album = rs.getString("album") ?: "",
                                durationSec = rs.getInt("duration"),
                                thumbnail = rs.getString("thumbnail") ?: "",
                            )
                        )
                    }
                }
            }
        }

    // ------------------------------------------------------------------ helpers
    private fun queryTracks(sql: String, limit: Int): List<Track> =
        conn.prepareStatement(sql).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs -> readTracks(rs) }
        }

    private fun readTracks(rs: java.sql.ResultSet): List<Track> = buildList {
        while (rs.next()) {
            add(
                Track(
                    id = rs.getString("id"),
                    title = rs.getString("title"),
                    artist = rs.getString("artist"),
                    album = rs.getString("album") ?: "",
                    durationSec = rs.getInt("duration"),
                    thumbnail = rs.getString("thumbnail") ?: "",
                )
            )
        }
    }

    /**
     * Copia consistente do banco (incluindo o conteudo do WAL) via VACUUM INTO.
     * Grava num arquivo temporario e so substitui o backup anterior no fim.
     */
    @Synchronized
    fun backupTo(target: Path) {
        runCatching {
            Files.createDirectories(target.parent)
            val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
            Files.deleteIfExists(temp)
            val escaped = temp.toAbsolutePath().toString().replace("'", "''")
            conn.createStatement().use { st -> st.execute("vacuum into '$escaped'") }
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Synchronized
    override fun close() {
        runCatching { conn.close() }
    }
}
