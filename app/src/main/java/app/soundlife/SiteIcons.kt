package app.soundlife

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Значки как на сайте: контурные, толщина 2, скруглённые концы (тот же рисунок, что в SVG-спрайте сайта). */
internal object SiteIcons {
    private fun icon(vararg paths: String): ImageVector {
        val b = ImageVector.Builder("site", 24.dp, 24.dp, 24f, 24f)
        paths.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }
    val Lib by lazy { icon("M4 4v16M9 4v16M14 6l5 14M14 6l-1 .3") }
    val Disc by lazy { icon("M3,12 a9,9 0 1 0 18,0 a9,9 0 1 0 -18,0", "M9.5,12 a2.5,2.5 0 1 0 5,0 a2.5,2.5 0 1 0 -5,0") }
    val User by lazy { icon("M8,8 a4,4 0 1 0 8,0 a4,4 0 1 0 -8,0", "M4 21a8 8 0 0 1 16 0") }
    val List by lazy { icon("M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01") }
    val Heart by lazy { icon("M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21.3l8.8-8.9a5.5 5.5 0 0 0 0-7.8Z") }
    val Clock by lazy { icon("M3,12 a9,9 0 1 0 18,0 a9,9 0 1 0 -18,0", "M12 7v5l3 2") }
    val Chart by lazy { icon("M5 21V10M12 21V4M19 21v-8") }
    val Cog by lazy { icon("M9,12 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0", "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z") }
    val Msg by lazy { icon("M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z") }
    val Dl by lazy { icon("M12 3v12m0 0-5-5m5 5 5-5M4 21h16") }
    val Search by lazy { icon("M4,11 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0", "m21 21-4.3-4.3") }
    val News by lazy { icon("M3 10v4a1 1 0 0 0 1 1h2l6 4V5L6 9H4a1 1 0 0 0-1 1zM16 9a4 4 0 0 1 0 6M19 6a8 8 0 0 1 0 12") }
    val X by lazy { icon("M6 6l12 12M18 6 6 18") }
}
