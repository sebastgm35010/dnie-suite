package pe.gob.reniec.dnie.pdf

import org.junit.Assert.*
import org.junit.Test

class StampLayoutEngineTest {
    private val measure: (String,Boolean,Float)->Float = { text,bold,size -> text.codePointCount(0,text.length)*size*if(bold) .56f else .5f }
    @Test fun wrapsWholeWordsAndHyphenatesOnlyOversizedTokens() {
        assertEquals(listOf("ANA MARIA","QUISPE"),StampLayoutEngine.wrap("ANA   MARIA QUISPE",54f,false,10f,measure))
        val pieces=StampLayoutEngine.wrap("SUPERCALIFRAGILISTICO",32f,false,10f,measure)
        assertTrue(pieces.dropLast(1).all { it.endsWith("-") })
        assertEquals("SUPERCALIFRAGILISTICO",pieces.joinToString("").replace("-",""))
        assertTrue(pieces.all { measure(it,false,10f)<=32f })
    }
    @Test fun stylesColorsTypographyAndTsaStayWithinBounds() {
        for(style in listOf(StampStyle.INSTITUTIONAL,StampStyle.CARD,StampStyle.MINIMAL)) for(color in StampColorTheme.entries) {
            val scene=StampLayoutEngine.layout(350f,135f,"ANA MARÍA QUISPE MAMANI","12345678",
                PdfSigningOptions(reniecTimestamp=true,appearance=SignatureAppearance(style=style,colorTheme=color)),0,measure)
            val text=scene.elements.filterIsInstance<StampElement.Text>()
            assertTrue(scene.baseSize in 5f..11f)
            assertTrue(text.any { it.bold && it.color==color.titleRgb })
            assertTrue(text.any { !it.bold })
            assertTrue(text.all { it.x>=0 && it.x+measure(it.text,it.bold,it.size)<=350f && it.baseline in 0f..135f })
            assertTrue(text.any { it.text.contains("TSA RENIEC") })
            if(style==StampStyle.MINIMAL) assertFalse(scene.elements.filterIsInstance<StampElement.Box>().any { it.width==349.3f })
        }
    }
    @Test fun hiddenDataDoesNotLeakInQrAndBadgeIsOptional() {
        val options=PdfSigningOptions(appearance=SignatureAppearance(showDni=false,showDate=false,showTime=false,showTsaBadge=false))
        val payload=StampLayoutEngine.publicQrPayload("ANA|TEST","SECRET",options,0)
        val decoded=StampQrCodec.decode(payload)
        assertNull(decoded.dni);assertNull(decoded.moment);assertEquals("ANA|TEST",decoded.name)
        assertEquals("19:00:00-05:00",StampQrCodec.decode(StampLayoutEngine.publicQrPayload("ANA","123",options.copy(appearance=options.appearance.copy(showTime=true)),0)).moment)
        val scene=StampLayoutEngine.layout(300f,110f,"ANA","12345678",options.copy(reniecTimestamp=true),0,measure)
        assertFalse(scene.elements.filterIsInstance<StampElement.Text>().any { it.text.contains("TSA") })
    }
    @Test fun pageTargetsResolvePerDocumentAndRejectMissingSpecificPage() {
        assertEquals(listOf(1),StampPageTarget.FIRST_PAGE.pages(8,99))
        assertEquals(listOf(8),StampPageTarget.LAST_PAGE.pages(8,99))
        assertEquals((1..8).toList(),StampPageTarget.ALL_PAGES.pages(8,99))
        assertEquals(listOf(3),StampPageTarget.SPECIFIC_PAGE.pages(8,3))
        assertThrows(IllegalArgumentException::class.java) { StampPageTarget.SPECIFIC_PAGE.pages(2,3) }
    }
    @Test fun longFullNameFitsInAtMostTwoLinesWithoutTruncation() {
        val name="ALEXANDRA FERNANDA GUADALUPE QUISPE MAMANI"
        val scene=StampLayoutEngine.layout(200f,200f,name,"12345678",PdfSigningOptions(),0,measure)
        val lines=scene.elements.filterIsInstance<StampElement.Text>().filter { it.color==StampColorTheme.AETHER_BLUE.titleRgb }
        assertTrue(lines.size in 1..2)
        assertEquals(name.replace(" ",""),lines.joinToString("") { it.text }.replace(" ",""))
        assertTrue(scene.baseSize<11f)
    }
    @Test fun handwrittenOnlyHasNoOtherElementsAndOverflowNeverTruncates() {
        val scene=StampLayoutEngine.layout(100f,30f,"IGNORED","IGNORED",PdfSigningOptions(appearance=SignatureAppearance(
            style=StampStyle.HANDWRITTEN_ONLY,handwrittenPng=byteArrayOf(1))),0,measure)
        assertEquals(1,scene.elements.size);assertTrue(scene.elements.single() is StampElement.Handwriting)
        assertThrows(IllegalArgumentException::class.java) {
            StampLayoutEngine.layout(30f,15f,"A very long complete legal name","123",PdfSigningOptions(),0,measure)
        }
    }
    @Test fun everyResizeHandleKeepsOppositeEdgesAndSnapHonorsMargins() {
        val p=NormalizedStampPlacement(.2f,.2f,.4f,.3f)
        for(handle in StampPlacementGeometry.Handle.entries.filter { it!=StampPlacementGeometry.Handle.MOVE }) {
            val resized=StampPlacementGeometry.resize(p,.02f,.01f,handle)
            if(handle in listOf(StampPlacementGeometry.Handle.W,StampPlacementGeometry.Handle.NW,StampPlacementGeometry.Handle.SW)) assertEquals(p.left+p.width,resized.left+resized.width,.0001f)
            if(handle in listOf(StampPlacementGeometry.Handle.N,StampPlacementGeometry.Handle.NW,StampPlacementGeometry.Handle.NE)) assertEquals(p.top+p.height,resized.top+resized.height,.0001f)
        }
        val centered=StampPlacementGeometry.snap(p.moved(.299f,.351f))
        assertEquals(.5f,centered.placement.left+centered.placement.width/2,.0001f)
        assertEquals(.5f,centered.placement.top+centered.placement.height/2,.0001f)
        assertEquals(.05f,StampPlacementGeometry.snap(p.moved(.051f,.051f)).placement.left,.0001f)
    }
    @Test fun decorativeEmblemHasFilledShieldAndWhiteCheckWithoutChangingQrPayload() {
        val scene=StampLayoutEngine.layout(350f,135f,"ANA","12345678",PdfSigningOptions(),0,measure)
        assertEquals(2,scene.elements.filterIsInstance<StampElement.Circle>().size)
        assertTrue(scene.elements.filterIsInstance<StampElement.Path>().any {it.fill==StampColorTheme.AETHER_BLUE.accentRgb && it.closed})
        assertTrue(scene.elements.filterIsInstance<StampElement.Path>().any {it.color==0xFFFFFF && !it.closed})
        val record=StampQrCodec.decode(StampLayoutEngine.publicQrPayload("ANA","12345678",PdfSigningOptions(),0))
        assertTrue(record.metadata.preview);assertEquals("12345678",record.dni);assertEquals("1969-12-31T19:00:00-05:00",record.moment)
    }

}
