package com.openfy.core

import com.openfy.android.data.Profile
import kotlinx.serialization.Serializable

/** Snapshot social publicado em `activities` (type = social_state). */
@Serializable
data class SocialPayload(
    val track: Track? = null,
    val playing: Boolean = false,
    val at: Long = 0L,
    val likes: List<Track> = emptyList(),
)

/** O que um amigo esta ouvindo/curtiu, pronto para a tela Comunidade. */
data class FriendStatus(
    val profile: Profile,
    val track: Track?,
    val playing: Boolean,
    val at: Long,
    val likes: List<Track>,
)
