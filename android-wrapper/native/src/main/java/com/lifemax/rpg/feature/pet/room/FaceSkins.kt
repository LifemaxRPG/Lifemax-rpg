package com.lifemax.rpg.feature.pet.room

/**
 * The authored side and top textures of the room's solid pieces ([FaceSkin]), by piece name: what
 * the sofa's armrest end panels, the bookcase's side boards, the cat tree's sisal, the fridge's
 * enamel and every other piece look like where the camera's angle shows them. Every colour is one
 * the piece was painted with, so a side reads as the same object as its front.
 *
 * The textures are small pixel-art painters keyed to where a face pixel is ([FaceSpot]): its row,
 * its column, how far behind the front edge and before the back it is. The rules they share:
 * - A side is its depth drawn a few device pixels wide, so a texture changes along the depth only
 *   in bands ([FaceSpot.band]: a seam, a back edge in shadow), never strip by strip, or it would
 *   flicker as the camera turns; the detail runs along the rows (stripes, grain, hems), which hold.
 * - The front edge of a face (its first strip) is the edge of the board or the piping: lighter.
 * - Outlines carry round the silhouette: on a piece outlined in the art's ink, a side's top edge is
 *   inked where the front's top is, and an outline row of the front stays an outline only as far
 *   back as what it outlines (a door's edge, not the whole side).
 * - A face inside the piece (between two cushions, inside a shelf, the walls of the cubby's
 *   opening) is in the piece's own shadow, a step darker than the outside.
 * - Where a piece's colour ramps would step into another material's colours (the browns of wood
 *   share a hue with the terracotta and the red books), the wood is given its colours for each side
 *   outright ([Tones], [FaceTex.exact]).
 */
internal object FaceSkins {
    /** The skin of the piece called [name] (the generic one for a piece with no own textures). */
    fun of(name: String, model: VolumeModel): FaceSkin =
        when (name) {
            "sofa" -> FaceSkin(::sofa)
            "sofa_cushion_yellow",
            "sofa_cushion_pink" -> FaceSkin(::cushion)
            "floor_lamp" -> FaceSkin(::lamp)
            "side_table_plant" -> FaceSkin(::sideTable)
            "bookcase_plant" -> Bookcase(model, BOOKCASE, caseTop = 32)
            "study_shelf" -> Bookcase(model, STUDY, caseTop = 0)
            "cat_tree_post" -> FaceSkin(::post)
            "cat_tree_cubby" -> FaceSkin(::cubby)
            "cat_tree_slab_base",
            "cat_tree_slab_mid",
            "cat_tree_slab_top" -> Slab(model, 0 until model.height, null)
            "counter" -> Counter(model)
            "fridge" -> FaceSkin(::fridge)
            "sun_bench" -> FaceSkin(::bench)
            "wicker_chair" -> FaceSkin(::wickerChair)
            "big_plant" -> FaceSkin(::bigPlant)
            "desk" -> FaceSkin(::desk)
            "desk_chair" -> FaceSkin(::deskChair)
            "paper_basket",
            "laundry_basket" -> FaceSkin(::basket)
            "bathtub",
            "sink_cabinet" -> FaceSkin(::bathroom)
            else -> FaceSkin(::generic)
        }

    // ------------------------------------------------------------------ the pieces

    /**
     * The sofa: blue fabric, piping down the front edge of every panel, the rolled tops of the arms
     * and the back catching the light, the hem underneath in shadow, the gaps between the seat
     * cushions dark, and the outline carried along the top of the arms. The legs are wood.
     */
    private fun sofa(s: FaceSpot): Int {
        if (s.slope || s.y >= 38) return generic(s)
        if (s.inked) return FaceTex.KEEP
        if (s.top) return fabricTop(s, s.material)
        if (silhouette(s)) return FaceTex.INK
        if (!s.outer && s.y >= 21 && s.x in 16..115) return FaceTex.of(s.material, -1) // a gap
        val roll = s.fromTop in 1..2 // the rolled top of an arm or the back
        return fabric(s, s.material, if (roll) 1 else 0)
    }

    /** A cushion: its stripes carry round its sides, rounded and darker as they turn away. */
    private fun cushion(s: FaceSpot): Int {
        val body = s.body
        if (s.inked) return FaceTex.KEEP
        if (s.slope) return FaceTex.of(body, slopeLight(s) + if (s.along > 0.5f) -1 else 0)
        if (s.top) return fabricTop(s, body)
        if (silhouette(s)) return FaceTex.INK
        return fabric(s, body, 0)
    }

