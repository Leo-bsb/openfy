package com.openfy.dev

import com.openfy.core.StoredSession
import com.openfy.core.SyncEngine
import com.openfy.core.Track
import com.openfy.data.Db
import com.openfy.data.SupabaseApi
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.system.exitProcess

/**
 * Teste headless do sync desktop contra o Supabase real.
 * Requer OPENFY_TEST_EMAIL/OPENFY_TEST_PASSWORD ou usa a conta de teste local.
 */
fun main() {
    val email = System.getenv("OPENFY_TEST_EMAIL") ?: "openfytest20261026@example.com"
    val password = System.getenv("OPENFY_TEST_PASSWORD") ?: "OpenfyTest123"
    val temp = Files.createTempDirectory("openfy-sync-smoke")
    val api = SupabaseApi()
    var failures = 0

    fun check(ok: Boolean, message: String) {
        println((if (ok) "OK   " else "FAIL ") + message)
        if (!ok) failures++
    }

    try {
        runBlocking {
            val auth = api.signIn(email, password)
            val userId = auth.user?.id ?: error("login sem user id")
            val session = StoredSession(
                accessToken = auth.accessToken,
                refreshToken = auth.refreshToken,
                userId = userId,
                email = email,
                expiresAt = System.currentTimeMillis() + 3_600_000,
            )
            val db = Db(temp.resolve("openfy.db"))
            try {
                val local = db.createPlaylist("Smoke PC")
                val track = Track("rA8xvaObO3c", "COPYCAT", "Billie Eilish", durationSec = 197)
                db.addToPlaylist(local.id, track)
                db.like(track.id)
                val coverUrl = api.uploadCover(
                    auth.accessToken,
                    userId,
                    local.id,
                    java.io.File("src/main/resources/logo.png"),
                )
                check(coverUrl.startsWith("https://"), "capa do PC subiu para o Storage")
                db.setPlaylistCoverUrl(local.id, coverUrl)
                db.recordPlay(track.id)

                val result = SyncEngine(db, api).sync(session)
                println(result.summary())
                check(result.pushedEvents >= 1, "evento de escuta do PC subiu para o recap")

                val remotePlaylist = api.playlists(auth.accessToken, listOf(local.id)).firstOrNull()
                check(remotePlaylist?.name == "Smoke PC", "playlist do PC subiu para o servidor")
                check(
                    remotePlaylist?.coverUrl == coverUrl,
                    "capa do PC subiu junto com a playlist",
                )

                val remoteItems = api.items(auth.accessToken, listOf(local.id))
                check(remoteItems.any { it.trackId == track.id && !it.deleted }, "item do PC subiu")

                val remoteLikes = api.likes(auth.accessToken)
                check(remoteLikes.any { it.trackId == track.id && !it.deleted }, "curtida do PC subiu")

                val names = db.playlists().map { it.name }
                println("playlists locais: $names")
                check(names.contains("TesteAndroid"), "playlist criada no Android apareceu no PC")
            } finally {
                db.close()
            }
        }
    } catch (error: Exception) {
        check(false, "sync: ${error::class.simpleName}: ${error.message}")
    } finally {
        api.close()
        runCatching { temp.toFile().deleteRecursively() }
    }

    println("\n== resultado: ${if (failures == 0) "TUDO OK" else "$failures FALHAS"} ==")
    exitProcess(if (failures == 0) 0 else 2)
}
