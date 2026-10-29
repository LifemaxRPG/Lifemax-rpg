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
            lateinit var main:MainActivity;scenario.onActivity{main=it}
            waitUntil("web ready"){evaluate(main,"typeof openNativeOverview==='function'")=="true"}
            evaluate(main,"""window.__nativeOverviewManual=true;lmaThumbWarm=async()=>{};document.dispatchEvent(new TouchEvent('touchstart',{touches:[],changedTouches:[]}));state.onboardingComplete=true;state.tutorialDone=true;state.name='Live preview';document.querySelectorAll('#onboarding,#tutorial').forEach(e=>{e.classList.remove('show');e.style.display='none'});document.body.classList.remove('noscroll');lmaLoad().nativeOverview=true;save();renderAll();navigate('dashboard');true""")
            waitUntil("thumbnail pass ended"){evaluate(main,"!lmaThumbWarmBusy")=="true"}
            evaluate(main,"navigate('dashboard');window.scrollTo(0,0);window.__liveFrames=0;window.__liveTicks=0;function liveFrame(){__liveFrames++;requestAnimationFrame(liveFrame)}requestAnimationFrame(liveFrame);window.__liveInterval=setInterval(()=>__liveTicks++,100);true")
            SystemClock.sleep(800)
            shot(main.bridge.webView,"overview-web.png")
            evaluate(main,"openNativeOverview().then(r=>window.__liveOpened=r.opened);true")
            waitUntil("live controls attached"){var attached=false;instrumentation.runOnMainSync{attached=main.window.decorView.findViewWithTag<View>("overview-live-shell")!=null};attached}
            waitUntil("live open response"){evaluate(main,"window.__liveOpened===true")=="true"}
            instrumentation.runOnMainSync {
                assertTrue("Original WebView stays accelerated",main.bridge.webView.isHardwareAccelerated)
                assertTrue("MainActivity stays resumed",ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).contains(main))
                assertFalse("No transition to Canvas activity",ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any{it is OverviewActivity})
            }
            val frames=evaluate(main,"__liveFrames").toInt();val ticks=evaluate(main,"__liveTicks").toInt()
            SystemClock.sleep(800)
            assertTrue("Web animation is live",evaluate(main,"__liveFrames").toInt()>frames+5)
            assertTrue("Timers remain live",evaluate(main,"__liveTicks").toInt()>ticks+3)
            shot(main.bridge.webView,"overview-native.png")
            evaluate(main,"window.scrollTo(0,500);true")
            waitUntil("original WebView scrolls"){var moved=false;instrumentation.runOnMainSync{moved=main.bridge.webView.scrollY>0};moved}
            evaluate(main,"navigate('settings');setNativeOverviewEnabled(false);true")
            waitUntil("native controls removed"){var gone=false;instrumentation.runOnMainSync{gone=main.window.decorView.findViewWithTag<View>("overview-live-shell")==null};gone}
            assertEquals("Classic persisted","false",evaluate(main,"JSON.parse(localStorage.getItem(LMA_PREF_KEY)).nativeOverview"))
            assertEquals("Shared save retained","\"Live preview\"",evaluate(main,"state.name"))
            File(instrumentation.targetContext.filesDir,"overview-parity.json").writeText(JSONObject().put("renderer","live-web-with-native-controls").put("liveAnimation",true).put("liveTimers",true).put("hardwareScrolling",true).put("sameActivity",true).toString(2));export("overview-parity.json")
        }
    }
}
