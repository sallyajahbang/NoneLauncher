package com.sally.launcher

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_YAW = 0.6f
private const val SPIN_STEP = 0.008f
private const val DRAG_SCALE = 0.012f
private const val PITCH = 0.25f
private const val MODEL_UNITS_TALL = 36f
private const val MODEL_CENTER_Y = 16f
private const val GROW_HEAD = 0.5f
private const val GROW_BODY = 0.25f

/**
 * Interactive 3D preview of a Minecraft player skin. The model is drawn from the skin texture
 * with per-face affine transforms, so it uses the real Steve or Alex layout (wide or slim arms).
 * It spins slowly, pauses while the user drags, and drags to turn it.
 */
@Composable
internal fun SkinPreview(skin: ImageBitmap?, slim: Boolean, modifier: Modifier = Modifier) {
    var yaw by remember { mutableFloatStateOf(START_YAW) }
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                if (!paused) yaw += SPIN_STEP
                Unit
            }
        }
    }
    val dragModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { paused = true },
            onDragEnd = { paused = false },
            onDragCancel = { paused = false },
        ) { _, dragAmount ->
            yaw += dragAmount * DRAG_SCALE
        }
    }
    Box(modifier.then(dragModifier), contentAlignment = Alignment.Center) {
        if (skin == null) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawSkinModel(
                    canvas = drawContext.canvas.nativeCanvas,
                    skin = skin.asAndroidBitmap(),
                    slim = slim,
                    yaw = yaw,
                    pitch = PITCH,
                    width = size.width,
                    height = size.height,
                )
            }
        }
    }
}

