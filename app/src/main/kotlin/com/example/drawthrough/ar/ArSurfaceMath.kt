package com.example.drawthrough.ar

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin 3D math utilities for anchoring, orienting, sizing, and dragging a planar
 * reference image quad on an ARCore surface. Free of Android/GL runtime dependencies so
 * every calculation can be thoroughly unit-tested on the JVM.
 */
object ArSurfaceMath {

    private const val MIN_QUAD_DIMENSION_METERS = 0.04f
    private const val MAX_QUAD_DIMENSION_METERS = 2.5f
    private const val MIN_RAY_PLANE_COSINE = 0.12f
    private const val MAX_RAY_INTERSECTION_METERS = 15f
    private const val MAX_SINGLE_DRAG_STEP_METERS = 0.75f

    data class Vec3(val x: Float, val y: Float, val z: Float) {
        operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
        operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
        operator fun times(scalar: Float) = Vec3(x * scalar, y * scalar, z * scalar)

        fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

        fun cross(other: Vec3): Vec3 = Vec3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x,
        )

        fun length(): Float = sqrt(x * x + y * y + z * z)

        fun normalizedOrNull(): Vec3? {
            val len = length()
            return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else null
        }

        companion object {
            fun fromArray(values: FloatArray, offset: Int = 0): Vec3 =
                Vec3(values[offset], values[offset + 1], values[offset + 2])
        }
    }

    /**
     * Computes the yaw angle (in degrees, right-handed rotation around the anchor's local +Y normal)
     * that rotates the anchor's local +X axis to align with the user's screen-right direction
     * projected onto the anchor's surface plane.
     *
     * After rotating the anchor frame by this yaw around +Y:
     * - Local +X points toward the right edge of the screen along the surface.
     * - Local +Y is the surface normal pointing toward the camera.
     * - Local +Z points toward the bottom edge of the screen along the surface (quadDown = quadRight x N).
     */
    fun computeSurfaceAlignedYawDeg(
        anchorXAxis: Vec3,
        anchorYAxis: Vec3,
        anchorZAxis: Vec3,
        cameraRightAxis: Vec3,
        cameraUpAxis: Vec3,
    ): Float {
        val normal = anchorYAxis.normalizedOrNull() ?: return 0f

        // Project screen-right onto the surface plane (perpendicular to normal)
        val projectedRight = (cameraRightAxis - normal * cameraRightAxis.dot(normal)).normalizedOrNull()
            ?: run {
                // Degenerate edge-on case: derive screen-right from screen-up x normal
                val projectedUp = (cameraUpAxis - normal * cameraUpAxis.dot(normal)).normalizedOrNull()
                    ?: return 0f
                projectedUp.cross(normal).normalizedOrNull() ?: return 0f
            }

        val localX = projectedRight.dot(anchorXAxis)
        val localZ = projectedRight.dot(anchorZAxis)

        // OpenGL rotateM(+theta, 0, 1, 0) maps (1, 0, 0) -> (cos(theta), 0, -sin(theta))
        val radians = atan2(-localZ, localX)
        return (radians * 180.0 / PI).toFloat()
    }

    /**
     * Computes the initial physical (widthMeters, heightMeters) of the anchored quad so it lands
     * at roughly the same visual size as the 2D screen-space overlay, preserving [bitmapWidth]/[bitmapHeight].
     */
    fun computeInitialQuadSizeMeters(
        distanceMeters: Float,
        projectionMatrix: FloatArray,
        bitmapWidth: Int,
        bitmapHeight: Int,
        fillFraction: Float = 0.80f,
    ): Pair<Float, Float> {
        val aspect = if (bitmapWidth > 0 && bitmapHeight > 0) {
            bitmapWidth.toFloat() / bitmapHeight.toFloat()
        } else {
            1f
        }

        val safeDistance = distanceMeters.coerceIn(0.12f, 4.0f)
        val p00 = abs(projectionMatrix.getOrElse(0) { 1.8f }).coerceAtLeast(0.25f)
        val p11 = abs(projectionMatrix.getOrElse(5) { 1.0f }).coerceAtLeast(0.25f)

        val visibleWidth = (2f * safeDistance) / p00
        val visibleHeight = (2f * safeDistance) / p11

        val maxW = visibleWidth * fillFraction
        val maxH = visibleHeight * fillFraction

        var width: Float
        var height: Float
        if (maxW / aspect <= maxH) {
            width = maxW
            height = maxW / aspect
        } else {
            height = maxH
            width = maxH * aspect
        }

        // Clamp overall scale cleanly while preserving aspect ratio
        val largest = max(width, height)
        if (largest > MAX_QUAD_DIMENSION_METERS) {
            val factor = MAX_QUAD_DIMENSION_METERS / largest
            width *= factor
            height *= factor
        }
        val smallest = min(width, height)
        if (smallest < MIN_QUAD_DIMENSION_METERS && smallest > 1e-6f) {
            val factor = MIN_QUAD_DIMENSION_METERS / smallest
            width *= factor
            height *= factor
        }

        return width to height
    }

    /**
     * Builds the 4x4 column-major model matrix placing a unit quad spanning [-0.5, +0.5] on X and Z
     * (at Y = 0) onto the ARCore anchor surface.
     *
     * Order: Anchor * RotY(baseYawDeg) * Translate(planeOffsetX, normalLift, planeOffsetZ)
     *        * RotY(-userRotationDeg) * Scale(widthMeters, 1, heightMeters)
     *
     * Note: Positive [userRotationDeg] rotates clockwise on screen (matching Compose rotationZ).
     */
    fun buildQuadModelMatrix(
        anchorMatrix: FloatArray,
        baseYawDeg: Float,
        planeOffsetX: Float,
        planeOffsetZ: Float,
        userRotationDeg: Float,
        widthMeters: Float,
        heightMeters: Float,
        normalLiftMeters: Float = 0.001f,
    ): FloatArray {
        val baseFrame = multiplyMatrix4x4(anchorMatrix, rotationYMatrix(baseYawDeg))
        val translated = multiplyMatrix4x4(baseFrame, translationMatrix(planeOffsetX, normalLiftMeters, planeOffsetZ))
        // Looking down from +Y onto X(right)-Z(down), clockwise screen rotation is negative right-hand Y rotation
        val rotated = multiplyMatrix4x4(translated, rotationYMatrix(-userRotationDeg))
        return multiplyMatrix4x4(rotated, scaleMatrix(widthMeters, 1f, heightMeters))
    }

    /**
     * Computes world-space (baseRight, baseDown) unit axes of the surface-aligned anchor frame.
     */
    fun computeBasePlaneAxes(anchorMatrix: FloatArray, baseYawDeg: Float): Pair<Vec3, Vec3> {
        val baseFrame = multiplyMatrix4x4(anchorMatrix, rotationYMatrix(baseYawDeg))
        val baseRight = Vec3(baseFrame[0], baseFrame[1], baseFrame[2]).normalizedOrNull()
            ?: Vec3(1f, 0f, 0f)
        val baseDown = Vec3(baseFrame[8], baseFrame[9], baseFrame[10]).normalizedOrNull()
            ?: Vec3(0f, 0f, 1f)
        return baseRight to baseDown
    }

    /**
     * Maps a 2D screen drag from ([prevScreenX], [prevScreenY]) to ([currScreenX], [currScreenY])
     * into (deltaPlaneX, deltaPlaneZ) in meters along the anchor's surface-aligned (baseRight, baseDown) axes.
     *
     * Uses exact ray-plane intersection when viewing the surface clearly, with a smooth fallback
     * to frustum-scaled camera axes at extreme grazing angles.
     */
    fun computePlaneDragDeltaMeters(
        prevScreenX: Float,
        prevScreenY: Float,
        currScreenX: Float,
        currScreenY: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        planeOrigin: Vec3,
        planeNormal: Vec3,
        baseRight: Vec3,
        baseDown: Vec3,
        fallbackDistanceMeters: Float,
    ): Pair<Float, Float> {
        if (viewportWidth <= 0 || viewportHeight <= 0) return 0f to 0f
        if (prevScreenX == currScreenX && prevScreenY == currScreenY) return 0f to 0f

        val normal = planeNormal.normalizedOrNull() ?: return 0f to 0f
        val vp = multiplyMatrix4x4(projectionMatrix, viewMatrix)
        val invVp = invertMatrix4x4(vp)

        if (invVp != null) {
            val hitPrev = intersectScreenRayWithPlane(
                screenX = prevScreenX,
                screenY = prevScreenY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                invViewProjection = invVp,
                planeOrigin = planeOrigin,
                planeNormal = normal,
            )
            val hitCurr = intersectScreenRayWithPlane(
                screenX = currScreenX,
                screenY = currScreenY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                invViewProjection = invVp,
                planeOrigin = planeOrigin,
                planeNormal = normal,
            )
            if (hitPrev != null && hitCurr != null) {
                val worldDelta = hitCurr - hitPrev
                if (worldDelta.length() <= MAX_SINGLE_DRAG_STEP_METERS) {
                    val dX = worldDelta.dot(baseRight)
                    val dZ = worldDelta.dot(baseDown)
                    if (dX.isFinite() && dZ.isFinite()) {
                        return dX to dZ
                    }
                }
            }
        }

        // Fallback for grazing angles: project screen-space displacement onto plane using camera basis
        val safeDist = fallbackDistanceMeters.coerceIn(0.12f, 4.0f)
        val p00 = abs(projectionMatrix.getOrElse(0) { 1.8f }).coerceAtLeast(0.25f)
        val p11 = abs(projectionMatrix.getOrElse(5) { 1.0f }).coerceAtLeast(0.25f)
        val dxWorld = ((currScreenX - prevScreenX) / viewportWidth.toFloat()) * (2f * safeDist / p00)
        val dyWorld = -((currScreenY - prevScreenY) / viewportHeight.toFloat()) * (2f * safeDist / p11)

        // Row 0 of viewMatrix is cameraRight in world space; Row 1 is cameraUp in world space
        val camRight = Vec3(viewMatrix[0], viewMatrix[4], viewMatrix[8])
        val camUp = Vec3(viewMatrix[1], viewMatrix[5], viewMatrix[9])
        val approxWorldDelta = camRight * dxWorld + camUp * dyWorld

        val dX = approxWorldDelta.dot(baseRight).coerceIn(-MAX_SINGLE_DRAG_STEP_METERS, MAX_SINGLE_DRAG_STEP_METERS)
        val dZ = approxWorldDelta.dot(baseDown).coerceIn(-MAX_SINGLE_DRAG_STEP_METERS, MAX_SINGLE_DRAG_STEP_METERS)
        return (if (dX.isFinite()) dX else 0f) to (if (dZ.isFinite()) dZ else 0f)
    }

    private fun intersectScreenRayWithPlane(
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        invViewProjection: FloatArray,
        planeOrigin: Vec3,
        planeNormal: Vec3,
    ): Vec3? {
        val ndcX = (2f * screenX / viewportWidth.toFloat()) - 1f
        val ndcY = 1f - (2f * screenY / viewportHeight.toFloat())

        val nearWorld = unprojectNdc(invViewProjection, ndcX, ndcY, -1f) ?: return null
        val farWorld = unprojectNdc(invViewProjection, ndcX, ndcY, 1f) ?: return null
        val rayDir = (farWorld - nearWorld).normalizedOrNull() ?: return null

        val denom = rayDir.dot(planeNormal)
        if (abs(denom) < MIN_RAY_PLANE_COSINE) return null

        val t = (planeOrigin - nearWorld).dot(planeNormal) / denom
        if (!t.isFinite() || t <= 0f || t > MAX_RAY_INTERSECTION_METERS) return null

        return nearWorld + rayDir * t
    }

    private fun unprojectNdc(invVp: FloatArray, ndcX: Float, ndcY: Float, ndcZ: Float): Vec3? {
        val x = invVp[0] * ndcX + invVp[4] * ndcY + invVp[8] * ndcZ + invVp[12]
        val y = invVp[1] * ndcX + invVp[5] * ndcY + invVp[9] * ndcZ + invVp[13]
        val z = invVp[2] * ndcX + invVp[6] * ndcY + invVp[10] * ndcZ + invVp[14]
        val w = invVp[3] * ndcX + invVp[7] * ndcY + invVp[11] * ndcZ + invVp[15]
        if (abs(w) < 1e-6f || !w.isFinite()) return null
        return Vec3(x / w, y / w, z / w)
    }

    fun identityMatrix(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    fun translationMatrix(tx: Float, ty: Float, tz: Float): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        tx, ty, tz, 1f,
    )

    fun scaleMatrix(sx: Float, sy: Float, sz: Float): FloatArray = floatArrayOf(
        sx, 0f, 0f, 0f,
        0f, sy, 0f, 0f,
        0f, 0f, sz, 0f,
        0f, 0f, 0f, 1f,
    )

    fun rotationYMatrix(angleDeg: Float): FloatArray {
        val rad = angleDeg * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        // Column-major right-handed rotation around +Y:
        // col0 = (c, 0, -s, 0), col1 = (0, 1, 0, 0), col2 = (s, 0, c, 0), col3 = (0, 0, 0, 1)
        return floatArrayOf(
            c, 0f, -s, 0f,
            0f, 1f, 0f, 0f,
            s, 0f, c, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    /** Multiplies two 4x4 column-major matrices: result = lhs * rhs. */
    fun multiplyMatrix4x4(lhs: FloatArray, rhs: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (col in 0 until 4) {
            val c = col * 4
            for (row in 0 until 4) {
                out[c + row] =
                    lhs[row] * rhs[c] +
                    lhs[4 + row] * rhs[c + 1] +
                    lhs[8 + row] * rhs[c + 2] +
                    lhs[12 + row] * rhs[c + 3]
            }
        }
        return out
    }

    /** Transforms a 3D point (x, y, z, 1) by a 4x4 column-major matrix. */
    fun transformPoint(matrix: FloatArray, point: Vec3): Vec3 {
        val x = matrix[0] * point.x + matrix[4] * point.y + matrix[8] * point.z + matrix[12]
        val y = matrix[1] * point.x + matrix[5] * point.y + matrix[9] * point.z + matrix[13]
        val z = matrix[2] * point.x + matrix[6] * point.y + matrix[10] * point.z + matrix[14]
        val w = matrix[3] * point.x + matrix[7] * point.y + matrix[11] * point.z + matrix[15]
        return if (abs(w) > 1e-6f && w != 1f) Vec3(x / w, y / w, z / w) else Vec3(x, y, z)
    }

    /** Inverts a 4x4 column-major matrix, returning null if singular. */
    fun invertMatrix4x4(m: FloatArray): FloatArray? {
        val inv = FloatArray(16)

        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
            m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
            m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
            m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
            m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]

        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
            m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
            m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
            m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
            m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]

        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
            m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
            m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
            m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
            m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]

        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
            m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
            m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
            m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
            m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]

        val det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (abs(det) < 1e-10f || !det.isFinite()) return null

        val invDet = 1f / det
        for (i in 0 until 16) {
            inv[i] *= invDet
        }
        return inv
    }
}
