package pe.gob.reniec.dnie.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import pe.gob.reniec.dnie.ui.design.rememberReducedMotion

/** Illustrative scenes only. Wave state is consumed in drawing, never as progress or card status. */
@Composable internal fun GuideScene(step: Int) {
    val reduced = rememberReducedMotion()
    val wave = if (!reduced && step < 2) rememberInfiniteTransition(label = "guide-wave")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = LinearEasing)), label = "guide-wave-phase") else null
    val ink = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primaryContainer
    val green = MaterialTheme.colorScheme.secondary
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Canvas(Modifier.fillMaxWidth().height(136.dp).padding(12.dp).semantics {
            contentDescription = when(step) {0 -> "DNIe ilustrativo con CAN destacado";1 -> "Tarjeta junto al sensor NFC del teléfono";else -> "Teléfono y PC vinculados por un canal seguro"}
        }) {
            val w=180.dp.toPx();val h=96.dp.toPx();val x=(size.width-w)/2;val y=(size.height-h)/2
            val line=2.dp.toPx()
            fun frame(at: Offset, dimensions: Size, radius: Float=8.dp.toPx()) {
                drawRoundRect(fill, at, dimensions, CornerRadius(radius))
                drawRoundRect(ink, at, dimensions, CornerRadius(radius), style=Stroke(line))
            }
            if(step==0) {
                frame(Offset(x,y),Size(w,h),10.dp.toPx())
                drawRoundRect(ink,Offset(x+8.dp.toPx(),y+8.dp.toPx()),Size(w-16.dp.toPx(),12.dp.toPx()),CornerRadius(3.dp.toPx()))
                drawRoundRect(Color(0xFFD9A441),Offset(x+16.dp.toPx(),y+34.dp.toPx()),Size(30.dp.toPx(),24.dp.toPx()),CornerRadius(4.dp.toPx()))
                repeat(3) {drawLine(ink.copy(alpha=.5f),Offset(x+62.dp.toPx(),y+(33+it*12).dp.toPx()),Offset(x+w-16.dp.toPx(),y+(33+it*12).dp.toPx()),line)}
                val can=Offset(x+110.dp.toPx(),y+72.dp.toPx())
                drawRoundRect(ink.copy(alpha=.14f),can,Size(56.dp.toPx(),16.dp.toPx()),CornerRadius(4.dp.toPx()))
                repeat(6){drawCircle(ink,1.6.dp.toPx(),Offset(can.x+(8+it*8).dp.toPx(),can.y+8.dp.toPx()))}
                val fraction=wave?.value ?: 0f
                drawRoundRect(ink.copy(alpha=if(wave==null).4f else .5f*(1-fraction)),Offset(can.x-4.dp.toPx()*fraction,can.y-4.dp.toPx()*fraction),Size(56.dp.toPx()+8.dp.toPx()*fraction,16.dp.toPx()+8.dp.toPx()*fraction),CornerRadius(6.dp.toPx()),style=Stroke(line))
            } else {
                frame(Offset(x+12.dp.toPx(),y),Size(48.dp.toPx(),h))
                drawLine(ink,Offset(x+28.dp.toPx(),y+8.dp.toPx()),Offset(x+44.dp.toPx(),y+8.dp.toPx()),line)
                if(step==1) {
                    frame(Offset(x+46.dp.toPx(),y+24.dp.toPx()),Size(100.dp.toPx(),60.dp.toPx()))
                    val contact=Offset(x+50.dp.toPx(),y+37.dp.toPx())
                    repeat(2){i ->val f=wave?.let{(it.value+i*.5f)%1f} ?: (i*.5f)
                        drawCircle(green.copy(alpha=if(wave==null).4f else .5f*(1-f)),(12+f*28).dp.toPx(),contact,style=Stroke(line))}
                    drawCircle(green,4.dp.toPx(),contact)
                } else {
                    frame(Offset(x+98.dp.toPx(),y+16.dp.toPx()),Size(80.dp.toPx(),58.dp.toPx()))
                    drawLine(ink,Offset(x+138.dp.toPx(),y+74.dp.toPx()),Offset(x+138.dp.toPx(),y+90.dp.toPx()),line)
                    drawLine(ink,Offset(x+118.dp.toPx(),y+90.dp.toPx()),Offset(x+158.dp.toPx(),y+90.dp.toPx()),line)
                    drawLine(green,Offset(x+60.dp.toPx(),y+46.dp.toPx()),Offset(x+98.dp.toPx(),y+46.dp.toPx()),line,pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx())))
                    val shield=Path().apply{moveTo(x+79.dp.toPx(),y+30.dp.toPx());lineTo(x+89.dp.toPx(),y+35.dp.toPx());lineTo(x+86.dp.toPx(),y+48.dp.toPx());lineTo(x+79.dp.toPx(),y+55.dp.toPx());lineTo(x+72.dp.toPx(),y+48.dp.toPx());lineTo(x+69.dp.toPx(),y+35.dp.toPx());close()}
                    drawPath(shield,fill);drawPath(shield,green,style=Stroke(line))
                }
            }
        }
    }
}
