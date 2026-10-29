package com.lifemax.rpg

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import org.json.JSONObject
import kotlin.math.roundToInt

/** Small native controls over the SAME resumed, hardware-accelerated WebView.
 * The body remains live DOM/canvas: no copies, screenshots or page-sized bitmap.
 * Empty space in this container passes touches through to the original WebView. */
internal class LiveOverviewShell(activity:Activity,private val web:WebView,activate:(String)->Unit) {
    private val parent=activity.findViewById<ViewGroup>(android.R.id.content)
    private val frame=FrameLayout(activity).apply { tag="overview-live-shell";importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
    private val header=OverviewSurface(activity){id,_->activate(id)}
    private val bar=OverviewSurface(activity){id,_->activate(id)}
    private val nav=HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled=false }
    private val scroll=ViewTreeObserver.OnScrollChangedListener { header.translationY=-web.scrollY.toFloat() }
    var sequence=0;private set
    private var first=true
    init {
        // Bound software layers to two small control strips. The WebView body
        // stays accelerated and owns native scrolling/fling and live animation.
        header.setLayerType(View.LAYER_TYPE_SOFTWARE,null);bar.setLayerType(View.LAYER_TYPE_SOFTWARE,null)
        nav.addView(bar)
        frame.addView(header,FrameLayout.LayoutParams(-1,1))
        frame.addView(nav,FrameLayout.LayoutParams(-1,1,Gravity.BOTTOM))
        parent.addView(frame,FrameLayout.LayoutParams(-1,-1))
        web.viewTreeObserver.addOnScrollChangedListener(scroll)
    }
    fun show(scene:JSONObject) {
        sequence=scene.optInt("sequence")
        val width=scene.getDouble("width").toFloat()
        val scale=(web.width/width).takeIf { it>0 } ?: web.resources.displayMetrics.density
        val h=scene.getJSONObject("header");val n=scene.getJSONObject("nav")
        header.applyScene(OverviewLayer(h),width,h.getDouble("height").toFloat(),Color.TRANSPARENT)
        header.layoutParams=FrameLayout.LayoutParams(-1,(h.getDouble("height")*scale).roundToInt().coerceAtLeast(1))
        bar.applyScene(OverviewLayer(n),n.getDouble("width").toFloat(),n.getDouble("height").toFloat(),scene.optLong("background").toInt(),true)
        nav.layoutParams=FrameLayout.LayoutParams(-1,(n.getDouble("height")*web.resources.displayMetrics.density).roundToInt().coerceAtLeast(1),Gravity.BOTTOM)
        header.translationY=-web.scrollY.toFloat()
        if(first){first=false;nav.post{nav.scrollTo((scene.optDouble("navScroll")*web.resources.displayMetrics.density).roundToInt(),0)}}
    }
    fun close(){if(web.viewTreeObserver.isAlive)web.viewTreeObserver.removeOnScrollChangedListener(scroll);parent.removeView(frame)}
}
