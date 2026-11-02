package com.lifemax.rpg.feature.pet.art

/**
 * The thin loader: reads the shipped art from the app's Java resources
 * (`native/src/main/resources/pet/`, written by `tools/gen_pet_native_assets.js`). Resources are
 * found through the class loader, so the same call works in the app and in JVM unit tests. All
 * Android-specific or file-system access for the cat's art belongs here and nowhere else.
 */
object PetArtResources {
    private const val ROOT = "/pet/"

    /**
     * Loads the art; [extra] answers for coats the app does not ship. Throws if a shipped file is
     * missing.
     */
    fun load(extra: CoatSource? = null): PetArt {
        val palettes = CatPalette.parseAll(text("palettes.json"))
        return PetArt(
            anims = AnimTable.parse(text("anims.json")),
            growth = Growth.parse(text("growth.json")),
            room =
                RoomAtlas.parse(
                    bytes("room/items.png") ?: error("missing room/items.png"),
                    text("room.json"),
                ),
            shipped = palettes,
            shippedSheet = { coat, kind -> bytes("sheets/${fileKind(kind)}-$coat.png") },
            extra = extra,
        )
    }

    /** The file-name prefix of a kind: `adult`, `depth`, `stage3`, `dstage3`. */
    private fun fileKind(kind: SheetKind): String =
        when (kind) {
            SheetKind.Adult -> "adult"
            SheetKind.Depth -> "depth"
            is SheetKind.Stage -> "stage${kind.index}"
            is SheetKind.DepthStage -> "dstage${kind.index}"
        }

    private fun bytes(name: String): ByteArray? =
        PetArtResources::class.java.getResourceAsStream(ROOT + name)?.use { it.readBytes() }

    private fun text(name: String): String =
        String(bytes(name) ?: error("missing $name"), Charsets.UTF_8)
}
