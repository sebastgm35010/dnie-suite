package pe.gob.reniec.dnie.ui.signer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pe.gob.reniec.dnie.data.signing.AndroidStampPreview
import pe.gob.reniec.dnie.pdf.*
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StampPageTargetSelector(target: StampPageTarget,enabled: Boolean,changed: (StampPageTarget)->Unit) {
    Text("Aplicar en",style=MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        StampPageTarget.entries.forEach { choice -> FilterChip(selected=choice==target,enabled=enabled,onClick={changed(choice)},label={Text(choice.label)}) }
    }
}

/** UI observes an immutable options snapshot; PDF work is delegated off the render thread. */
@Composable
internal fun StampPreviewCard(width: Float,height: Float,name: String,dni: String,options: PdfSigningOptions) {
    val context=LocalContext.current
    val date=remember { Date() }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(true) }
    LaunchedEffect(width,height,name,dni,options) {
        preparing=true;delay(160)
        try { bitmap=AndroidStampPreview.stamp(context,width,height,name,dni,options,date);error=null }
        catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(failure: Exception) { bitmap=null;error=failure.message?:"Amplía el sello o quita campos" }
        finally { preparing=false }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Vista previa del sello",style=MaterialTheme.typography.labelLarge)
            val grid=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.32f)
            Box(Modifier.fillMaxWidth().height(180.dp),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val step=16.dp.toPx()
                    var x=0f;while(x<size.width) { drawLine(grid,Offset(x,0f),Offset(x,size.height),1f);x+=step }
                    var y=0f;while(y<size.height) { drawLine(grid,Offset(0f,y),Offset(size.width,y),1f);y+=step }
                }
                bitmap?.let { Image(it.asImageBitmap(),"Vista previa del sello configurado",Modifier.fillMaxSize().padding(8.dp),contentScale=ContentScale.Fit) }
                if(bitmap==null && error==null) Text("Preparando sello…",style=MaterialTheme.typography.bodySmall)
            }
            if(preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        }
    }
}
