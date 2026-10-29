package com.lifemax.rpg

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class OverviewParityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun waitUntil(label: String, action: () -> Boolean) {
        val end=SystemClock.uptimeMillis()+60000
        while(SystemClock.uptimeMillis()<end){ if(action())return;SystemClock.sleep(250) }
        fail("Timed out: $label")
    }
    private fun evaluate(activity: MainActivity, js: String): String {
        val done=CountDownLatch(1);var result=""
        instrumentation.runOnMainSync { activity.bridge.webView.evaluateJavascript(js){result=it;done.countDown()} }
        assertTrue(done.await(10,TimeUnit.SECONDS));return result
    }
    private fun export(name: String) {
        if(android.os.Build.VERSION.SDK_INT<29)return
        val context=instrumentation.targetContext
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,if(name.endsWith(".png"))"image/png" else "application/json")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver=context.contentResolver
        val uri=resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error("Could not export $name")
        resolver.openOutputStream(uri)!!.use { output -> File(context.filesDir,name).inputStream().use { it.copyTo(output) } }
    }
    private fun shot(view: View, name: String): Bitmap {
        val rect=Rect();instrumentation.runOnMainSync{view.getGlobalVisibleRect(rect)}
        val full=instrumentation.uiAutomation.takeScreenshot()
        val image=Bitmap.createBitmap(full,rect.left,rect.top,rect.width(),rect.height())
        val file=File(instrumentation.targetContext.filesDir,name)
        file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
        export(name)
        return image
    }
    private fun webViews(view: View): Int = (if(view is WebView)1 else 0)+(if(view is ViewGroup)(0 until view.childCount).sumOf{webViews(view.getChildAt(it))}else 0)
    @Test fun overviewIsNativeAndKeepsTheOriginalDesign() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var main: MainActivity
            scenario.onActivity { main=it }
            waitUntil("web app ready"){evaluate(main,"typeof window.openNativeOverview==='function'")=="true"}
            evaluate(main,"""window.__nativeOverviewManual=true;window.__overviewNavHistory=[];const originalNavigate=navigate;navigate=function(v){if(v!=='dashboard')__overviewNavHistory.push({v,stack:new Error().stack});return originalNavigate.apply(this,arguments)};state.onboardingComplete=true;state.tutorialDone=true;state.name='Native preview';state.petInviteHidden=false;document.querySelectorAll('#onboarding,#tutorial').forEach(e=>{e.classList.remove('show');e.style.display='none'});document.body.classList.remove('noscroll');document.body.style.overflow='';document.querySelectorAll('.lu-card').forEach(e=>e.parentElement.remove());save();renderAll();navigate('dashboard');true""")
            SystemClock.sleep(1800)
            evaluate(main,"window.scrollTo(0,0);true")
            evaluate(main,"document.activeElement?.blur();lmaNavTo('dashboard',{fast:true,skip:true});window.scrollTo(0,0);true")
            SystemClock.sleep(800)
            assertEquals("Reference must be Overview", "\"dashboard\"", evaluate(main,"lmaView()"))
            val original=shot(main.bridge.webView,"overview-web.png")
            evaluate(main,"window.openNativeOverview().then(r=>window.__nativeAttempt=r);true")
            var native: OverviewActivity?=null
            try { waitUntil("native Overview"){instrumentation.runOnMainSync{native=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<OverviewActivity>().firstOrNull()};native!=null} }
            catch(e: AssertionError){throw AssertionError("Native handoff diagnostics: "+evaluate(main,"JSON.stringify({attempt:window.__nativeAttempt,sceneError:window.__nativeOverviewError,diagnostics:NativeOverviewSnapshot.diagnostics(),navigation:window.__overviewNavHistory})"),e)}
            waitUntil("native scene painted"){var ready=false;instrumentation.runOnMainSync{ready=native!!.window.decorView.findViewWithTag<View>("overview-rendered")?.height?.let{it>1}==true};ready}
            SystemClock.sleep(500)
            lateinit var content:View
            instrumentation.runOnMainSync{content=native!!.findViewById(android.R.id.content);assertEquals("Overview must not contain a WebView",0,webViews(content))}
            val rendered=shot(content,"overview-native.png")
            val report=JSONObject().put("webWidth",original.width).put("webHeight",original.height).put("nativeWidth",rendered.width).put("nativeHeight",rendered.height)
            assertEquals(original.width,rendered.width)
            assertTrue(abs(original.height-rendered.height)<=4)
            var difference=0L;var samples=0
            for(y in 0 until minOf(original.height,rendered.height) step 3)for(x in 0 until original.width step 3){val a=original.getPixel(x,y);val b=rendered.getPixel(x,y);for(shift in listOf(0,8,16)){difference+=abs(((a shr shift)and 255)-((b shr shift)and 255));samples++}}
            val mean=difference.toDouble()/samples
            report.put("meanChannelDifference",mean)
            File(instrumentation.targetContext.filesDir,"overview-parity.json").writeText(report.toString(2))
            export("overview-parity.json")
            // Geometry is measured from the actual Android WebView, not a separate desktop font layout.
            assertTrue("Native rendering differs too much: $report",mean<25.0)
            instrumentation.runOnMainSync { native!!.finish() }
            waitUntil("native screen closed"){var closed=false;instrumentation.runOnMainSync{closed=OverviewHost.session==null};closed}
            assertEquals("shared web state preserved", "\"Native preview\"", evaluate(main,"state.name"))
        }
    }
}