    /**
     * The floor lamp: a round cloth shade darkening as it turns, its open top showing its glowing
     * inside within the rim; a round pole and base.
     */
    private fun lamp(s: FaceSpot): Int {
        if (s.y <= 15) {
            if (s.top && !s.slope) {
                val rim = s.behind <= 1 || s.before <= 1
                return if (rim) FaceTex.exact(s.colour(SHADE_RIM))
                else FaceTex.exact(s.colour(SHADE_GLOW))
            }
            // the cloth's own three colours (its ramp runs on into the brown of the pole)
            val cloth = SHADE.map { s.colour(it) }
            var k = cloth.indexOf(s.body).coerceAtLeast(0)
            if (s.along > 0.55f) k++
            if (s.face == Face.RIGHT) k++
            return FaceTex.exact(cloth[k.coerceAtMost(cloth.size - 1)])
        }
        return round(s, s.body)
    }

    /** The side table: leaves, a round terracotta pot with soil in it, a wooden table. */
    private fun sideTable(s: FaceSpot): Int =
        when {
            s.y < 34 && s.material in s.coloursOf(TERRACOTTA) -> pot(s, 0xFF5B3A20.toInt())
            s.y < 34 -> foliage(s)
            else -> wood(s, SIDE_TABLE)
        }

    /**
     * The cat tree's post: round, its sisal wrap carrying on round the back (the stripes keep their
     * slant: they move two columns a row on the front, so going round they move on by rows), darker
     * as it turns away; the hanging toy and its string are round.
     */
    private fun post(s: FaceSpot): Int {
        val sisal = s.coloursOf(SISAL)
        if (s.body !in sisal || s.top) return round(s, s.body)
        if (!s.slope && !s.side) return generic(s)
        val inward = if (s.face == Face.LEFT) 1 else -1
        val turn = ((s.depth - s.front) * 0.75f).toInt()
        var row = s.y - inward * (turn % 4)
        if (s.at(s.x + inward, row) !in sisal) row = s.y
        val painted = s.at(s.x + inward, row).takeIf { it in sisal } ?: s.body
        val turned = if (s.along > 0.5f) -1 else 0
        val lit = if (s.slope) slopeLight(s) else 0
        return FaceTex.of(painted, turned + lit)
    }

    /**
     * The cubby: carpeted side walls with the light trim at the front edge and a carpeted top; the
     * walls of its opening (only where the front has one) dark inside, their front edge lit.
     */
    private fun cubby(s: FaceSpot): Int {
        val trim = s.colour(0xFFB49AE0.toInt())
        val wall = s.colour(0xFF664A96.toInt())
        val inside = s.colour(0xFF382852.toInt())
        if (s.slope) return generic(s)
        if (s.inked || !s.outer) {
            return FaceTex.exact(if (s.behind <= 1 && !s.inked) wall else inside)
        }
        if (s.top) return carpet(s, wall)
        if (s.behind == 1) return FaceTex.of(trim, 0)
        return carpet(s, wall)
    }

    /**
     * The fridge: white enamel. The doors are a row thick, their edge catching the light and the
     * seal between them and the body a grey line; the body behind them white, a shade darker
     * towards the back; the top a little shadowed where it meets the wall.
     */
    private fun fridge(s: FaceSpot): Int {
        val white = s.colour(0xFFF6F8F4.toInt())
        if (s.slope) return generic(s)
        if (s.top) {
            if (s.inked) return FaceTex.KEEP
            return FaceTex.of(white, if (s.band(4) == 3) -1 else if (s.behind == 1) 1 else 0)
        }
        if (s.slope) return generic(s)
        if (silhouette(s)) return FaceTex.INK
        val door = s.behind <= 2
        if (s.y >= 96) return FaceTex.of(s.material, -1) // the kick plate
        if (s.inked) return if (door) FaceTex.KEEP else FaceTex.of(white, 0)
        return when {
            door && s.y in 34..36 -> FaceTex.of(s.material, 0) // the gap between the doors
            s.behind == 1 -> FaceTex.of(white, 1)
            door -> FaceTex.of(white, 0)
            s.behind in 3..4 -> FaceTex.of(white, -2) // the seal
            else -> FaceTex.of(white, if (s.along > 0.6f) -1 else 0)
        }
    }

