package com.openfy.data

import com.openfy.core.PendingPlayEvent
import com.openfy.core.PlayEventRow
import com.openfy.core.Playlist
import com.openfy.core.RecapArtist
import com.openfy.core.RecapDay
import com.openfy.core.RecapSummary
import com.openfy.core.RecapTrack
import com.openfy.core.SyncLike
import com.openfy.core.SyncPlaylist
import com.openfy.core.SyncPlaylistItem
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
                st.execute("update playlist set updated_at=created_at where updated_at=0")
                st.execute("update liked set updated_at=at where updated_at=0")
                st.execute(
                    "update playlist_item set updated_at=" +
                        "coalesce((select created_at from playlist p where p.id=playlist_item.playlist_id), 0) " +
                        "where updated_at=0"
                )
                st.execute("pragma user_version=3")
            }
            if (version < 4) {
                // v4: sync com Supabase. O id remoto do item é necessário porque
                // o servidor usa UUID por item, enquanto o banco local usa
                // (playlist_id, track_id).
                if (!hasColumn("playlist_item", "remote_item_id")) {
                    st.execute("alter table playlist_item add column remote_item_id text")
                }
                st.execute(
                    "create table if not exists sync_state(" +
                        "k text primary key, v text not null)"
                )
                st.execute(
                    "create table if not exists sync_outbox(" +
                        "seq integer primary key autoincrement, " +
                        "entity text not null, op text not null, entity_id text not null, " +
                        "payload text not null, created_at integer not null)"
                )
                st.execute("pragma user_version=4")
            }
            if (version < 5) {
                // v5: capa remota no Storage. O caminho local continua em cover
                // para funcionar offline; cover_url é o que os outros aparelhos veem.
                if (!hasColumn("playlist", "cover_url")) {
                    st.execute("alter table playlist add column cover_url text not null default ''")
                }
                st.execute("pragma user_version=5")
            }
            if (version < 6) {
                // v6: eventos de reprodução completos para o recap na nuvem.
                if (!hasColumn("play_event", "played_ms")) {
                    st.execute("alter table play_event add column played_ms integer not null default 0")
                }
                if (!hasColumn("play_event", "completed")) {
                    st.execute("alter table play_event add column completed integer not null default 0")
                }
                if (!hasColumn("play_event", "skipped")) {
                    st.execute("alter table play_event add column skipped integer not null default 0")
                }
                if (!hasColumn("play_event", "source")) {
                    st.execute("alter table play_event add column source text not null default 'desktop'")
                }
                if (!hasColumn("play_event", "synced")) {
                    st.execute("alter table play_event add column synced integer not null default 0")
                }
                st.execute(
                    "update play_event set played_ms=coalesce(" +
                        "(select duration * 1000 from track t where t.id=play_event.id), 0) " +
                        "where played_ms=0"
                )
                st.execute("update play_event set completed=1 where completed=0 and skipped=0")
                st.execute("pragma user_version=6")
            }
            if (version < 7) {
                // v7: telemetria real de escuta (inicio/fim/skip/origem) e limpeza do DJ.
                if (!hasColumn("play_event", "duration_ms")) {
                    st.execute("alter table play_event add column duration_ms integer not null default 0")
                }
                if (!hasColumn("play_event", "event_id")) {
                    st.execute("alter table play_event add column event_id text not null default ''")
                }
                if (!hasColumn("play_event", "finished")) {
                    st.execute("alter table play_event add column finished integer not null default 0")
                }
                if (!hasColumn("play_event", "origin")) {
                    st.execute("alter table play_event add column origin text not null default 'desktop'")
                }
                if (!hasColumn("play_event", "context_id")) {
                    st.execute("alter table play_event add column context_id text")
                }
                if (!hasColumn("play_event", "context_title")) {
                    st.execute("alter table play_event add column context_title text not null default ''")
                }
                st.execute("update play_event set duration_ms=coalesce((select duration * 1000 from track t where t.id=play_event.id), 0) where duration_ms=0")
                st.execute("update play_event set finished=1 where finished=0")
                st.execute("update play_event set origin=source where origin='' or origin is null")
                st.execute("create index if not exists play_event_at_idx on play_event(at desc)")
                st.execute("create index if not exists play_event_track_at_idx on play_event(id, at desc)")
                st.execute("create index if not exists play_event_synced_idx on play_event(synced, seq)")
                st.execute("drop table if exists track_bpm")
                st.execute("delete from setting where k in ('playback.dj','playback.transition')")
                st.execute("pragma user_version=7")
            }
            if (version < 8) {
                // v8: idempotencia dos eventos e correcao do duration_ms dos eventos antigos.
                if (!hasColumn("play_event", "event_id")) {
                    st.execute("alter table play_event add column event_id text not null default ''")
                }
                st.execute("create index if not exists play_event_event_id_idx on play_event(event_id)")
                st.execute(
                    "update play_event set duration_ms=coalesce(" +
                        "(select duration * 1000 from track t where t.id=play_event.id), duration_ms) " +
                        "where duration_ms=0 or duration_ms=played_ms"
                )
                st.execute("pragma user_version=8")
            }
        }
    }

    private fun hasColumn(table: String, column: String): Boolean =
        conn.createStatement().use { st ->
            st.executeQuery("pragma table_info($table)").use { rs ->
                while (rs.next()) {
                    if (rs.getString("name") == column) return true
                }
                false
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

    @Synchronized
    fun tracksByIds(ids: Collection<String>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val list = ids.joinToString(",") { "'${it.replace("'", "''")}'" }
        return conn.createStatement().use { st ->
            st.executeQuery("select * from track where id in ($list)").use { rs -> readTracks(rs) }
        }
    }

    // ------------------------------------------------------------------ playlists
    @Synchronized
    fun playlists(): List<Playlist> =
        conn.prepareStatement(
            "select p.id, p.name, " +
                "(select count(*) from playlist_item i where i.playlist_id = p.id and i.deleted=0) as c, " +
                "coalesce(nullif(p.cover_url,''), nullif(p.cover,''), (" +
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
    fun setPlaylistCoverUrl(id: String, url: String) {
        conn.prepareStatement("update playlist set cover_url=?, updated_at=? where id=?").use { ps ->
            ps.setString(1, url); ps.setLong(2, System.currentTimeMillis()); ps.setString(3, id)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun playlistCover(id: String): String? =
        conn.prepareStatement(
            "select coalesce(nullif(cover_url,''), cover) from playlist where id=? and deleted=0"
        ).use { ps ->
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

    // ------------------------------------------------------------------ sync
    @Synchronized
    fun syncPlaylists(): List<SyncPlaylist> =
        conn.createStatement().use { st ->
            st.executeQuery("select id, name, updated_at, deleted, cover_url from playlist").use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SyncPlaylist(
                                id = rs.getString("id"),
                                name = rs.getString("name"),
                                updatedAt = rs.getLong("updated_at"),
                                deleted = rs.getInt("deleted") != 0,
                                coverUrl = rs.getString("cover_url") ?: "",
                            )
                        )
                    }
                }
            }
        }

    @Synchronized
    fun syncPlaylistItems(): List<SyncPlaylistItem> =
        conn.createStatement().use { st ->
            st.executeQuery(
                "select playlist_id, id, position, updated_at, deleted, remote_item_id from playlist_item"
            ).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SyncPlaylistItem(
                                playlistId = rs.getString("playlist_id"),
                                trackId = rs.getString("id"),
                                position = rs.getInt("position"),
                                updatedAt = rs.getLong("updated_at"),
                                deleted = rs.getInt("deleted") != 0,
                                remoteItemId = rs.getString("remote_item_id"),
                            )
                        )
                    }
                }
            }
        }

    @Synchronized
    fun syncLikes(): List<SyncLike> =
        conn.createStatement().use { st ->
            st.executeQuery("select id, updated_at, deleted from liked").use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SyncLike(
                                trackId = rs.getString("id"),
                                updatedAt = rs.getLong("updated_at"),
                                deleted = rs.getInt("deleted") != 0,
                            )
                        )
                    }
                }
            }
        }

    @Synchronized
    fun syncTracks(): List<Track> =
        conn.createStatement().use { st ->
            st.executeQuery(
                "select * from track where id in (" +
                    "select id from playlist_item union select id from liked union select id from play_event)"
            ).use { rs -> readTracks(rs) }
        }

    /** Aplica uma playlist remota se ela for mais nova que a local. */
    @Synchronized
    fun mergeRemotePlaylist(remote: SyncPlaylist) {
        conn.prepareStatement(
            """
            insert into playlist(id, name, created_at, cover, cover_url, updated_at, deleted)
            values(?,?,?,'',?,?,?)
            on conflict(id) do update set
                name=excluded.name,
                cover_url=excluded.cover_url,
                updated_at=excluded.updated_at,
                deleted=excluded.deleted
            where excluded.updated_at > playlist.updated_at
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, remote.id)
            ps.setString(2, remote.name)
            ps.setLong(3, remote.updatedAt)
            ps.setString(4, remote.coverUrl)
            ps.setLong(5, remote.updatedAt)
            ps.setInt(6, if (remote.deleted) 1 else 0)
            ps.executeUpdate()
        }
    }

    /** Aplica um item remoto; preserva o UUID do servidor para o próximo push. */
    @Synchronized
    fun mergeRemotePlaylistItem(remote: SyncPlaylistItem) {
        conn.prepareStatement(
            """
            insert into playlist_item(
                playlist_id, id, position, updated_at, deleted, remote_item_id
            ) values(?,?,?,?,?,?)
            on conflict(playlist_id, id) do update set
                position=excluded.position,
                updated_at=excluded.updated_at,
                deleted=excluded.deleted,
                remote_item_id=coalesce(excluded.remote_item_id, playlist_item.remote_item_id)
            where excluded.updated_at > playlist_item.updated_at
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, remote.playlistId)
            ps.setString(2, remote.trackId)
            ps.setInt(3, remote.position)
            ps.setLong(4, remote.updatedAt)
            ps.setInt(5, if (remote.deleted) 1 else 0)
            ps.setString(6, remote.remoteItemId)
            ps.executeUpdate()
        }
    }

    @Synchronized
    fun mergeRemoteLike(remote: SyncLike) {
        conn.prepareStatement(
            """
            insert into liked(id, at, updated_at, deleted) values(?,?,?,?)
            on conflict(id) do update set
                updated_at=excluded.updated_at,
                deleted=excluded.deleted
            where excluded.updated_at > liked.updated_at
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, remote.trackId)
            ps.setLong(2, remote.updatedAt)
            ps.setLong(3, remote.updatedAt)
            ps.setInt(4, if (remote.deleted) 1 else 0)
            ps.executeUpdate()
        }
    }

    /** Guarda o UUID que o servidor deu a um item local recém-enviado. */
    @Synchronized
    fun setRemoteItemId(playlistId: String, trackId: String, remoteItemId: String) {
        conn.prepareStatement(
            "update playlist_item set remote_item_id=? where playlist_id=? and id=?"
        ).use { ps ->
            ps.setString(1, remoteItemId)
            ps.setString(2, playlistId)
            ps.setString(3, trackId)
            ps.executeUpdate()
        }
    }

    // ------------------------------------------------------------------ history
    /** Registro de compatibilidade: uma escuta completa (usado por imports e smokes). */
    @Synchronized
    fun recordPlay(id: String) {
        val now = System.currentTimeMillis()
        conn.prepareStatement(
            "insert into play_event(event_id, id, at, played_ms, duration_ms, completed, skipped, source, " +
                "origin, context_id, context_title, finished, synced) " +
                "values(?, ?, ?, coalesce((select duration from track where id=?), 0) * 1000, " +
                "coalesce((select duration from track where id=?), 0) * 1000, 1, 0, 'desktop', " +
                "'desktop', null, '', 1, 0)"
        ).use { ps ->
            ps.setString(1, UUID.randomUUID().toString())
            ps.setString(2, id); ps.setLong(3, now)
            ps.setString(4, id); ps.setString(5, id)
            ps.executeUpdate()
        }
    }

    /** Abre um evento de escuta; deve ser fechado por finishPlay ao trocar/parar. */
    @Synchronized
    fun beginPlay(
        track: Track,
        origin: String,
        contextId: String?,
        contextTitle: String,
        at: Long = System.currentTimeMillis(),
    ): Long {
        remember(track)
        conn.prepareStatement(
            "insert into play_event(event_id, id, at, played_ms, duration_ms, completed, skipped, source, " +
                "origin, context_id, context_title, finished, synced) " +
                "values(?, ?, ?, 0, ?, 0, 0, ?, ?, ?, ?, 0, 0)"
        ).use { ps ->
            ps.setString(1, UUID.randomUUID().toString())
            ps.setString(2, track.id)
            ps.setLong(3, at)
            ps.setInt(4, track.durationSec.coerceAtLeast(0) * 1000)
            ps.setString(5, origin)
            ps.setString(6, origin)
            ps.setString(7, contextId)
            ps.setString(8, contextTitle)
            ps.executeUpdate()
        }
        return conn.createStatement().use { st ->
            st.executeQuery("select last_insert_rowid()").use { rs ->
                if (rs.next()) rs.getLong(1) else -1L
            }
        }
    }

    @Synchronized
    fun finishPlay(
        seq: Long,
        playedMs: Int,
        completed: Boolean,
        skipped: Boolean,
        durationMs: Int? = null,
    ) {
        if (seq < 0) return
        conn.prepareStatement(
            "update play_event set played_ms=?, duration_ms=coalesce(?, duration_ms), " +
                "completed=?, skipped=?, finished=1 where seq=?"
        ).use { ps ->
            ps.setInt(1, playedMs.coerceAtLeast(0))
            if (durationMs != null && durationMs > 0) ps.setInt(2, durationMs) else ps.setNull(2, java.sql.Types.INTEGER)
            ps.setInt(3, if (completed) 1 else 0)
            ps.setInt(4, if (skipped) 1 else 0)
            ps.setLong(5, seq)
            ps.executeUpdate()
        }
    }

    /** Eventos finalizados e ainda n?o enviados ao Supabase. */
    @Synchronized
    fun pendingPlayEvents(limit: Int = 200): List<PendingPlayEvent> =
        conn.prepareStatement(
            "select seq, event_id, id, at, played_ms, duration_ms, completed, skipped, origin, " +
                "context_id, context_title from play_event " +
                "where synced=0 and finished=1 order by seq asc limit ?"
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            PendingPlayEvent(
                                seq = rs.getInt("seq"),
                                eventId = rs.getString("event_id") ?: "",
                                trackId = rs.getString("id"),
                                startedAt = rs.getLong("at"),
                                playedMs = rs.getInt("played_ms"),
                                durationMs = rs.getInt("duration_ms"),
                                completed = rs.getInt("completed") != 0,
                                skipped = rs.getInt("skipped") != 0,
                                origin = rs.getString("origin") ?: "desktop",
                                contextId = rs.getString("context_id"),
                                contextTitle = rs.getString("context_title") ?: "",
                            )
                        )
                    }
                }
            }
        }

    // ------------------------------------------------------------------ recap
    @Synchronized
    fun recapMonthKeys(): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery(
                "select strftime('%Y-%m', at / 1000, 'unixepoch', 'localtime') as month " +
                    "from play_event where finished=1 group by month order by month desc"
            ).use { rs ->
                buildList { while (rs.next()) add(rs.getString("month")) }
            }
        }

    @Synchronized
    fun recapSummary(startMs: Long, endMs: Long): RecapSummary =
        conn.prepareStatement(
            "select count(*) as plays, " +
                "coalesce(sum(coalesce(nullif(e.played_ms,0), coalesce(t.duration,0) * 1000)),0) as played_ms, " +
                "count(distinct e.id) as unique_tracks, " +
                "count(distinct nullif(t.artist,'')) as unique_artists, " +
                "coalesce(sum(case when e.completed=1 then 1 else 0 end),0) as completed_plays, " +
                "coalesce(sum(case when e.skipped=1 then 1 else 0 end),0) as skipped_plays, " +
                "min(e.at) as first_at, max(e.at) as last_at " +
                "from play_event e left join track t on t.id=e.id " +
                "where e.finished=1 and e.at >= ? and e.at < ?"
        ).use { ps ->
            ps.setLong(1, startMs); ps.setLong(2, endMs)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return RecapSummary()
                RecapSummary(
                    plays = rs.getInt("plays"),
                    playedMs = rs.getLong("played_ms"),
                    uniqueTracks = rs.getInt("unique_tracks"),
                    uniqueArtists = rs.getInt("unique_artists"),
                    completedPlays = rs.getInt("completed_plays"),
                    skippedPlays = rs.getInt("skipped_plays"),
                    firstAt = rs.getLong("first_at").takeIf { !rs.wasNull() },
                    lastAt = rs.getLong("last_at").takeIf { !rs.wasNull() },
                )
            }
        }

    @Synchronized
    fun recapTopTracks(startMs: Long, endMs: Long, limit: Int = 10): List<RecapTrack> =
        conn.prepareStatement(
            "select t.id, t.title, t.artist, t.album, t.duration, t.thumbnail, count(*) as plays, " +
                "coalesce(sum(coalesce(nullif(e.played_ms,0), coalesce(t.duration,0) * 1000)),0) as played_ms " +
                "from play_event e join track t on t.id=e.id " +
                "where e.finished=1 and e.at >= ? and e.at < ? " +
                "group by t.id order by plays desc, played_ms desc, t.title asc limit ?"
        ).use { ps ->
            ps.setLong(1, startMs); ps.setLong(2, endMs); ps.setInt(3, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RecapTrack(
                                Track(
                                    id = rs.getString("id"),
                                    title = rs.getString("title"),
                                    artist = rs.getString("artist"),
                                    album = rs.getString("album") ?: "",
                                    durationSec = rs.getInt("duration"),
                                    thumbnail = rs.getString("thumbnail") ?: "",
                                ),
                                plays = rs.getInt("plays"),
                                playedMs = rs.getLong("played_ms"),
                            )
                        )
                    }
                }
            }
        }

    @Synchronized
    fun recapTopArtists(startMs: Long, endMs: Long, limit: Int = 8): List<RecapArtist> =
        conn.prepareStatement(
            "select coalesce(nullif(t.artist,''),'Artista desconhecido') as artist, count(*) as plays, " +
                "coalesce(sum(coalesce(nullif(e.played_ms,0), coalesce(t.duration,0) * 1000)),0) as played_ms, " +
                "min(t.thumbnail) as thumbnail " +
                "from play_event e join track t on t.id=e.id " +
                "where e.finished=1 and e.at >= ? and e.at < ? " +
                "group by artist order by plays desc, played_ms desc limit ?"
        ).use { ps ->
            ps.setLong(1, startMs); ps.setLong(2, endMs); ps.setInt(3, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RecapArtist(
                                name = rs.getString("artist"),
                                plays = rs.getInt("plays"),
                                playedMs = rs.getLong("played_ms"),
                                thumbnail = rs.getString("thumbnail") ?: "",
                            )
                        )
                    }
                }
            }
        }

    @Synchronized
    fun recapDays(startMs: Long, endMs: Long): List<RecapDay> =
        conn.prepareStatement(
            "select date(e.at / 1000, 'unixepoch', 'localtime') as day, count(*) as plays, " +
                "coalesce(sum(coalesce(nullif(e.played_ms,0), coalesce(t.duration,0) * 1000)),0) as played_ms " +
                "from play_event e left join track t on t.id=e.id " +
                "where e.finished=1 and e.at >= ? and e.at < ? group by day order by day asc"
        ).use { ps ->
            ps.setLong(1, startMs); ps.setLong(2, endMs)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RecapDay(
                                day = rs.getString("day"),
                                plays = rs.getInt("plays"),
                                playedMs = rs.getLong("played_ms"),
                            )
                        )
                    }
                }
            }
        }

    @Synchronized
    fun recapFirstTimeTracks(startMs: Long, endMs: Long, limit: Int = 8): List<RecapTrack> =
        conn.prepareStatement(
            "select t.id, t.title, t.artist, t.album, t.duration, t.thumbnail, count(*) as plays, " +
                "coalesce(sum(e.played_ms),0) as played_ms " +
                "from play_event e join track t on t.id=e.id " +
                "where e.finished=1 and e.at >= ? and e.at < ? and not exists (" +
                "  select 1 from play_event p where p.id=e.id and p.finished=1 and p.at < ?" +
                ") group by t.id order by plays desc limit ?"
        ).use { ps ->
            ps.setLong(1, startMs); ps.setLong(2, endMs); ps.setLong(3, startMs); ps.setInt(4, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            RecapTrack(
                                Track(
                                    id = rs.getString("id"),
                                    title = rs.getString("title"),
                                    artist = rs.getString("artist"),
                                    album = rs.getString("album") ?: "",
                                    durationSec = rs.getInt("duration"),
                                    thumbnail = rs.getString("thumbnail") ?: "",
                                ),
                                plays = rs.getInt("plays"),
                                playedMs = rs.getLong("played_ms"),
                            )
                        )
                    }
                }
            }
        }

    @Synchronized
    fun recapLiked(startMs: Long, endMs: Long): Int =
        conn.prepareStatement(
            "select count(*) from liked where deleted=0 and at >= ? and at < ?"
        ).use { ps ->
            ps.setLong(1, startMs); ps.setLong(2, endMs)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

    /** Eventos brutos recentes para recomenda??o, recap e no-repeat. */
    @Synchronized
    fun playEventsSince(since: Long, limit: Int = 5000): List<PlayEventRow> =
        conn.prepareStatement(
            "select id, at, played_ms, duration_ms, completed, skipped, origin, context_id, " +
                "context_title from play_event where finished=1 and at >= ? " +
                "order by at desc limit ?"
        ).use { ps ->
            ps.setLong(1, since)
            ps.setInt(2, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            PlayEventRow(
                                trackId = rs.getString("id"),
                                at = rs.getLong("at"),
                                playedMs = rs.getInt("played_ms"),
                                durationMs = rs.getInt("duration_ms"),
                                completed = rs.getInt("completed") != 0,
                                skipped = rs.getInt("skipped") != 0,
                                origin = rs.getString("origin") ?: "desktop",
                                contextId = rs.getString("context_id"),
                                contextTitle = rs.getString("context_title") ?: "",
                            )
                        )
                    }
                }
            }
        }

    @Synchronized
    fun markPlayEventsSynced(seqs: List<Int>) {
        if (seqs.isEmpty()) return
        val list = seqs.joinToString(",") { it.toString() }
        conn.createStatement().use { st ->
            st.executeUpdate("update play_event set synced=1 where seq in ($list)")
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
