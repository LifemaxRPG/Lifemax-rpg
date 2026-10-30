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
    @Test fun characterUsesComposeInMainActivityWithoutGameplayWrites() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var main:MainActivity;scenario.onActivity{main=it}
            waitUntil("Character bridge ready"){evaluate(main,"typeof openNativeCharacter==='function'")=="true"}
            evaluate(main,"window.__nativeOverviewManual=true;lmaThumbWarm=async()=>{};state.onboardingComplete=true;state.tutorialDone=true;state.classId='athlete';document.querySelectorAll('#onboarding,#tutorial').forEach(e=>{e.classList.remove('show');e.style.setProperty('display','none','important');e.setAttribute('aria-hidden','true')});document.body.classList.remove('noscroll');document.body.style.overflow='';document.activeElement?.blur();save();renderAll();navigate('character');true")
            SystemClock.sleep(800)
            evaluate(main,"document.activeElement?.blur();document.body.style.overflow='';true")
            SystemClock.sleep(1000)
            assertEquals("Reference Character visible","\"character\"",evaluate(main,"lmaView()"))
            assertEquals("Onboarding hidden","\"none\"",evaluate(main,"getComputedStyle(document.getElementById('onboarding')).display"))
            val saved=evaluate(main,"localStorage.getItem(STORE)")
            shot(main.bridge.webView,"character-web.png")
            evaluate(main,"openNativeCharacter().then(r=>window.__characterOpened=r);true")
            waitUntil("Compose Character attached"){var ready=false;instrumentation.runOnMainSync{ready=main.window.decorView.findViewWithTag<View>("native-character-screen")!=null};ready}
            waitUntil("Character open response"){evaluate(main,"window.__characterOpened===true")=="true"}
            SystemClock.sleep(800)
            instrumentation.runOnMainSync{val content=main.window.decorView.findViewWithTag<View>("native-character-screen");assertEquals(0,webViews(content));assertTrue(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).contains(main))}
            val nativeImage=shot(main.bridge.webView,"character-native.png")
            var brightText=0
            for(y in (nativeImage.height*.35).toInt() until (nativeImage.height*.52).toInt() step 2)for(x in 0 until nativeImage.width step 2){val pixel=nativeImage.getPixel(x,y);if(android.graphics.Color.red(pixel)>200&&android.graphics.Color.green(pixel)>200&&android.graphics.Color.blue(pixel)>200)brightText++}
            assertTrue("Dark theme hero text must be readable",brightText>100)
            fun swipeCharacter(){
                val bounds=Rect();instrumentation.runOnMainSync{main.bridge.webView.getGlobalVisibleRect(bounds)}
                val start=SystemClock.uptimeMillis();val x=bounds.centerX().toFloat();val y0=bounds.bottom-240f;val y1=bounds.top+220f
                fun event(kind:Int,y:Float,time:Long){val e=android.view.MotionEvent.obtain(start,time,kind,x,y,0);instrumentation.uiAutomation.injectInputEvent(e,true);e.recycle()}
                event(android.view.MotionEvent.ACTION_DOWN,y0,start)
                for(i in 1..12){SystemClock.sleep(25);event(android.view.MotionEvent.ACTION_MOVE,y0+(y1-y0)*i/12,SystemClock.uptimeMillis())}
                event(android.view.MotionEvent.ACTION_UP,y1,SystemClock.uptimeMillis());SystemClock.sleep(500)
            }
            swipeCharacter();shot(main.bridge.webView,"character-radar.png")
            swipeCharacter();shot(main.bridge.webView,"character-attributes.png")
            swipeCharacter();swipeCharacter();shot(main.bridge.webView,"character-bottom.png")
            evaluate(main,"setLightMode(true);Capacitor.Plugins.NativeCharacter.update({snapshot:NativeCharacterProjection.snapshot()});true")
            SystemClock.sleep(500);shot(main.bridge.webView,"character-light.png")

            assertEquals("Read projection never changes save",saved,evaluate(main,"localStorage.getItem(STORE)"))
            evaluate(main,"setNativeCharacterEnabled(false);true")
            waitUntil("Classic Character restored"){var gone=false;instrumentation.runOnMainSync{gone=main.window.decorView.findViewWithTag<View>("native-character-screen")==null};gone}
        }
    }

    private fun clickSharedTab(label: String) {
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isClickable && node.contentDescription?.toString()?.trim() == label) return node
            for (i in 0 until node.childCount) {
                val child = find(node.getChild(i))
                if (child != null) return child
            }
            return null
        }
        val target = find(instrumentation.uiAutomation.rootInActiveWindow)
        assertNotNull("Shared native tab $label must be accessible", target)
        assertTrue("Shared native tab $label must accept activation", target!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
    }

    @Test fun nativeTabsReuseOneSharedChromeAndReleaseItOnNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var main: MainActivity
            scenario.onActivity { main = it }
            waitUntil("native tab bridges ready") {
                evaluate(main, "typeof openNativeCharacter==='function' && typeof setNativeNutritionEnabled==='function'") == "true"
            }
            evaluate(main, "window.__nativeOverviewManual=true;lmaThumbWarm=async()=>{};state.onboardingComplete=true;state.tutorialDone=true;document.querySelectorAll('#onboarding,#tutorial').forEach(e=>{e.classList.remove('show');e.style.setProperty('display','none','important')});document.body.classList.remove('noscroll');save();renderAll();navigate('character');true")
            evaluate(main, "openNativeCharacter().then(r=>window.__sharedCharacterOpened=r);true")
            waitUntil("Character with shared chrome") {
                var ready = false
                instrumentation.runOnMainSync {
                    val root = main.window.decorView
                    ready = root.findViewWithTag<View>("native-character-screen") != null &&
                        root.findViewWithTag<View>("native-shared-chrome") != null
                }
                ready && evaluate(main, "window.__sharedCharacterOpened===true") == "true"
            }
            evaluate(main, "navigate('meals');true")
            waitUntil("Character overlay removed") {
                var gone = false
                instrumentation.runOnMainSync {
                    gone = main.window.decorView.findViewWithTag<View>("native-character-screen") == null
                }
                gone
            }
            waitUntil("Web Food page active") { evaluate(main, "lmaView()") == "\"meals\"" }
            SystemClock.sleep(500)
            val webFood = shot(main.bridge.webView, "food-web.png")
            evaluate(main, "setNativeNutritionEnabled(true);true")
            waitUntil("Food with shared chrome") {
                var ready = false
                instrumentation.runOnMainSync {
                    val root = main.window.decorView
                    ready = root.findViewWithTag<View>("native-character-screen") == null &&
                        root.findViewWithTag<View>("native-shared-chrome") != null &&
                        root.findViewWithTag<View>("native-nutrition-screen") != null
                }
                ready && evaluate(main, "window.NativeNutritionBeta?.snapshot()!=null") == "true"
            }
            SystemClock.sleep(900)
            val nativeFood = shot(main.bridge.webView, "food-native.png")
            var changedPixels = 0
            for (y in nativeFood.height / 4 until nativeFood.height * 3 / 4 step 4) {
                for (x in 0 until nativeFood.width step 4) {
                    if (nativeFood.getPixel(x, y) != webFood.getPixel(x, y)) changedPixels++
                }
            }
            assertTrue("Food Compose body must render over the WebView", changedPixels > 3000)
            repeat(3) {
                clickSharedTab("Character")
                waitUntil("Character opens from native Food bar") {
                    var attached = false
                    instrumentation.runOnMainSync { attached = main.window.decorView.findViewWithTag<View>("native-character-screen") != null }
                    attached && evaluate(main, "lmaView()") == "\"character\""
                }
                clickSharedTab("Food")
                waitUntil("Food opens from native Character bar") {
                    var attached = false
                    instrumentation.runOnMainSync { attached = main.window.decorView.findViewWithTag<View>("native-nutrition-screen") != null }
                    attached && evaluate(main, "lmaView()") == "\"meals\""
                }
            }
            clickSharedTab("Overview")
            waitUntil("Overview opens from native Food bar") { evaluate(main, "lmaView()") == "\"dashboard\"" }
            waitUntil("native Food chrome removed") {
                var gone = false
                instrumentation.runOnMainSync {
                    gone = main.window.decorView.findViewWithTag<View>("native-shared-chrome") == null
                }
                gone
            }
            assertEquals("Default native tabs stay adjacent", "true", evaluate(main, "(()=>{let ids=[...document.querySelectorAll('.sidebar [data-view]')].map(e=>e.dataset.view);let i=ids.indexOf('dashboard');return i>=0&&ids[i+1]==='character'&&ids[i+2]==='food'})()"))
        }
    }

}
