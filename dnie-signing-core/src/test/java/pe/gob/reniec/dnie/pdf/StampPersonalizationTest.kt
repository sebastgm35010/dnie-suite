package pe.gob.reniec.dnie.pdf

import org.junit.Test
import org.junit.Assert.*

class StampPersonalizationTest {
    private val measure:(String,Boolean,Float)->Float={s,_,size->s.length*size*.55f}
    @Test fun transparentContainerAndBorderAreIndependentAndOldOrdinalsStayStable() {
        assertEquals(1,StampBadgeType.QR_VERIFICATION.ordinal);assertEquals(2,StampBadgeType.NONE.ordinal)
        for(style in listOf(StampStyle.CARD,StampStyle.INSTITUTIONAL)) for(border in listOf(true,false)) {
            val a=SignatureAppearance(style=style,backgroundMode=StampBackgroundMode.TRANSPARENT,showBorder=border)
            val scene=StampLayoutEngine.layout(400f,180f,"ANA QUISPE","12345678",PdfSigningOptions(appearance=a),0,measure)
            val container=scene.elements.filterIsInstance<StampElement.Box>().single {it.width>390}
            assertNull(container.fill);assertEquals(border,container.stroke!=null)
        }
    }
    @Test fun newBadgesDatesAndDensitiesRenderWithoutOverflow() {
        for(badge in listOf(StampBadgeType.CHECK_SEAL,StampBadgeType.LOCK_SEAL)) for(date in StampDateFormat.entries) for(density in StampDensity.entries) {
            val scene=StampLayoutEngine.layout(400f,180f,"ANA QUISPE","12345678",PdfSigningOptions(appearance=SignatureAppearance(badge=badge,dateFormat=date,density=density)),0,measure)
            assertTrue(scene.elements.any {it is StampElement.Circle})
            val text=scene.elements.filterIsInstance<StampElement.Text>()
            assertTrue(text.all {it.x>=0 && it.x+measure(it.text,it.bold,it.size)<=400.01f && it.baseline<=180})
            val rendered=text.joinToString(" ") {it.text}
            assertTrue(rendered.contains(when(date){StampDateFormat.CLASSIC_PE->"31/12/1969";StampDateFormat.COMPACT_HUMAN->"dic";else->"1969-12-31"}))
        }
    }
    @Test fun sharedPreferencesRoundTripAndNeverRecoverTransientImagesOrDocumentMetadata() {
        val original=PdfSigningOptions(reason="Conformidad",location="Lima, Perú",reniecTimestamp=true,pageTarget=StampPageTarget.ALL_PAGES,
            appearance=SignatureAppearance(backgroundMode=StampBackgroundMode.TRANSPARENT,dateFormat=StampDateFormat.ISO_8601,density=StampDensity.COMPACT,badge=StampBadgeType.LOCK_SEAL,colorTheme=StampColorTheme.ROYAL_INDIGO,showBorder=false))
        assertEquals(original,StampDesignCodec.decode(StampDesignCodec.encode(original)))
        val transient=original.copy(appearance=original.appearance.copy(style=StampStyle.INK,handwrittenPng=byteArrayOf(9,8,7),showHandwritten=true),qrMetadata=StampQrMetadata.PREVIEW)
        val restored=StampDesignCodec.decode(StampDesignCodec.encode(transient))
        assertNull(restored.appearance.handwrittenPng);assertTrue(restored.appearance.requiresHandwritten);assertNull(restored.qrMetadata)
        assertEquals(StampBadgeType.SEAL_VECTOR,StampDesignCodec.decode("badge=QR_VERIFICATION\nleft=NaN").appearance.badge)
        assertEquals(NormalizedStampPlacement(),StampDesignCodec.decode("width=NaN").placement)
    }
    @Test fun templatesPreserveSigningIntentAndCornerAnchorsRespectMargin() {
        for(template in StampTemplate.entries) {
            val o=template.apply(PdfSigningOptions(reniecTimestamp=true,reason="Personal",location="Cusco"))
            assertTrue(o.reniecTimestamp);assertEquals("Personal",o.reason);assertEquals("Cusco",o.location)
            assertNotEquals(StampBadgeType.QR_VERIFICATION,o.appearance.badge)
        }
        val p=NormalizedStampPlacement(.2f,.2f,.4f,.2f)
        assertEquals(.06f,StampPlacementGeometry.preset(p,SignaturePreset.TOP_LEFT).left,.00001f)
        val bottom=StampPlacementGeometry.preset(p,SignaturePreset.BOTTOM_RIGHT)
        assertEquals(.94f,bottom.left+bottom.width,.00001f);assertEquals(.94f,bottom.top+bottom.height,.00001f)
    }
}
