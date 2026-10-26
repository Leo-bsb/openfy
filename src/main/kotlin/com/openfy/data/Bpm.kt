package com.openfy.data

import com.openfy.core.Importer
import com.openfy.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * BPM editorial a partir da metadata publica do Deezer. Usado apenas para o
 * modo DJ (similaridade de andamento); a busca/catalogo/reproducao segue 100% YTM.
 * Se o Deezer nao tiver o BPM, devolve null e o DJ mantem a ordem da radio.
 */
object BpmClient {

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(track: Track): Double? = withContext(Dispatchers.IO) {
        val artist = track.artist.substringBefore("•").substringBefore(",").trim()
        val query = "$artist ${track.title}".trim()
        if (query.isBlank()) return@withContext null

        val search = get("https://api.deezer.com/search?q=${enc(query)}&limit=5")
            ?: return@withContext null
        val data = runCatching {
            json.parseToJsonElement(search).jsonObject["data"]?.jsonArray
        }.getOrNull() ?: return@withContext null

        val chosen = data.asSequence()
            .mapNotNull { item -> runCatching { item.jsonObject }.getOrNull() }
            .firstOrNull { item -> matches(track, item) }
            ?: return@withContext null

        val id = chosen["id"]?.jsonPrimitive?.intOrNull ?: return@withContext null
        val detail = get("https://api.deezer.com/track/$id") ?: return@withContext null
        val obj = runCatching { json.parseToJsonElement(detail).jsonObject }.getOrNull()
            ?: return@withContext null
        val bpm = obj["bpm"]?.jsonPrimitive?.doubleOrNull
        if (bpm != null && bpm > 20.0) bpm else null
    }

    private fun matches(track: Track, item: JsonObject): Boolean {
        val candidateTitle = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val candidateArtist = item["artist"]?.jsonObject
            ?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
        val candidateDuration = item["duration"]?.jsonPrimitive?.intOrNull ?: 0

        val wantedTitle = Importer.normalize(track.title)
        val foundTitle = Importer.normalize(candidateTitle)
        val titleOk = wantedTitle.isNotEmpty() &&
            (foundTitle.contains(wantedTitle) || wantedTitle.contains(foundTitle))

        val wantedArtist = Importer.normalize(
            track.artist.substringBefore("•").substringBefore(",").trim(),
        )
        val foundArtist = Importer.normalize(candidateArtist)
        val artistOk = wantedArtist.isEmpty() ||
            foundArtist.contains(wantedArtist) || wantedArtist.contains(foundArtist)

        val durationOk = track.durationSec <= 0 || candidateDuration <= 0 ||
            kotlin.math.abs(candidateDuration - track.durationSec) <= 8

        return titleOk && artistOk && durationOk
    }

    private fun get(url: String): String? = runCatching {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "Openfy/0.1")
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() in 200..299) response.body() else null
    }.getOrNull()

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
