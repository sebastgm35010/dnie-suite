package pe.gob.reniec.dnie.data.feedback

import android.content.Context
import android.os.*
import android.media.AudioAttributes
import pe.gob.reniec.dnie.domain.feedback.ReadingCue

/** Best effort tactile adapter. Does not authorize, retry or alter any card operation. */
object ReadingFeedback {
    @Volatile var enabled=true
    private var lastProgress=0L
    fun start(context: Context)=play(context,ReadingCue.START)
    fun progressTick(context: Context)=play(context,ReadingCue.PROGRESS)
    fun success(context: Context)=play(context,ReadingCue.SUCCESS)
    fun error(context: Context)=play(context,ReadingCue.ERROR)
    fun play(context: Context,cue: ReadingCue) {
        if(!enabled)return
        if(cue==ReadingCue.PROGRESS) synchronized(this) {
            val now=SystemClock.elapsedRealtime();if(now-lastProgress<100)return;lastProgress=now
        }
        val (timings,amplitudes)=when(cue) {
            ReadingCue.START->longArrayOf(0,65) to intArrayOf(0,190)
            ReadingCue.PROGRESS->longArrayOf(0,25) to intArrayOf(0,95)
            ReadingCue.SUCCESS->longArrayOf(0,45,65,110) to intArrayOf(0,140,0,255)
            ReadingCue.ERROR->longArrayOf(0,70,55,85) to intArrayOf(0,255,0,220)
        }
        runCatching {
            val vibrator=if(Build.VERSION.SDK_INT>=31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator else context.getSystemService(Vibrator::class.java)
            if(vibrator?.hasVibrator()!=true)return
            if(Build.VERSION.SDK_INT>=26) {
                val effect=if(vibrator.hasAmplitudeControl()) VibrationEffect.createWaveform(timings,amplitudes,-1) else VibrationEffect.createWaveform(timings,-1)
                if(Build.VERSION.SDK_INT>=33) vibrator.vibrate(effect,VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK).build())
                else vibrator.vibrate(effect,AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            } else {@Suppress("DEPRECATION") vibrator.vibrate(timings,-1)}
        }
    }
}
