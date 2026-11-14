package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.graphics.Color
import com.lifemax.rpg.feature.habits.domain.HabitMaterial
import com.lifemax.rpg.feature.habits.domain.SkyPhase

/*
 * The scene is a picture, not a page: its sky, ground and materials have colours of their own, the same in the
 * light and the dark theme (glass is glass at any hour), as the cat's room has. Everything around the picture
 * (the button, the sheets, the toast) takes the page's palette tokens.
 */

/** The world at one time of day. */
internal class SkyColors(
    val sky: List<Color>,
    val ground: List<Color>,
    val lot: Color,
    val lotEdge: Color,
    val line: Color,
    val far: Color,
    val near: Color,
    val haze: Color,
    val halo: Color,
    val orb: Color,
    /** Words drawn straight on the sky or the ground. */
    val ink: Color,
    val inkSoft: Color,
)

internal fun skyColors(phase: SkyPhase): SkyColors =
    when (phase) {
        SkyPhase.NIGHT ->
            SkyColors(
                sky = listOf(Color(0xFF060A18), Color(0xFF0B1330), Color(0xFF1D2C63)),
                ground = listOf(Color(0xFF141E45), Color(0xFF090D1F)),
                lot = Color(0xFF1C274D),
                lotEdge = Color(0xFF10173A),
                line = Color(0x338FA5FF),
                far = Color(0xFF0E1530),
                near = Color(0xFF131C3D),
                haze = Color(0x336E8CFF),
                halo = Color(0x2EE9EDFF),
                orb = Color(0xFFE9EDFF),
                ink = Color(0xFFE6ECFF),
                inkSoft = Color(0xFF9AA8D8),
            )
        SkyPhase.DUSK ->
            SkyColors(
                sky = listOf(Color(0xFF1D2350), Color(0xFF6B4B7C), Color(0xFFE39A74)),
                ground = listOf(Color(0xFF45345E), Color(0xFF221A36)),
                lot = Color(0xFF55436F),
                lotEdge = Color(0xFF33264A),
                line = Color(0x3DFFD6BE),
                far = Color(0xFF57406F),
                near = Color(0xFF412F5C),
                haze = Color(0x4DFFC496),
                halo = Color(0x66FFC896),
                orb = Color(0xFFFFD9A8),
                ink = Color(0xFFFFF1E6),
                inkSoft = Color(0xFFE3C4BC),
            )
        SkyPhase.DAY ->
            SkyColors(
                sky = listOf(Color(0xFF2F66B8), Color(0xFF6FA5DD), Color(0xFFC4E2F5)),
                ground = listOf(Color(0xFF8DBB99), Color(0xFF5F9276)),
                lot = Color(0xFFDBE3EE),
                lotEdge = Color(0xFFA3B2C8),
                line = Color(0x33283C6E),
                far = Color(0xFFA4C5E8),
                near = Color(0xFF86ADD9),
                haze = Color(0x66FFFFFF),
                halo = Color(0x66FFF3C4),
                orb = Color(0xFFFFF3C4),
                ink = Color(0xFFFFFFFF),
                inkSoft = Color(0xFFE3EEFA),
            )
    }

/**
 * A block's faces: the front from [frontTop] down to [frontBottom], its lit [top], its shaded
 * [side].
 */
internal class Faces(
    val frontTop: Color,
    val frontBottom: Color,
    val top: Color,
    val side: Color,
    /** Words on the front face. */
    val ink: Color,
    val inkSoft: Color,
    /** The material's own colour, for a blueprint's footing and the glow of the next block. */
    val tint: Color,
)

internal fun faces(material: HabitMaterial): Faces =
    when (material) {
        HabitMaterial.GLASS ->
            Faces(
                Color(0x8CA6DCFF),
                Color(0x66709ED6),
                Color(0xA6CDEBFF),
                Color(0x59608CC4),
                Color(0xFFF2F8FF),
                Color(0xCCDCEBFF),
                Color(0xFF9FD8FF),
            )
        HabitMaterial.CLAY ->
            Faces(
                Color(0xFFDE9C72),
                Color(0xFFB46D48),
                Color(0xFFEDB592),
                Color(0xFF935438),
                Color(0xFF3A1A0B),
                Color(0xB33A1A0B),
                Color(0xFFDD9A72),
            )
        HabitMaterial.STONE ->
            Faces(
                Color(0xFFB4C3E0),
                Color(0xFF8799BF),
                Color(0xFFD0DAF0),
                Color(0xFF6C7FA6),
                Color(0xFF18233D),
                Color(0xB318233D),
                Color(0xFFA9BBDC),
            )
        HabitMaterial.GOLD ->
            Faces(
                Color(0xFFFFF1B0),
                Color(0xFFB98A22),
                Color(0xFFFFF8D2),
                Color(0xFF9A6E18),
                Color(0xFF452E00),
                Color(0xB3452E00),
                Color(0xFFF3CF63),
            )
        HabitMaterial.FADE ->
            Faces(
                Color(0xFF7D3D59),
                Color(0xFF622D45),
                Color(0xFFA85C7B),
                Color(0xFF4E2437),
                Color(0xFFFFE9F1),
                Color(0xCCFFDCE8),
                Color(0xFFE08AAE),
            )
    }

internal object SceneInk {
    val SlabFrontTop = Color(0xFF56658E)
    val SlabFrontBottom = Color(0xFF414E75)
    val SlabTop = Color(0xFF7483AB)
    val SlabSide = Color(0xFF364263)
    val SlabText = Color(0xFFE8EDFF)

    /** A blueprint: a faint wash, a dashed outline. */
    val PlanFill = Color(0x1F8CA8FF)
    val PlanLine = Color(0x99B4C6FF)
    val PlanText = Color(0xFFDCE6FF)

    val GlassBacking = Color(0x8027406B)
    val GlassEdge = Color(0xFFE6F6FF)

    val Crack = Color(0xCC1A0D0D)

    /** A crack on a blueprint, which is see-through: pale, so it reads on any sky. */
    val CrackOnPlan = Color(0xF2FFB9A8)
    val Seam = Color(0xFFF6D679)
    val SeamGlow = Color(0x66FFE9A6)

    val WindowLit = Color(0xFFFFD27A)
    val WindowDim = Color(0xFFE9B45C)
    val WindowDark = Color(0xFF3A1626)

    val RewardLockTop = Color(0xFF9C8757)
    val RewardLockFront = Color(0xFF77643A)
    val RewardLockSide = Color(0xFF5E4F2C)
    val RewardLockText = Color(0xFFE9DFC2)
    val Flag = Color(0xFFFF6B6B)
    val FlagPole = Color(0xFFFFF7D6)

    val TreeTrunk = Color(0xFF6B4A2F)
    val TreeA = Color(0xFF3F9D6B)
    val TreeB = Color(0xFF348A5C)
    val TreeC = Color(0xFF46AD77)

    val Cat = Color(0xFF05070D)
    val CatEye = Color(0xFFF6D679)

    val Shadow = Color(0x73000000)
    val Occlusion = Color(0x47000000)
    val Highlight = Color(0x59FFFFFF)
    val Star = Color(0xFFD5DDFF)
    val CityLight = Color(0xFFFFD27A)
    val Cloud = Color(0xFFFFFFFF)
    val Dust = Color(0xFFE8EDFF)
    val Sheen = Color(0xD9FFFFFF)
}
