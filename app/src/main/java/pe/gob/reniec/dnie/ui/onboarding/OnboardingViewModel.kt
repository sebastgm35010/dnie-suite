package pe.gob.reniec.dnie.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import pe.gob.reniec.dnie.data.preferences.CanStorage

data class OnboardingState(val can: String = "", val saving: Boolean = false, val saved: Boolean = false, val message: String? = null)

/** Public CAN only; this guide never authorizes NFC, asks for PIN or calls a card engine. */
class OnboardingViewModel(private val save: suspend (Context, String) -> Unit = { context, can ->
    withContext(Dispatchers.IO) {
        CanStorage.saveCan(context, can)
        check(CanStorage.getSavedCan(context) == can) { "CAN persistence failed" }
    }
}) : ViewModel() {
    private val current = MutableStateFlow(OnboardingState())
    val state = current.asStateFlow()
    fun changeCan(value: String) { if (!current.value.saving && value.length <= 6 && value.all { it in '0'..'9' }) current.value = OnboardingState(can = value) }
    fun saveCan(context: Context) {
        val value = current.value
        if (value.saving || value.can.length != 6) return
        val app = context.applicationContext
        current.value = value.copy(saving = true)
        viewModelScope.launch {
            try {
                save(app, value.can)
                current.value = OnboardingState(saved = true, message = "CAN guardado en este teléfono")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { current.value = value.copy(message = "No se pudo guardar el CAN. Puedes continuar sin guardarlo.") }
        }
    }
    fun clearInput() { if (!current.value.saving) current.value = OnboardingState() }
}
