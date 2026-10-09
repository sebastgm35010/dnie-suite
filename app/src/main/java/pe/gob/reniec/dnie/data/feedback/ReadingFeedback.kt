package pe.gob.reniec.dnie.data.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.media.AudioAttributes
import android.os.Vibrator
import android.os.VibratorManager

/** Best-effort physical reading feedback. Never blocks or changes the card operation. */
object ReadingFeedback {
    fun start(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else context.getSystemService(Vibrator::class.java)
            if (vibrator?.hasVibrator() != true) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                vibrator.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE),
                    VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_PHYSICAL_EMULATION).build())
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            else { @Suppress("DEPRECATION") vibrator.vibrate(90) }
        }
    }
}
