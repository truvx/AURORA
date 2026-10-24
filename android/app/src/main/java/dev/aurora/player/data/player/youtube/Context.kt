package dev.aurora.player.data.player.youtube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * InnerTube request context.
 *
 * Ported from NomaTune (GPL-3.0, github.com/Shahdullah/NomaTune),
 * `innertube/models/Context.kt`.
 */
@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
    @SerialName("request")
    private val request: Request = Request(),
    @SerialName("user")
    private val user: User = User(),
) {
    @Serializable
    data class Client(
        val clientName: String,
        val clientVersion: String,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: String? = null,
        val gl: String,
        val hl: String,
        val visitorData: String?,
    )

    @Serializable
    data class ThirdParty(
        val embedUrl: String,
    )

    @Serializable
    data class Request(
        val internalExperimentFlags: Array<String> = emptyArray(),
        val useSsl: Boolean = true,
    )

    @Serializable
    data class User(
        val lockedSafetyMode: Boolean = false,
        val onBehalfOfUser: String? = null,
    )
}
