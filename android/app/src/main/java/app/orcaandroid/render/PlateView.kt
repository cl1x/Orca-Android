package app.orcaandroid.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import app.orcaandroid.core.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class InteractionMode { NAVIGATE, PAINT, LAY_ON_FACE, MEASURE }

/** What the 3D view reports back to the app. Called on the UI thread. */
interface PlateListener {
    /** A tap (not a drag); the host picks and selects / lays on face / measures depending on the mode. */
    fun onTap(x: Float, y: Float)
    /** The selected object was dragged by (dx, dy) mm on the bed. */
    fun onMoved(dx: Float, dy: Float)
    /** Paint stroke sample at screen (x, y); [newStroke] for the first sample of a stroke. */
    fun onPaint(x: Float, y: Float, newStroke: Boolean)
}

/**
 * GLSurfaceView hosting [PlateRenderer] with touch navigation: one finger orbits (or drags the
 * selected object), two fingers pinch-zoom and pan, double tap frames the plate. Mouse wheel zooms
 * and right-drag pans on tablets with a mouse.
 */
@SuppressLint("ViewConstructor")
class PlateView(context: Context) : GLSurfaceView(context) {

    val camera = Camera()
    val renderer = PlateRenderer(camera)
    var listener: PlateListener? = null
    var mode = InteractionMode.NAVIGATE
    /** World bounding boxes (min, max) of the selected object's instances; a drag starting on them moves it. */
    var selectedBounds: List<Pair<Vec3, Vec3>> = emptyList()
    /** Only navigate (the sliced preview): touches never select or move objects. */
    var viewOnly = false

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var multiTouch = false
    private var moved = false
    private var dragging = false
    private var dragStart: Vec3? = null
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            camera.zoom(detector.scaleFactor)
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (mode == InteractionMode.NAVIGATE) queueEvent { renderer.frame(renderer.activePlate) }
            return true
        }
    })

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 4)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        // Draw only when something changed (touch, camera, new data): continuous rendering keeps the
        // GPU busy at the display's refresh rate and drains the battery.
        renderMode = RENDERMODE_WHEN_DIRTY
        isFocusable = true
    }

    /** Runs [block] on the GL thread and redraws. */
    fun onGl(block: PlateRenderer.() -> Unit) {
        queueEvent { renderer.block() }
        requestRender()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        val mouseRight = event.isFromSource(android.view.InputDevice.SOURCE_MOUSE) && event.buttonState and MotionEvent.BUTTON_SECONDARY != 0
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y; downX = event.x; downY = event.y
                multiTouch = false; moved = false; dragging = false
                if (mode == InteractionMode.PAINT) listener?.onPaint(event.x, event.y, true)
                else if (mode == InteractionMode.NAVIGATE && !viewOnly && hitsSelection(event.x, event.y)) {
                    dragging = true
                    dragStart = camera.rayOnPlane(event.x, event.y)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                cancelDrag()
                lastFocusX = focusX(event); lastFocusY = focusY(event)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(remaining); lastY = event.getY(remaining)
                lastFocusX = focusX(event); lastFocusY = focusY(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) + abs(event.y - downY) > touchSlop) moved = true
                if (event.pointerCount >= 2) {
                    val fx = focusX(event); val fy = focusY(event)
                    camera.pan(fx - lastFocusX, fy - lastFocusY)
                    lastFocusX = fx; lastFocusY = fy
                } else if (!multiTouch) {
                    when {
                        mouseRight -> camera.pan(event.x - lastX, event.y - lastY)
                        mode == InteractionMode.PAINT -> {
                            // Fill gaps of fast strokes so the painted trail stays continuous.
                            val dx = event.x - lastX
                            val dy = event.y - lastY
                            val steps = (kotlin.math.hypot(dx, dy) / PAINT_STEP_PX).toInt().coerceIn(1, 32)
                            for (i in 1..steps) listener?.onPaint(lastX + dx * i / steps, lastY + dy * i / steps, false)
                        }
                        dragging -> {
                            val start = dragStart
                            val now = camera.rayOnPlane(event.x, event.y)
                            if (start != null && now != null) {
                                val d = floatArrayOf(now.x - start.x, now.y - start.y, 0f)
                                queueEvent { renderer.dragOffset = d }
                            }
                        }
                        else -> camera.orbit((event.x - lastX) * 0.3f, (event.y - lastY) * 0.3f)
                    }
                    lastX = event.x; lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val start = dragStart
                    val end = camera.rayOnPlane(event.x, event.y)
                    cancelDrag()
                    if (moved && start != null && end != null) listener?.onMoved(end.x - start.x, end.y - start.y)
                    else if (!moved) listener?.onTap(event.x, event.y)
                } else if (!moved && !multiTouch && mode != InteractionMode.PAINT && !viewOnly) {
                    listener?.onTap(event.x, event.y)
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelDrag()
        }
        requestRender()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            camera.zoom(if (v > 0) 1.15f else 1f / 1.15f)
            requestRender()
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun cancelDrag() {
        if (dragging) queueEvent { renderer.dragOffset = floatArrayOf(0f, 0f, 0f) }
        dragging = false
        dragStart = null
    }

    /** Ray / axis-aligned box test against the selected object's instances. */
    private fun hitsSelection(x: Float, y: Float): Boolean {
        if (selectedBounds.isEmpty()) return false
        val (o, d) = camera.ray(x, y)
        return selectedBounds.any { (lo, hi) ->
            var tMin = 0f
            var tMax = Float.MAX_VALUE
            val os = floatArrayOf(o.x, o.y, o.z); val ds = floatArrayOf(d.x, d.y, d.z)
            val los = floatArrayOf(lo.x, lo.y, lo.z); val his = floatArrayOf(hi.x, hi.y, hi.z)
            for (i in 0..2) {
                if (abs(ds[i]) < 1e-8f) {
                    if (os[i] < los[i] || os[i] > his[i]) return@any false
                } else {
                    var t1 = (los[i] - os[i]) / ds[i]
                    var t2 = (his[i] - os[i]) / ds[i]
                    if (t1 > t2) { val t = t1; t1 = t2; t2 = t }
                    tMin = max(tMin, t1)
                    tMax = min(tMax, t2)
                    if (tMin > tMax) return@any false
                }
            }
            true
        }
    }

    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    private fun focusX(e: MotionEvent) = (0 until e.pointerCount).map { e.getX(it) }.average().toFloat()
    private fun focusY(e: MotionEvent) = (0 until e.pointerCount).map { e.getY(it) }.average().toFloat()
}

/** Spacing of interpolated paint samples, in pixels. */
private const val PAINT_STEP_PX = 12f
