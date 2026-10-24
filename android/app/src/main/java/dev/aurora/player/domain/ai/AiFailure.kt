package dev.aurora.player.domain.ai

/**
 * Why an AI request failed, in terms the UI can act on.
 *
 * The gateway reports a stable error code for each of these. Before it did, every failure
 * reached this app as "HTTP 500", and the AI screen printed that verbatim - so "you haven't
 * connected the gateway" and "the provider is busy" looked identical to the person using it.
 */
enum class AiFailureReason {
    /** No gateway token saved on this device, or the gateway rejected the one that is. */
    NotConnected,

    /** The gateway itself has no access token configured, so it refuses every request. */
    GatewayNotConfigured,

    /** The gateway is up but has no key for the chosen AI provider. */
    AiNotConfigured,

    /** The gateway's own request budget is spent for this minute. */
    RateLimited,

    /** The AI provider is overloaded or rate limiting the gateway. */
    ProviderBusy,

    /** The gateway could not be reached at all. */
    Unreachable,

    /**
     * The model answered with something the gateway would not pass on: a tool it was never
     * offered, arguments of the wrong shape, or no text at all.
     *
     * Its own category because it is not a fault the person can do anything about, and it
     * is the one failure that means the allowlist did its job.
     */
    RefusedByGateway,

    /** The request was rejected before it reached a provider: too large, or malformed. */
    RequestRejected,

    /** Anything else: a malformed response, an outage, a bug. */
    Unexpected;

    companion object {
        /** Maps the gateway's `error.code`. Unknown codes are Unexpected, never guessed. */
        fun fromGatewayCode(code: String?): AiFailureReason = when (code) {
            "unauthorized" -> NotConnected
            "gateway_not_configured" -> GatewayNotConfigured
            "ai_not_configured" -> AiNotConfigured
            "rate_limited" -> RateLimited
            "provider_busy" -> ProviderBusy
            "provider_unavailable" -> ProviderBusy
            "invalid_provider_response" -> RefusedByGateway
            "payload_too_large" -> RequestRejected
            "invalid_request" -> RequestRejected
            else -> Unexpected
        }
    }
}

class AiGatewayException(val reason: AiFailureReason, cause: Throwable? = null) :
    Exception("AI request failed: $reason", cause)
