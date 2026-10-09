package pe.gob.reniec.dnie.ui.design

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A check means this specific step completed; it does not claim certificate trust. */
@Composable fun StatusCheck(label: String, complete: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (complete) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (complete) "Completado" else "Pendiente",
            tint = if (complete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
