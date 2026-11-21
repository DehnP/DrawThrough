package com.example.drawthrough.ar

/**
 * Lifecycle and tracking state of the optional ARCore surface-anchoring mode.
 */
enum class ArSurfaceState {
    /** AR surface lock is turned off; using low-latency CameraX feed. */
    INACTIVE,

    /** Checking ARCore availability and starting the ARCore camera session. */
    INITIALIZING,

    /** ARCore is running and scanning for the paper or desk surface to anchor onto. */
    SEARCHING,

    /** Reference image is anchored in 3D space onto the detected surface. */
    ANCHORED,

    /** Anchor exists, but ARCore temporarily lost tracking (e.g. too dark or featureless). */
    TRACKING_LIMITED,

    /** Device does not support ARCore or Google Play Services for AR was declined/unavailable. */
    UNAVAILABLE,
}

data class ArSurfaceStatus(
    val state: ArSurfaceState = ArSurfaceState.INACTIVE,
    val hint: String? = null,
    val isApproximateAnchor: Boolean = false,
) {
    val isActivelyAnchored: Boolean
        get() = state == ArSurfaceState.ANCHORED || state == ArSurfaceState.TRACKING_LIMITED
}
