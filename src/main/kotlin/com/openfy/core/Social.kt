package com.openfy.core

import com.openfy.data.RemoteProfile
import kotlinx.serialization.Serializable

/**
 * Snapshot social publicado em `activities` (type = social_state, is_public).
 *
 * Uma unica linha por publicacao evita varias tabelas novas no Supabase e
 * mantem o banco enxuto: o que esta tocando agora e as ultimas curtidas viajam
 * juntos. Quem le (seguidores) enxerga apenas o snapshot mais recente.
 */
@Serializable
data class SocialPayload(
    val track: Track? = null,
    val playing: Boolean = false,
    val at: Long = 0L,
    val likes: List<Track> = emptyList(),
)

/** Estado local usado para publicar o feed social. */
data class LocalSocialState(
    val track: Track?,
    val playing: Boolean,
    val likes: List<Track>,
)

/** O que um amigo esta ouvindo/curtiu, pronto para a tela Comunidade. */
data class FriendStatus(
    val profile: RemoteProfile,
    val track: Track?,
    val playing: Boolean,
    val at: Long,
    val likes: List<Track>,
)
