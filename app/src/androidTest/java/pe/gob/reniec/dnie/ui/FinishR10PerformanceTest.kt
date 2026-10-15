package pe.gob.reniec.dnie.ui

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import pe.gob.reniec.dnie.MainActivity
import java.util.Collections

/** Measures real rendered UI frames; no card commands, network requests, system setting changes or documents. */
class FinishR10PerformanceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun measureNavigationAndScrollingWithoutCardOperations() {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT>=24)
        val samples=Collections.synchronizedList(mutableListOf<Double>())
        val observedVsync=HashSet<Long>()
        var callbacks=0
        var droppedReports=0
        val worker=HandlerThread("R10FrameProbe").apply{start()}
        val listener=Window.OnFrameMetricsAvailableListener {_,metrics,dropped ->
            callbacks++;droppedReports+=dropped
            val vsync=metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            if(metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME)==0L && (vsync<=0 || observedVsync.add(vsync))) samples.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)/1_000_000.0)
        }
        rule.runOnUiThread {rule.activity.window.addOnFrameMetricsAvailableListener(listener,Handler(worker.looper))}
        val started=android.os.SystemClock.elapsedRealtime()
        try {
            repeat(4) {
                for(route in listOf("nav_home","nav_signer","nav_vault","nav_certificates","nav_home")) {
                    rule.onNodeWithTag(route).performClick();rule.waitForIdle()
                    rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().takeIf{it.isNotEmpty()}?.let {
                        rule.onAllNodes(hasScrollAction()).onFirst().performTouchInput {swipeUp(durationMillis=400);swipeDown(durationMillis=400)}
                    }
                }
            }
        } finally {rule.runOnUiThread{rule.activity.window.removeOnFrameMetricsAvailableListener(listener)};worker.quitSafely();worker.join(3000)}
        val sorted=synchronized(samples){samples.sorted()}
        assertTrue("Must collect rendered frames",sorted.isNotEmpty())
        val report=org.json.JSONObject().apply {
            put("elapsedMs",android.os.SystemClock.elapsedRealtime()-started);put("callbacks",callbacks);put("droppedReports",droppedReports);put("deduplicatedByIntendedVsync",true)
            put("samples",sorted.size);put("p50Ms",sorted[(sorted.lastIndex*.50).toInt()]);put("p95Ms",sorted[(sorted.lastIndex*.95).toInt()]);put("maxMs",sorted.last());put("over16_67Ms",sorted.count{it>1000.0/60});put("over33_33Ms",sorted.count{it>1000.0/30});put("physicalCardOperations",0);put("guarantees60Fps",false)
        }
        java.io.File(rule.activity.cacheDir,"finish-r10-performance.json").writeText(report.toString(2),Charsets.UTF_8)
    }
}
