package com.example.wallpaperrotator

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Screen frame - represents the phone screen (fixed position)
    private val screenFrame = RectF()
    
    // Overlay paint for dimmed areas outside the screen frame
    private val overlayPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.FILL
    }
    
    // Frame border
    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // Dotted center line
    private val dottedLinePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    // Image transformation (user drags and zooms the image)
    private var scale = 1f
    private var translateX = 0f
    private var translateY = 0f
    
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    
    // Calculate screen aspect ratio dynamically
    private val screenAspectRatio: Float by lazy {
        val displayMetrics = context.resources.displayMetrics
        displayMetrics.widthPixels.toFloat() / displayMetrics.heightPixels.toFloat()
    }

    fun setBitmap(bmp: Bitmap) {
        if (bitmap != null && bitmap != bmp) {
            bitmap?.recycle()
        }
        bitmap = bmp
        resetTransform()
        invalidate()
    }

    fun cleanup() {
        bitmap?.recycle()
        bitmap = null
    }

    private fun resetTransform() {
        val bmp = bitmap ?: return
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val bmpWidth = bmp.width.toFloat()
        val bmpHeight = bmp.height.toFloat()

        // Calculate initial scale to cover screen frame
        val frameWidth = screenFrame.width()
        val frameHeight = screenFrame.height()
        
        if (frameWidth > 0 && frameHeight > 0) {
            // Scale to cover the frame (not just fit)
            scale = max(frameWidth / bmpWidth, frameHeight / bmpHeight)
        } else {
            // Fallback if layout hasn't happened yet
            scale = min(viewWidth / bmpWidth, viewHeight / bmpHeight)
        }
        
        // Center in view
        translateX = (viewWidth - bmpWidth * scale) / 2
        translateY = (viewHeight - bmpHeight * scale) / 2
        
        updateMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Create screen frame in the center of the view
        // Make it 70% of view width, maintaining screen aspect ratio
        val frameWidth = w * 0.7f
        val frameHeight = frameWidth / screenAspectRatio
        
        val left = (w - frameWidth) / 2
        val top = (h - frameHeight) / 2
        
        screenFrame.set(left, top, left + frameWidth, top + frameHeight)
        
        bitmap?.let { resetTransform() }
    }

    private fun updateMatrix() {
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(translateX, translateY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bmp = bitmap ?: return

        // Draw the image
        canvas.save()
        canvas.concat(matrix)
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        canvas.restore()

        // Draw dark overlay outside the frame (dimmed areas)
        // Top overlay
        if (screenFrame.top > 0) {
            canvas.drawRect(0f, 0f, width.toFloat(), screenFrame.top, overlayPaint)
        }
        // Bottom overlay
        if (screenFrame.bottom < height) {
            canvas.drawRect(0f, screenFrame.bottom, width.toFloat(), height.toFloat(), overlayPaint)
        }
        // Left overlay
        canvas.drawRect(0f, screenFrame.top, screenFrame.left, screenFrame.bottom, overlayPaint)
        // Right overlay
        canvas.drawRect(screenFrame.right, screenFrame.top, width.toFloat(), screenFrame.bottom, overlayPaint)

        // Draw the frame border
        canvas.drawRect(screenFrame, framePaint)
        
        // Draw dotted vertical center line
        val centerX = screenFrame.centerX()
        canvas.drawLine(centerX, screenFrame.top, centerX, screenFrame.bottom, dottedLinePaint)
        
        // Draw instruction text
        val instructionPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Drag and pinch to position", width / 2f, screenFrame.bottom + 60f, instructionPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activePointerId = event.getPointerId(0)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) return true
                
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true

                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                // Only drag if not pinching
                if (!scaleDetector.isInProgress) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    translateX += dx
                    translateY += dy
                    updateMatrix()
                    invalidate()
                }

                lastTouchX = x
                lastTouchY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    // Pick a new active pointer
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val previousScale = scale
            scale *= scaleFactor
            scale = max(0.1f, min(scale, 10f))
            
            // Adjust translation to scale around the focus point
            val focusX = detector.focusX
            val focusY = detector.focusY
            val scaleChange = scale / previousScale
            translateX = focusX - (focusX - translateX) * scaleChange
            translateY = focusY - (focusY - translateY) * scaleChange
            
            updateMatrix()
            invalidate()
            return true
        }
    }

    fun getCropRect(): RectF {
        val bmp = bitmap ?: return RectF(0f, 0f, 1f, 1f)
        
        // Convert screen frame coordinates to bitmap coordinates
        val invertMatrix = Matrix()
        if (!matrix.invert(invertMatrix)) {
            return RectF(0f, 0f, 1f, 1f)
        }

        val points = floatArrayOf(
            screenFrame.left, screenFrame.top,
            screenFrame.right, screenFrame.bottom
        )
        invertMatrix.mapPoints(points)

        // Normalize to 0-1 range
        val normalizedRect = RectF(
            (points[0] / bmp.width).coerceIn(0f, 1f),
            (points[1] / bmp.height).coerceIn(0f, 1f),
            (points[2] / bmp.width).coerceIn(0f, 1f),
            (points[3] / bmp.height).coerceIn(0f, 1f)
        )
        
        return normalizedRect
    }

    fun setCropRect(normalizedRect: RectF) {
        val bmp = bitmap ?: return
        
        // Convert normalized rect back to bitmap coordinates
        val cropX = normalizedRect.left * bmp.width
        val cropY = normalizedRect.top * bmp.height
        val cropWidth = (normalizedRect.right - normalizedRect.left) * bmp.width
        val cropHeight = (normalizedRect.bottom - normalizedRect.top) * bmp.height
        
        // Calculate scale to fit crop area into screen frame
        scale = max(
            screenFrame.width() / cropWidth,
            screenFrame.height() / cropHeight
        )
        
        // Calculate translation to center the crop area in the frame
        val scaledCropX = cropX * scale
        val scaledCropY = cropY * scale
        val scaledCropWidth = cropWidth * scale
        val scaledCropHeight = cropHeight * scale
        
        translateX = screenFrame.centerX() - scaledCropX - scaledCropWidth / 2
        translateY = screenFrame.centerY() - scaledCropY - scaledCropHeight / 2
        
        updateMatrix()
        invalidate()
    }
}