    /** The sun room's bench: a wooden frame with drawers, a cushion and a pillow on it. */
    private fun bench(s: FaceSpot): Int =
        if (s.material in s.coloursOf(BENCH.all)) wood(s, BENCH) else upholstery(s)

    /** The wicker chair: a woven seat and back on a wooden frame, a green cushion. */
    private fun wickerChair(s: FaceSpot): Int =
        when {
            s.material in s.coloursOf(GREENS) -> upholstery(s)
            else -> weave(s)
        }

    /** The big plant: a bushy crown of leaves, a round terracotta pot. */
    private fun bigPlant(s: FaceSpot): Int =
        if (s.material in s.coloursOf(TERRACOTTA)) pot(s, 0xFF9C532E.toInt()) else foliage(s)

    /** The desk: a green top, a wooden frame and drawers, things on top. */
    private fun desk(s: FaceSpot): Int =
        when {
            s.material in s.coloursOf(DESK.all) -> wood(s, DESK)
            s.material in s.coloursOf(GREENS) -> panel(s, s.material)
            else -> generic(s)
        }

    /** The desk chair: green upholstery on a wooden frame. */
    private fun deskChair(s: FaceSpot): Int =
        if (s.material in s.coloursOf(CHAIR.all)) wood(s, CHAIR) else upholstery(s)

    /** A wicker basket: woven round its sides, whatever is in it soft on top. */
    private fun basket(s: FaceSpot): Int =
        if (s.material in s.coloursOf(WICKER)) weave(s) else upholstery(s)

    /**
     * The bath and the sink cabinet: white enamel and painted wood, a front edge catching light.
     */
    private fun bathroom(s: FaceSpot): Int {
        if (s.inked) return FaceTex.KEEP
        return panel(s, s.body, gradient = true)
    }

    /**
     * A bookcase: wooden side boards (grain running up them, a lighter edge banding at the front)
     * that hide the books from the side, the top and the shelf boards with their grain, the tops of
     * the books their pages between the covers, the inside of the shelves in shadow; a potted plant
     * on top.
     *
     * @param tones the case's wood.
     * @param caseTop the first row of the case (above it, the plant).
     */
    private class Bookcase(model: VolumeModel, private val tones: Tones, private val caseTop: Int) :
        FaceSkin {
        private val wood = tones.all.map { model.palette.indexOf(it) }.filter { it >= 0 }.toSet()
        private val pages = model.palette.indexOf(PAGES)

        override fun paint(spot: FaceSpot): Int {
            val s = spot
            if (s.y < caseTop) {
                return if (s.material in s.coloursOf(TERRACOTTA)) pot(s, 0xFF432A18.toInt())
                else foliage(s)
            }
            val isWood = s.material in wood
            if (s.top) {
                if (isWood) return woodTop(s, tones)
                if (s.behind <= 1 || s.before <= 1) return FaceTex.of(s.material, 0) // covers
                return if (pages >= 0) FaceTex.exact(pages) else FaceTex.of(s.material, 1)
            }
            if (s.side && s.outer) return woodSide(s, tones, vertical = true)
            if (s.slope) return generic(s)
            if (isWood) return FaceTex.exact(s.colour(tones.side(s).grain)) // inside a shelf
            return FaceTex.of(s.material, -1) // a book's cover, in the shelf's shadow
        }
    }

    /**
     * A board seen from its side (a platform of the cat tree, the counter's worktop): as thick as
     * the front shows its edge, the side rises towards the back as the top recedes (the art paints
     * the top as rows above the edge), and below the board it is dark, its underside. The top is
     * carpet, or wood in [tones].
     */
    private class Slab(
        private val model: VolumeModel,
        private val rows: IntRange,
        private val tones: Tones?,
    ) : FaceSkin {
        private val edges = Array(model.width) { edgeRows(model, it, rows) }

        override fun paint(spot: FaceSpot): Int {
            val s = spot
            if (s.slope || (s.side && !s.outer)) return generic(s) // as painted; inside
            if (s.top) return if (tones == null) carpet(s, s.material) else woodTop(s, tones)
            val edge = edges[s.x]
            if (s.fromTop > edge.last - edge.first) {
                return if (tones == null) FaceTex.exact(model.palette.ink) // underneath
                else FaceTex.exact(s.colour(tones.side(s).grain))
            }
            val row = edge.first + s.fromTop
            val inward = s.at(s.x + if (s.face == Face.LEFT) 1 else -1, row)
            val painted = if (inward >= 0) inward else s.at(s.x, row)
            if (tones != null) {
                val side = tones.side(s)
                if (s.behind == 1 || s.fromTop == 0) return FaceTex.exact(s.colour(side.edge))
                if (painted == model.palette.ink || s.fromTop == edge.last - edge.first) {
                    return FaceTex.exact(s.colour(side.grain))
                }
                return FaceTex.exact(s.colour(side.panel))
            }
            return carpet(s, painted)
        }
    }

