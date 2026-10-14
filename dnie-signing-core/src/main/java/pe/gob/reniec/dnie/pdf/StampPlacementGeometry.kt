package pe.gob.reniec.dnie.pdf

/** Shared interaction geometry in visible-page fractions. */
object StampPlacementGeometry {
    enum class Handle { MOVE, N, NE, E, SE, S, SW, W, NW }
    data class Snap(val placement: NormalizedStampPlacement,val vertical: Float?,val horizontal: Float?)
    fun resize(p: NormalizedStampPlacement,dx: Float,dy: Float,handle: Handle): NormalizedStampPlacement {
        require(dx.isFinite() && dy.isFinite())
        if(handle==Handle.MOVE) return p.moved(p.left+dx,p.top+dy)
        val west=handle in listOf(Handle.W,Handle.NW,Handle.SW);val east=handle in listOf(Handle.E,Handle.NE,Handle.SE)
        val north=handle in listOf(Handle.N,Handle.NW,Handle.NE);val south=handle in listOf(Handle.S,Handle.SW,Handle.SE)
        val right=p.left+p.width;val bottom=p.top+p.height
        val left=if(west) (p.left+dx).coerceIn(0f,right-.05f) else p.left
        val top=if(north) (p.top+dy).coerceIn(0f,bottom-.02f) else p.top
        val endX=if(east) (right+dx).coerceIn(left+.05f,1f) else right
        val endY=if(south) (bottom+dy).coerceIn(top+.02f,1f) else bottom
        return NormalizedStampPlacement(left,top,endX-left,endY-top)
    }
    fun snap(p: NormalizedStampPlacement,threshold: Float=.012f): Snap {
        val horizontal=listOf(.05f to .05f,.5f to (.5f-p.width/2),.95f to (.95f-p.width))
        val vertical=listOf(.05f to .05f,.5f to (.5f-p.height/2),.95f to (.95f-p.height))
        val x=horizontal.filter { it.second in 0f..1f-p.width }.minByOrNull { kotlin.math.abs(p.left-it.second) }?.takeIf { kotlin.math.abs(p.left-it.second)<threshold }
        val y=vertical.filter { it.second in 0f..1f-p.height }.minByOrNull { kotlin.math.abs(p.top-it.second) }?.takeIf { kotlin.math.abs(p.top-it.second)<threshold }
        return Snap(p.moved(x?.second?:p.left,y?.second?:p.top),x?.first,y?.first)
    }
    fun preset(p: NormalizedStampPlacement,preset: SignaturePreset): NormalizedStampPlacement = p.moved(
        if(preset in listOf(SignaturePreset.TOP_LEFT,SignaturePreset.BOTTOM_LEFT)) .06f else .94f-p.width,
        if(preset in listOf(SignaturePreset.TOP_LEFT,SignaturePreset.TOP_RIGHT)) .06f else .94f-p.height)
}
