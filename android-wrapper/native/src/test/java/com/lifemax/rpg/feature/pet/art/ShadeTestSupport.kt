package com.lifemax.rpg.feature.pet.art

import org.junit.Assert.assertTrue

/** One sheet under test: where it came from, its index pixels and the coat's colours. */
class SheetCase(
    val label: String,
    val kind: SheetKind,
    val sheet: IndexSheet,
    val palette: CatPalette,
)

/** Every shipped sheet and the genetic fixtures, for the shade-split tests. */
object ShadeTestSupport {
    val art: PetArt by lazy { PetArtResources.load() }

    val kinds: List<SheetKind> =
        listOf(SheetKind.Adult, SheetKind.Depth) +
            (0..5).map { SheetKind.Stage(it) } +
            (0..5).map { SheetKind.DepthStage(it) }

    /** The four shipped coats at every kind: 4 x 14 sheets. */
    val shipped: List<SheetCase> by lazy {
        PetArtFixtures.coats.flatMap { coat ->
            kinds.map {
                SheetCase("$coat ${it.key}", it, art.sheet(coat, it)!!, art.palette(coat)!!)
            }
        }
    }

    /** The four genetic fixture coats at adult, depth and stage 1. */
    val genetic: List<SheetCase> by lazy {
        CoatFixtures.coats.keys.sorted().flatMap { label ->
            val bridge = FakeCoatBridge()
            val source = PageCoatSource().also { it.bind(bridge.requester) }
            val key = CoatFixtures.coats.getValue(label)
            for ((name, kind) in CoatFixtures.kinds) {
                source.sheet(key, kind)
                assertTrue(source.onReply(CoatFixtures.reply(label, name, bridge.last().id)))
            }
            val loaded = PetArtResources.load(source)
            CoatFixtures.kinds.map { (name, kind) ->
                SheetCase("$label $name", kind, loaded.sheet(key, kind)!!, loaded.palette(key)!!)
            }
        }
    }

    val all: List<SheetCase>
        get() = shipped + genetic

    /** Every palette we ship or fixture, labelled. */
    val palettes: List<Pair<String, CatPalette>> by lazy {
        PetArtFixtures.coats.map { it to art.palette(it)!! } +
            genetic.map { it.label.substringBefore(' ') to it.palette }.distinctBy { it.first }
    }
}