    /**
     * The kitchen counter: the worktop's edge as thick as the front shows it, its grain along the
     * top; the cabinets' side panel with the doors' edge, the gap behind them and the shadow under
     * the worktop; the kettle, the tap and the fruit round.
     */
    private class Counter(model: VolumeModel) : FaceSkin {
        private val worktop = Slab(model, 14..21, WORKTOP)

        override fun paint(spot: FaceSpot): Int {
            val s = spot
            if (s.y < 14) return round(s, s.body)
            if (s.y < 22) return worktop.paint(s)
            if (s.y >= 48) return panel(s, s.material)
            if (s.top) return generic(s)
            if (!s.outer) return FaceTex.of(s.material, -1)
            val carcass = s.colour(0xFF80A68C.toInt())
            return when {
                s.fromTop <= 1 -> FaceTex.of(carcass, -1) // under the worktop
                s.behind == 1 -> FaceTex.of(s.colour(0xFF96BCA0.toInt()), 0) // the door's edge
                s.behind == 2 -> FaceTex.of(s.colour(0xFF62846E.toInt()), 0) // the gap
                else -> FaceTex.of(carcass, 0)
            }
        }
    }

    // ------------------------------------------------------------------ the textures

    /** Any face with no texture of its own: its colour, edges catching the light, insides dark. */
    fun generic(s: FaceSpot): Int {
        if (s.slope || s.inked) return FaceTex.KEEP
        if (s.side) {
            if (silhouette(s)) return FaceTex.INK
            var steps = 0
            if (!s.outer) steps -= 1
            if (s.behind == 1) steps += 1
            return FaceTex.of(s.material, steps)
        }
        return FaceTex.of(s.material, if (s.band(4) == 3) -1 else 0)
    }

    /**
     * Whether a side pixel is on the top edge of the side of a piece outlined in ink, where the
     * front's own top is inked: the outline carried back along the silhouette.
     */
    private fun silhouette(s: FaceSpot): Boolean =
        s.side && s.outlined && s.fromTop == 0 && s.topInked

    /** Upholstery: fabric sides, a lighter top. */
    private fun upholstery(s: FaceSpot): Int {
        if (s.inked) return FaceTex.KEEP
        if (s.slope) return round(s, s.body)
        if (s.top) return fabricTop(s, s.material)
        if (silhouette(s)) return FaceTex.INK
        return fabric(s, s.material, if (s.outer) 0 else -1)
    }

    /** Fabric: piping down the front edge, the hem underneath in shadow. */
    private fun fabric(s: FaceSpot, mat: Int, steps: Int): Int {
        var st = steps
        if (s.behind == 1) st += 1 // the piping
        if (s.fromBottom == 0 && s.fromTop > 2) st -= 1 // the hem
        return FaceTex.of(mat, st)
    }

    /** The top of a fabric surface: the front edge's piping, the back a shade darker. */
    private fun fabricTop(s: FaceSpot, mat: Int): Int =
        FaceTex.of(mat, if (s.behind == 1) 1 else if (s.band(4) == 3) -1 else 0)

    /** Carpet: a fleck of a shade either way, changing along the depth only in broad bands. */
    private fun carpet(s: FaceSpot, mat: Int, steps: Int = 0): Int {
        val n = s.noise(s.x * 3 + s.y, s.band(4), 3)
        return FaceTex.of(mat, steps + if (n < 50) -1 else if (n > 228) 1 else 0)
    }

    /** Wood in [tones]: a top with its grain, a side with its grain along it. */
    private fun wood(s: FaceSpot, tones: Tones): Int {
        if (s.inked) return FaceTex.KEEP
        if (s.slope) return round(s, s.body)
        if (s.top) return woodTop(s, tones)
        if (silhouette(s)) return FaceTex.INK
        return woodSide(s, tones, vertical = s.fromTop + s.fromBottom > 6)
    }

