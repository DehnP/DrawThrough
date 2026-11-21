package com.example.drawthrough.ar

import com.example.drawthrough.ar.ArSurfaceMath.Vec3
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class ArSurfaceMathTest {

    @Test
    fun surfaceAlignedYaw_horizontalDesk_alignsWithCameraRightAcrossOrientations() {
        val anchorX = Vec3(1f, 0f, 0f)
        val anchorY = Vec3(0f, 1f, 0f)
        val anchorZ = Vec3(0f, 0f, 1f)

        // Phone flat looking straight down, top pointing North (-Z), right pointing East (+X)
        val yawNorth = ArSurfaceMath.computeSurfaceAlignedYawDeg(
            anchorXAxis = anchorX,
            anchorYAxis = anchorY,
            anchorZAxis = anchorZ,
            cameraRightAxis = Vec3(1f, 0f, 0f),
            cameraUpAxis = Vec3(0f, 0f, -1f),
        )
        assertEquals(0f, yawNorth, 1e-3f)

        // Phone rotated 90 deg CCW on desk: top pointing West (-X), right pointing North (-Z)
        val yawWest = ArSurfaceMath.computeSurfaceAlignedYawDeg(
            anchorXAxis = anchorX,
            anchorYAxis = anchorY,
            anchorZAxis = anchorZ,
            cameraRightAxis = Vec3(0f, 0f, -1f),
            cameraUpAxis = Vec3(-1f, 0f, 0f),
        )
        assertEquals(90f, yawWest, 1e-3f)

        // Phone rotated 90 deg CW on desk: top pointing East (+X), right pointing South (+Z)
        val yawEast = ArSurfaceMath.computeSurfaceAlignedYawDeg(
            anchorXAxis = anchorX,
            anchorYAxis = anchorY,
            anchorZAxis = anchorZ,
            cameraRightAxis = Vec3(0f, 0f, 1f),
            cameraUpAxis = Vec3(1f, 0f, 0f),
        )
        assertEquals(-90f, yawEast, 1e-3f)
    }

    @Test
    fun surfaceAlignedYaw_arbitraryAnchorOrientation_mapsLocalXToProjectedScreenRight() {
        // Anchor frame rotated 37 degrees in world XZ plane
        val anchorMatrix = ArSurfaceMath.rotationYMatrix(37f)
        val anchorX = Vec3(anchorMatrix[0], anchorMatrix[1], anchorMatrix[2])
        val anchorY = Vec3(anchorMatrix[4], anchorMatrix[5], anchorMatrix[6])
        val anchorZ = Vec3(anchorMatrix[8], anchorMatrix[9], anchorMatrix[10])

        // Tilted camera looking down at an angle with cameraRight pointing roughly (-0.6, 0.2, 0.77)
        val camRight = Vec3(-0.6f, 0.2f, 0.7745967f).normalizedOrNull()!!
        val camUp = Vec3(0f, 0.7f, -0.7f).normalizedOrNull()!!

        val yawDeg = ArSurfaceMath.computeSurfaceAlignedYawDeg(
            anchorXAxis = anchorX,
            anchorYAxis = anchorY,
            anchorZAxis = anchorZ,
            cameraRightAxis = camRight,
            cameraUpAxis = camUp,
        )

        val (baseRight, baseDown) = ArSurfaceMath.computeBasePlaneAxes(anchorMatrix, yawDeg)
        val expectedRight = (camRight - anchorY * camRight.dot(anchorY)).normalizedOrNull()!!
        val expectedDown = expectedRight.cross(anchorY).normalizedOrNull()!!

        assertVec3Equals(expectedRight, baseRight, 1e-3f)
        assertVec3Equals(expectedDown, baseDown, 1e-3f)
    }

    @Test
    fun surfaceAlignedYaw_verticalWall_alignsUprightWithScreen() {
        // Vertical wall facing South (+Z): normal = (0, 0, 1), local X = (1, 0, 0), local Z = (0, -1, 0)
        // Verify right-handed: X x Y = (1,0,0) x (0,0,1) = (0,-1,0) = Z
        val anchorX = Vec3(1f, 0f, 0f)
        val anchorY = Vec3(0f, 0f, 1f)
        val anchorZ = Vec3(0f, -1f, 0f)

        val yaw = ArSurfaceMath.computeSurfaceAlignedYawDeg(
            anchorXAxis = anchorX,
            anchorYAxis = anchorY,
            anchorZAxis = anchorZ,
            cameraRightAxis = Vec3(1f, 0f, 0f),
            cameraUpAxis = Vec3(0f, 1f, 0f),
        )
        assertEquals(0f, yaw, 1e-3f)
    }

    @Test
    fun surfaceAlignedYaw_degenerateEdgeOnCamera_fallsBackCleanly() {
        val yaw = ArSurfaceMath.computeSurfaceAlignedYawDeg(
            anchorXAxis = Vec3(1f, 0f, 0f),
            anchorYAxis = Vec3(0f, 1f, 0f),
            anchorZAxis = Vec3(0f, 0f, 1f),
            cameraRightAxis = Vec3(0f, 1f, 0f), // parallel to surface normal
            cameraUpAxis = Vec3(0f, 0f, -1f),
        )
        assertTrue(yaw.isFinite())
    }

    @Test
    fun buildQuadModelMatrix_clockwiseUserRotationAndLocalPivot() {
        val anchor = ArSurfaceMath.identityMatrix()

        // Pan 0.1m right, 0.2m down, rotate 90 degrees clockwise on screen, size 0.4m x 0.2m
        val model = ArSurfaceMath.buildQuadModelMatrix(
            anchorMatrix = anchor,
            baseYawDeg = 0f,
            planeOffsetX = 0.10f,
            planeOffsetZ = 0.20f,
            userRotationDeg = 90f,
            widthMeters = 0.40f,
            heightMeters = 0.20f,
            normalLiftMeters = 0.001f,
        )

        // Center (0, 0, 0) must land at panned center (0.10, 0.001, 0.20) — NOT orbited around origin
        val center = ArSurfaceMath.transformPoint(model, Vec3(0f, 0f, 0f))
        assertVec3Equals(Vec3(0.10f, 0.001f, 0.20f), center, 1e-4f)

        // Right-edge midpoint (+0.5, 0, 0) has half-width 0.20m; rotated 90 deg CW on screen
        // moves right (+X) toward screen-down (+Z) -> (0.10, 0.001, 0.20 + 0.20)
        val rightMid = ArSurfaceMath.transformPoint(model, Vec3(0.5f, 0f, 0f))
        assertVec3Equals(Vec3(0.10f, 0.001f, 0.40f), rightMid, 1e-4f)

        // Top-edge midpoint (0, 0, -0.5) has half-height 0.10m; rotated 90 deg CW on screen
        // moves screen-up (-Z) toward screen-right (+X) -> (0.10 + 0.10, 0.001, 0.20)
        val topMid = ArSurfaceMath.transformPoint(model, Vec3(0f, 0f, -0.5f))
        assertVec3Equals(Vec3(0.20f, 0.001f, 0.20f), topMid, 1e-4f)
    }

    @Test
    fun computeInitialQuadSizeMeters_preservesAspectRatioAndClampsBounds() {
        val proj = perspectiveMatrix(fovYDeg = 60f, aspect = 0.5f, near = 0.05f, far = 20f)

        // Landscape 4:3 photo at 0.40m distance
        val (wLand, hLand) = ArSurfaceMath.computeInitialQuadSizeMeters(
            distanceMeters = 0.40f,
            projectionMatrix = proj,
            bitmapWidth = 1600,
            bitmapHeight = 1200,
        )
        assertEquals(4f / 3f, wLand / hLand, 1e-3f)
        assertTrue(wLand in 0.10f..0.40f)

        // Portrait 3:4 photo at 0.40m distance
        val (wPort, hPort) = ArSurfaceMath.computeInitialQuadSizeMeters(
            distanceMeters = 0.40f,
            projectionMatrix = proj,
            bitmapWidth = 900,
            bitmapHeight = 1200,
        )
        assertEquals(3f / 4f, wPort / hPort, 1e-3f)
        assertTrue(hPort > wPort)

        // Very far distance clamps within max physical size while preserving aspect ratio
        val (wFar, hFar) = ArSurfaceMath.computeInitialQuadSizeMeters(
            distanceMeters = 50f,
            projectionMatrix = proj,
            bitmapWidth = 1000,
            bitmapHeight = 500,
        )
        assertTrue(wFar <= 2.501f)
        assertEquals(2f, wFar / hFar, 1e-3f)
    }

    @Test
    fun computePlaneDragDeltaMeters_topDownViewMatchesScreenDirection() {
        // Camera at (0, 0.5, 0) looking straight down at (0, 0, 0) with camera-up = North (0, 0, -1)
        val view = lookAtMatrix(
            eye = Vec3(0f, 0.5f, 0f),
            center = Vec3(0f, 0f, 0f),
            up = Vec3(0f, 0f, -1f),
        )
        val proj = perspectiveMatrix(fovYDeg = 60f, aspect = 1f, near = 0.05f, far = 20f)
        val origin = Vec3(0f, 0f, 0f)
        val normal = Vec3(0f, 1f, 0f)
        val baseRight = Vec3(1f, 0f, 0f)
        val baseDown = Vec3(0f, 0f, 1f)

        // Drag right on screen -> positive dX, zero dZ
        val (dxRight, dzRight) = ArSurfaceMath.computePlaneDragDeltaMeters(
            prevScreenX = 500f,
            prevScreenY = 500f,
            currScreenX = 600f,
            currScreenY = 500f,
            viewportWidth = 1000,
            viewportHeight = 1000,
            viewMatrix = view,
            projectionMatrix = proj,
            planeOrigin = origin,
            planeNormal = normal,
            baseRight = baseRight,
            baseDown = baseDown,
            fallbackDistanceMeters = 0.5f,
        )
        assertTrue("Expected positive dX when dragging right, got $dxRight", dxRight > 0.04f)
        assertEquals(0f, dzRight, 1e-3f)

        // Drag down on screen -> zero dX, positive dZ
        val (dxDown, dzDown) = ArSurfaceMath.computePlaneDragDeltaMeters(
            prevScreenX = 500f,
            prevScreenY = 500f,
            currScreenX = 500f,
            currScreenY = 600f,
            viewportWidth = 1000,
            viewportHeight = 1000,
            viewMatrix = view,
            projectionMatrix = proj,
            planeOrigin = origin,
            planeNormal = normal,
            baseRight = baseRight,
            baseDown = baseDown,
            fallbackDistanceMeters = 0.5f,
        )
        assertEquals(0f, dxDown, 1e-3f)
        assertTrue("Expected positive dZ when dragging down, got $dzDown", dzDown > 0.04f)
    }

    @Test
    fun computePlaneDragDeltaMeters_angledViewAndRotatedCameraStaysAlignedWithFinger() {
        // User walked 90 deg to the East side of the desk and tilts phone 45 deg looking West (-X)
        // Camera at (0.4, 0.4, 0) looking at (0, 0, 0), up = (0, 1, 0)
        // In this view, screen-right points North (0, 0, -1), which is -baseDown!
        val view = lookAtMatrix(
            eye = Vec3(0.4f, 0.4f, 0f),
            center = Vec3(0f, 0f, 0f),
            up = Vec3(0f, 1f, 0f),
        )
        val proj = perspectiveMatrix(fovYDeg = 60f, aspect = 1f, near = 0.05f, far = 20f)
        val origin = Vec3(0f, 0f, 0f)
        val normal = Vec3(0f, 1f, 0f)
        val baseRight = Vec3(1f, 0f, 0f)
        val baseDown = Vec3(0f, 0f, 1f)

        val (dX, dZ) = ArSurfaceMath.computePlaneDragDeltaMeters(
            prevScreenX = 500f,
            prevScreenY = 500f,
            currScreenX = 600f,
            currScreenY = 500f,
            viewportWidth = 1000,
            viewportHeight = 1000,
            viewMatrix = view,
            projectionMatrix = proj,
            planeOrigin = origin,
            planeNormal = normal,
            baseRight = baseRight,
            baseDown = baseDown,
            fallbackDistanceMeters = 0.56f,
        )
        // Dragging right on screen moves North (-Z), so dZ must be negative and dX ~ 0
        assertEquals(0f, dX, 1e-2f)
        assertTrue("Expected negative dZ moving North, got $dZ", dZ < -0.04f)
    }

    @Test
    fun invertMatrix4x4_invertsViewProjectionAndRejectsSingular() {
        val view = lookAtMatrix(Vec3(0.3f, 0.5f, 0.4f), Vec3(0f, 0f, 0f), Vec3(0f, 1f, 0f))
        val proj = perspectiveMatrix(60f, 0.75f, 0.05f, 20f)
        val vp = ArSurfaceMath.multiplyMatrix4x4(proj, view)
        val inv = ArSurfaceMath.invertMatrix4x4(vp)
        assertNotNull(inv)

        val product = ArSurfaceMath.multiplyMatrix4x4(vp, inv!!)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                val expected = if (col == row) 1f else 0f
                assertEquals(expected, product[col * 4 + row], 1e-4f)
            }
        }

        assertNull(ArSurfaceMath.invertMatrix4x4(FloatArray(16)))
    }

    private fun assertVec3Equals(expected: Vec3, actual: Vec3, eps: Float) {
        assertTrue(
            "Expected $expected, got $actual",
            abs(expected.x - actual.x) <= eps &&
                abs(expected.y - actual.y) <= eps &&
                abs(expected.z - actual.z) <= eps,
        )
    }

    private fun perspectiveMatrix(fovYDeg: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = 1f / tan(Math.toRadians(fovYDeg.toDouble()) / 2.0).toFloat()
        val nf = 1f / (near - far)
        return floatArrayOf(
            f / aspect, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            0f, 0f, (far + near) * nf, -1f,
            0f, 0f, 2f * far * near * nf, 0f,
        )
    }

    private fun lookAtMatrix(eye: Vec3, center: Vec3, up: Vec3): FloatArray {
        val f = (center - eye).normalizedOrNull()!!
        val s = f.cross(up).normalizedOrNull()!!
        val u = s.cross(f)
        return floatArrayOf(
            s.x, u.x, -f.x, 0f,
            s.y, u.y, -f.y, 0f,
            s.z, u.z, -f.z, 0f,
            -s.dot(eye), -u.dot(eye), f.dot(eye), 1f,
        )
    }
}
