package com.cuchieman.tripplanner.ui.screens.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

import com.cuchieman.tripplanner.ui.theme.DarkPalette
import com.cuchieman.tripplanner.ui.theme.LocalTpPalette
import java.io.File

data class MapPin(
    val id: String,
    val name: String,
    val number: Int?,
    /** ARGB, from the day colour tokens. */
    val color: Int,
    val lat: Double,
    val lng: Double,
)

/**
 * osmdroid inside Compose. OpenStreetMap's default tiles are fine for personal use with a real
 * user agent; before publishing, switch the tile source to a provider with your own key.
 */
@Composable
fun OsmMap(
    pins: List<MapPin>,
    fallbackCenter: GeoPoint?,
    selectedId: String?,
    onPinClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember { createMapView(context) }
    // OpenStreetMap only has light tiles; in dark mode they are inverted so the map is not a white slab.
    val dark = LocalTpPalette.current === DarkPalette

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Re-frame only when the set of pins changes, not when one is selected.
    LaunchedEffect(pins.map { it.id }) {
        mapView.fitTo(pins.map { GeoPoint(it.lat, it.lng) }, fallbackCenter)
    }

    // A row tapped in the list below brings its pin to the middle.
    LaunchedEffect(selectedId) {
        val pin = pins.firstOrNull { it.id == selectedId } ?: return@LaunchedEffect
        mapView.controller.animateTo(GeoPoint(pin.lat, pin.lng))
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlayManager.tilesOverlay.setColorFilter(if (dark) DarkTiles else null)
            map.overlays.clear()
            val density = map.resources.displayMetrics.density
            pins.forEach { pin ->
                val marker = Marker(map).apply {
                    position = GeoPoint(pin.lat, pin.lng)
                    title = pin.name
                    icon = BitmapDrawable(
                        map.resources,
                        pinBitmap(pin.number, pin.color, density, selected = pin.id == selectedId)
                    )
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ ->
                        onPinClick(pin.id)
                        true
                    }
                }
                map.overlays.add(marker)
            }
            map.invalidate()
        }
    )
}

private fun createMapView(context: Context): MapView {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        minZoomLevel = 2.0
    }
}

private fun MapView.fitTo(points: List<GeoPoint>, fallback: GeoPoint?) {
    val apply = {
        when {
            points.size > 1 -> zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 120)
            points.size == 1 -> {
                controller.setZoom(15.0)
                controller.setCenter(points.first())
            }
            fallback != null -> {
                controller.setZoom(12.0)
                controller.setCenter(fallback)
            }
            else -> controller.setZoom(3.0)
        }
    }
    if (width > 0 && height > 0) apply() else addOnFirstLayoutListener { _, _, _, _, _ -> apply() }
}

/** Teardrop pin in the day colour with a white number, drawn once per marker. */
private fun pinBitmap(number: Int?, color: Int, density: Float, selected: Boolean): Bitmap {
    val scale = if (selected) 1.25f else 1f
    val w = (28 * density * scale).toInt()
    val h = (36 * density * scale).toInt()
    val r = w / 2f
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val tail = Path().apply {
        moveTo(r, h.toFloat())
        lineTo(w * 0.2f, r * 1.4f)
        lineTo(w * 0.8f, r * 1.4f)
        close()
    }
    canvas.drawPath(tail, fill)
    canvas.drawCircle(r, r, r, fill)

    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        this.color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(r, r, r - outline.strokeWidth / 2, outline)

    if (number != null) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = 12 * density * scale
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val baseline = r - (text.descent() + text.ascent()) / 2
        canvas.drawText(number.toString(), r, baseline, text)
    }
    return bitmap
}

/**
 * Dark tiles: invert, then rotate the hue 180° so water stays blue and parks stay green
 * (a plain invert turned the sea brown), then dim a little.
 */
private val DarkTiles: ColorMatrixColorFilter by lazy {
    val invert = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
    // Hue rotation by 180° with luminance weights 0.213 / 0.715 / 0.072.
    val hue180 = ColorMatrix(
        floatArrayOf(
            -0.574f, 1.430f, 0.144f, 0f, 0f,
            0.426f, 0.430f, 0.144f, 0f, 0f,
            0.426f, 1.430f, -0.856f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
    val dim = ColorMatrix().apply { setScale(0.85f, 0.85f, 0.85f, 1f) }
    invert.postConcat(hue180)
    invert.postConcat(dim)
    ColorMatrixColorFilter(invert)
}