    /**
     * A wooden side in [tones]: the front edge (the board's edge banding) and the top edge lighter;
     * the grain in long lines along the depth, broken here and there, or up a tall board
     * ([vertical]), its lines a fifth of the depth apart; the back edge and the inside in shadow.
     */
    private fun woodSide(s: FaceSpot, tones: Tones, vertical: Boolean): Int {
        val side = tones.side(s)
        if (s.behind == 1 || (s.fromTop == 0 && s.outer)) return FaceTex.exact(s.colour(side.edge))
        val grain =
            !s.outer ||
                s.band(4) == 3 ||
                if (vertical) s.noise(s.band(4), s.y / 9, s.x) < 64
                else s.noise(s.y, 11, s.x) < 80 && s.noise(s.y, s.band(4)) > 70
        return FaceTex.exact(s.colour(if (grain) side.grain else side.panel))
    }

    /** A wooden top in [tones]: the grain running front to back in broken lines, the lip lit. */
    private fun woodTop(s: FaceSpot, tones: Tones): Int {
        if (s.behind == 1) return FaceTex.exact(s.colour(tones.top))
        val grain = s.band(4) == 3 || (s.noise(s.x, 5) < 80 && s.noise(s.x, s.band(4), 9) > 70)
        return FaceTex.exact(s.colour(if (grain) tones.topGrain else tones.top))
    }

    /** A painted panel: a light front edge, plain, a shade darker towards the back. */
    private fun panel(s: FaceSpot, mat: Int, gradient: Boolean = false): Int {
        if (s.slope) return round(s, mat)
        if (s.top) return FaceTex.of(mat, if (s.behind == 1) 1 else if (s.band(4) == 3) -1 else 0)
        if (silhouette(s)) return FaceTex.INK
        var st = if (s.outer) 0 else -1
        if (s.behind == 1) st += 1 else if (gradient && s.along > 0.6f) st -= 1
        return FaceTex.of(mat, st)
    }

    /**
     * Wicker: an over-and-under weave of two shades, two rows tall, a quarter of the depth long.
     */
    private fun weave(s: FaceSpot): Int {
        if (s.inked) return FaceTex.KEEP
        if (s.top) return FaceTex.of(s.material, if (s.behind == 1) 1 else 0)
        val over = ((s.y / 2) + s.band(4)) % 2 == 0
        val lit = if (s.slope) slopeLight(s) else 0
        val turned = if (s.along > 0.6f) -1 else 0
        return FaceTex.of(s.body, (if (over) 0 else -1) + lit + turned)
    }

    /** Leaves: a scatter of lighter and darker leaves, darker towards the back of the plant. */
    private fun foliage(s: FaceSpot): Int {
        val n = s.noise(s.x + 7 * s.y, s.band(4), 5)
        var st = if (n < 70) -1 else if (n > 210) 1 else 0
        if (s.along > 0.6f) st -= 1
        if (s.slope) st += slopeLight(s)
        return FaceTex.of(s.body, st)
    }

    /** A round terracotta pot: its bands carry round, darker as it turns; [soil] in it. */
    private fun pot(s: FaceSpot, soil: Int): Int {
        if (s.top) {
            if (s.behind <= 1 || s.before <= 1) return FaceTex.of(s.material, 0) // the rim
            val earth = s.colour(soil)
            return FaceTex.exact(earth)
        }
        return round(s, s.body)
    }

    /** Something round: its own colour, darker as it turns away from the light and the eye. */
    private fun round(s: FaceSpot, mat: Int): Int {
        if (s.inked) return FaceTex.KEEP
        if (s.top) return generic(s)
        if (!s.slope) {
            return FaceTex.of(mat, (if (s.behind == 1) 1 else 0) + if (s.along > 0.6f) -1 else 0)
        }
        return FaceTex.of(mat, slopeLight(s) + if (s.along > 0.5f) -1 else 0)
    }

    /**
     * A slope is unlit; its side towards the light (the left) keeps its colour, the right darkens.
     */
    private fun slopeLight(s: FaceSpot): Int = if (s.face == Face.RIGHT) -1 else 0

    /** The rows of column [x] among [rows] at its nearest front: the front edge of a board. */
    private fun edgeRows(model: VolumeModel, x: Int, rows: IntRange): IntRange {
        var nearest = Float.MAX_VALUE
        for (y in rows) {
            val k = y * model.width + x
            if (model.solid(k)) nearest = minOf(nearest, model.front[k])
        }
        var first = -1
        var last = -1
        for (y in rows) {
            val k = y * model.width + x
            if (model.solid(k) && model.front[k] <= nearest) {
                if (first < 0) first = y
                last = y
            }
        }
        return first..last
    }