/** Head-only avatar for the top bar: the front face with the hat layer on top. */
@Composable
internal fun SkinAvatar(skin: ImageBitmap?, avatarSize: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(avatarSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (skin == null) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val px = size.width.roundToInt()
                val py = size.height.roundToInt()
                drawImage(
                    image = skin,
                    srcOffset = IntOffset(8, 8),
                    srcSize = IntSize(8, 8),
                    dstSize = IntSize(px, py),
                    filterQuality = FilterQuality.None,
                )
                drawImage(
                    image = skin,
                    srcOffset = IntOffset(40, 8),
                    srcSize = IntSize(8, 8),
                    dstSize = IntSize(px, py),
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- geometry

private class Vec(val x: Float, val y: Float, val z: Float)

/** One textured quad: tl/tr/bl are the 3D corners that match the texture rect's top-left, top-right and bottom-left. */
private class Face(val tl: Vec, val tr: Vec, val bl: Vec, val normal: Vec, val tex: Rect)

private class ProjectedFace(val tl: Vec, val tr: Vec, val bl: Vec, val tex: Rect, val depth: Float)

private fun tex(left: Int, top: Int, w: Int, h: Int) = Rect(left, top, left + w, top + h)

/** Adds the six faces of a box in model space, laid out the way Minecraft's UV unwrap is. */
private fun addBox(
    out: MutableList<Face>,
    x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float,
    u: Int, v: Int, w: Int, h: Int, d: Int,
) {
    // Front (faces -z, toward the default camera).
    out += Face(Vec(x0, y0, z0), Vec(x1, y0, z0), Vec(x0, y1, z0), Vec(0f, 0f, -1f), tex(u + d, v + d, w, h))
    // Back.
    out += Face(Vec(x1, y0, z1), Vec(x0, y0, z1), Vec(x1, y1, z1), Vec(0f, 0f, 1f), tex(u + 2 * d + w, v + d, w, h))
    // Character's right side (-x).
    out += Face(Vec(x0, y0, z1), Vec(x0, y0, z0), Vec(x0, y1, z1), Vec(-1f, 0f, 0f), tex(u, v + d, d, h))
    // Character's left side (+x).
    out += Face(Vec(x1, y0, z0), Vec(x1, y0, z1), Vec(x1, y1, z0), Vec(1f, 0f, 0f), tex(u + d + w, v + d, d, h))
    // Top.
    out += Face(Vec(x0, y0, z1), Vec(x1, y0, z1), Vec(x0, y0, z0), Vec(0f, -1f, 0f), tex(u + d, v, w, d))
    // Bottom.
    out += Face(Vec(x0, y1, z0), Vec(x1, y1, z0), Vec(x0, y1, z1), Vec(0f, 1f, 0f), tex(u + d + w, v, w, d))
}

/** Player model in pixel units, origin at the top of the head, y pointing down. Base layer plus hat/jacket/sleeve/pants overlays. */
private fun buildFaces(slim: Boolean): List<Face> {
    val armWidth = if (slim) 3 else 4
    val faces = ArrayList<Face>(96)

    fun part(
        x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float,
        u: Int, v: Int, w: Int, h: Int, d: Int, grow: Float,
    ) {
        addBox(
            faces,
            x0 - grow, x1 + grow, y0 - grow, y1 + grow, z0 - grow, z1 + grow,
            u, v, w, h, d,
        )
    }

    // Head: base (0,0) and hat overlay (32,0).
    part(-4f, 4f, 0f, 8f, -4f, 4f, 0, 0, 8, 8, 8, 0f)
    part(-4f, 4f, 0f, 8f, -4f, 4f, 32, 0, 8, 8, 8, GROW_HEAD)
    // Body: base (16,16) and jacket overlay (16,32).
    part(-4f, 4f, 8f, 20f, -2f, 2f, 16, 16, 8, 12, 4, 0f)
    part(-4f, 4f, 8f, 20f, -2f, 2f, 16, 32, 8, 12, 4, GROW_BODY)
    // Character's right arm: base (40,16) and sleeve overlay (40,32).
    part(-4f - armWidth, -4f, 8f, 20f, -2f, 2f, 40, 16, armWidth, 12, 4, 0f)
    part(-4f - armWidth, -4f, 8f, 20f, -2f, 2f, 40, 32, armWidth, 12, 4, GROW_BODY)
    // Character's left arm: base (32,48) and sleeve overlay (48,48).
    part(4f, 4f + armWidth, 8f, 20f, -2f, 2f, 32, 48, armWidth, 12, 4, 0f)
    part(4f, 4f + armWidth, 8f, 20f, -2f, 2f, 48, 48, armWidth, 12, 4, GROW_BODY)
    // Right leg: base (0,16) and pants overlay (0,32).
    part(-4f, 0f, 20f, 32f, -2f, 2f, 0, 16, 4, 12, 4, 0f)
    part(-4f, 0f, 20f, 32f, -2f, 2f, 0, 32, 4, 12, 4, GROW_BODY)
    // Left leg: base (16,48) and pants overlay (0,48).
    part(0f, 4f, 20f, 32f, -2f, 2f, 16, 48, 4, 12, 4, 0f)
    part(0f, 4f, 20f, 32f, -2f, 2f, 0, 48, 4, 12, 4, GROW_BODY)
    return faces
}

/** Rotates a point (or a direction) by yaw around the vertical axis, then pitch around the horizontal axis. */
private fun rotate(p: Vec, yaw: Float, pitch: Float): Vec {
    val cy = cos(yaw)
    val sy = sin(yaw)
    val x1 = p.x * cy + p.z * sy
    val z1 = -p.x * sy + p.z * cy
    val cp = cos(pitch)
    val sp = sin(pitch)
    return Vec(x1, p.y * cp - z1 * sp, p.y * sp + z1 * cp)
}

private fun project(p: Vec, yaw: Float, pitch: Float, scale: Float, centerX: Float, centerY: Float): Vec {
    val r = rotate(Vec(p.x, p.y - MODEL_CENTER_Y, p.z), yaw, pitch)
    return Vec(centerX + r.x * scale, centerY + r.y * scale, r.z)
}

// ---------------------------------------------------------------- drawing

/**
 * Draws the skin model into [canvas]. Each visible face is clipped to its projected parallelogram and
 * filled by mapping the face's texture rect onto it with an affine matrix. Faces are painted far to near.
 */
private fun drawSkinModel(
    canvas: android.graphics.Canvas,
    skin: Bitmap,
    slim: Boolean,
    yaw: Float,
    pitch: Float,
    width: Float,
    height: Float,
) {
    val scale = minOf(width, height) / MODEL_UNITS_TALL
    val centerX = width / 2f
    val centerY = height / 2f
    val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    val visible = buildFaces(slim).mapNotNull { face ->
        val normal = rotate(face.normal, yaw, pitch)
        if (normal.z >= 0f) return@mapNotNull null
        // Skip faces that point outside the texture (old 64x32 skins have no arm/leg overlays).
        if (face.tex.right > skin.width || face.tex.bottom > skin.height) return@mapNotNull null
        val tl = project(face.tl, yaw, pitch, scale, centerX, centerY)
        val tr = project(face.tr, yaw, pitch, scale, centerX, centerY)
        val bl = project(face.bl, yaw, pitch, scale, centerX, centerY)
        ProjectedFace(tl, tr, bl, face.tex, (tl.z + tr.z + bl.z) / 3f)
    }.sortedByDescending { it.depth }

    for (face in visible) {
        drawFace(canvas, skin, face, paint)
    }
}

private fun drawFace(canvas: android.graphics.Canvas, skin: Bitmap, face: ProjectedFace, paint: Paint) {
    val fourthX = face.tr.x + face.bl.x - face.tl.x
    val fourthY = face.tr.y + face.bl.y - face.tl.y
    val path = Path().apply {
        moveTo(face.tl.x, face.tl.y)
        lineTo(face.tr.x, face.tr.y)
        lineTo(fourthX, fourthY)
        lineTo(face.bl.x, face.bl.y)
        close()
    }
    val source = floatArrayOf(
        face.tex.left.toFloat(), face.tex.top.toFloat(),
        face.tex.right.toFloat(), face.tex.top.toFloat(),
        face.tex.left.toFloat(), face.tex.bottom.toFloat(),
    )
    val target = floatArrayOf(
        face.tl.x, face.tl.y,
        face.tr.x, face.tr.y,
        face.bl.x, face.bl.y,
    )
    val matrix = Matrix()
    if (!matrix.setPolyToPoly(source, 0, target, 0, 3)) return
    canvas.save()
    canvas.clipPath(path)
    canvas.concat(matrix)
    canvas.drawBitmap(skin, face.tex, RectF(face.tex), paint)
    canvas.restore()
}