    /** The three colours of a wooden side seen from one side: the board, its grain, its edge. */
    class Side(val panel: Int, val grain: Int, val edge: Int)

    /**
     * A wood's colours, given outright for each face (its ramp would step into the colours of other
     * materials): the top and its grain, and the left (lit) and right (shaded) sides.
     */
    class Tones(val top: Int, val topGrain: Int, val left: Side, val right: Side) {
        /** Every colour of the wood. */
        val all =
            intArrayOf(
                top,
                topGrain,
                left.panel,
                left.grain,
                left.edge,
                right.panel,
                right.grain,
                right.edge,
            )

        /** The side [s] is on. */
        fun side(s: FaceSpot): Side = if (s.face == Face.RIGHT) right else left
    }

    private fun argb(rgb: Int) = rgb or 0xFF000000.toInt()

    private fun tones(top: Int, topGrain: Int, left: IntArray, right: IntArray) =
        Tones(
            argb(top),
            argb(topGrain),
            Side(argb(left[0]), argb(left[1]), argb(left[2])),
            Side(argb(right[0]), argb(right[1]), argb(right[2])),
        )

    /** The bookcase's wood: panel, grain and edge of each side. */
    private val BOOKCASE =
        tones(
            0xA87446,
            0x8A5A34,
            intArrayOf(0x8A5A34, 0x684225, 0xA87446),
            intArrayOf(0x53341D, 0x432A18, 0x684225),
        )
    private val STUDY =
        tones(
            0xB37E4E,
            0x8E5E35,
            intArrayOf(0x885A33, 0x684225, 0xB37E4E),
            intArrayOf(0x684225, 0x5B3A20, 0x8E5E35),
        )
    private val SIDE_TABLE =
        tones(
            0x946238,
            0x885A33,
            intArrayOf(0x8E5E35, 0x684225, 0x946238),
            intArrayOf(0x684225, 0x5B3A20, 0x885A33),
        )
    private val BENCH =
        tones(
            0xC58E5A,
            0xB37E4E,
            intArrayOf(0xB37E4E, 0x8E5E35, 0xC58E5A),
            intArrayOf(0x8E5E35, 0x684225, 0x885A33),
        )
    private val DESK = BENCH
    private val CHAIR =
        tones(
            0x8E5E35,
            0x885A33,
            intArrayOf(0x8E5E35, 0x885A33, 0x8E5E35),
            intArrayOf(0x885A33, 0x885A33, 0x8E5E35),
        )
    private val WORKTOP =
        tones(
            0xDEAE76,
            0xC48E5A,
            intArrayOf(0xC48E5A, 0x684225, 0xDEAE76),
            intArrayOf(0xC48E5A, 0x684225, 0xC48E5A),
        )

    private const val SHADE_RIM = 0xFFCEB078.toInt()
    private val SHADE = intArrayOf(0xFFF6E2B0.toInt(), 0xFFDCBE82.toInt(), 0xFFCEB078.toInt())
    private const val SHADE_GLOW = 0xFFF6E2B0.toInt()
    private const val PAGES = 0xFFE8E0D0.toInt()

    private val SISAL = intArrayOf(0xFFF0CF94.toInt(), 0xFFD8B070.toInt(), 0xFFA98040.toInt())
    private val GREENS =
        intArrayOf(
            0xFF3F7544.toInt(),
            0xFF86BF86.toInt(),
            0xFF5E9A62.toInt(),
            0xFF2C5238.toInt(),
            0xFF5F9070.toInt(),
            0xFF3F6E4E.toInt(),
        )
    private val TERRACOTTA =
        intArrayOf(
            0xFFD7723F.toInt(),
            0xFFCE6D3C.toInt(),
            0xFFC4683A.toInt(),
            0xFF9C532E.toInt(),
            0xFF8F4325.toInt(),
            0xFFD89575.toInt(),
            0xFFC8683A.toInt(),
        )
    private val WICKER =
        intArrayOf(
            0xFFA98040.toInt(),
            0xFFF0CF94.toInt(),
            0xFFD8B070.toInt(),
            0xFFD8B08A.toInt(),
            0xFF8A6A34.toInt(),
            0xFFA9808A.toInt(),
        )
}
