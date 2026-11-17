package com.lifemax.rpg.feature.habits.domain

/**
 * The crew's appearances, GU1–GU5: five friends who walk the street together now and then
 * ([StreetCrew]). They are never in random traffic; their looks are indexed after the ordinary ones
 * ([StreetLooks.of]).
 */
object StreetCameos {
    const val STRIPE_DARK = 0x34393D
    const val STRIPE_LIGHT = 0xE1DCD3
    const val PLAID_LIGHT = 0xB6CCDD
    const val PLAID_DARK = 0x2A4D72
    // 90% of GU2's earlier wrist-space palm/finger dimensions. Match the actual mesh
    // size across the five different arm builds, rather than reusing the same multiplier.
    const val GROUP_HAND_BUILD = 1.25874f

    /**
     * Relaxed middle-aged build, loose red striped tee, dark-green long shorts, plain blue shoes.
     */
    val GU1 =
        StreetLook(
            height = 1.03f,
            build = 1.08f,
            head = 1.01f,
            coat = Coat.NONE,
            hair = StreetHair.CLOSE_CROP,
            bag = Bag.NONE,
            skirt = false,
            wear =
                Wear(
                    skin = 0xD3A17F,
                    hair = 0x302822,
                    hat = 0x302822,
                    coat = 0xD45A59,
                    top = 0xD45A59,
                    legs = 0x154634,
                    skirt = 0,
                    shoe = 0x2E4D96,
                    sole = 0xD7D7D0,
                    bag = 0,
                ),
            shoes = Shoe.TRAINER,
            shorts = true,
            tee = Tee.LOOSE_STRIPED,
            face =
                StreetFaceProfile(
                    nose = .17f,
                    tip = -.015f,
                    lips = .025f,
                    chin = -.01f,
                    brow = .025f,
                    skullWidth = 1.07f,
                    skullHeight = 1.04f,
                    cheek = .07f,
                    jaw = .045f,
                ),
            plainTrainers = true,
            armScale = .80f,
            handScale = GROUP_HAND_BUILD / (1.08f * .80f),
            chestDepth = 1.08f,
            backDepth = .84f,
            neckScale = .80f,
            neckForward = .14f,
            headLag = .20f,
            walkStyle =
                Gait.Style(
                    stride = StreetGait.style(3).stride,
                    armNear = 16f,
                    armFar = 17f,
                    bob = .45f,
                    lean = 1.6f,
                    split = 0f,
                    roll = .55f,
                    hip = .65f,
                    spring = .70f,
                ),
        )
    /** Slimmer blond friend: forward cap, blue plaid, rolled sleeves and olive cargo shorts. */
    val GU2 =
        StreetLook(
            height = .95f,
            build = .90f,
            head = .97f,
            coat = Coat.NONE,
            hair = StreetHair.LONG_CAP,
            bag = Bag.NONE,
            skirt = false,
            wear =
                Wear(
                    skin = 0xD7B28B,
                    hair = 0xB99961,
                    hat = 0x273445,
                    coat = 0x527BA4,
                    top = 0x527BA4,
                    legs = 0x687354,
                    skirt = 0,
                    shoe = 0x756E63,
                    sole = 0x3C3933,
                    bag = 0,
                ),
            shoes = Shoe.TRAINER,
            shorts = true,
            tee = Tee.PLAID_SHIRT,
            plainTrainers = true,
            face =
                StreetFaceProfile(
                    nose = .23f,
                    tip = .025f,
                    lips = .01f,
                    chin = .015f,
                    brow = .025f,
                    skullWidth = 1.01f,
                    skullHeight = 1.10f,
                    cheek = .015f,
                    jaw = .015f,
                    moustache = true,
                    goatee = true,
                ),
            armScale = .84f,
            chestDepth = 1.05f,
            backDepth = .87f,
            neckScale = .76f,
            neckForward = .12f,
            headLag = .20f,
            walkStyle =
                Gait.Style(
                    stride = StreetGait.style(3).stride,
                    armNear = 17f,
                    armFar = 16f,
                    bob = .43f,
                    lean = 1.5f,
                    split = 0f,
                    roll = .6f,
                    hip = .65f,
                    spring = .70f,
                ),
            cargoShorts = true,
            shortsLength = .90f,
            sockHeight = 1.30f,
            handScale = GROUP_HAND_BUILD / (.90f * .84f),
        )
    /** Lean work-shirt friend: green over a red tee, beige chinos and brown leather shoes. */
    val GU3 =
        StreetLook(
            height = 1.04f,
            build = .93f,
            head = .99f,
            coat = Coat.NONE,
            hair = StreetHair.TIGHT_CROP,
            bag = Bag.NONE,
            skirt = false,
            wear =
                Wear(
                    skin = 0x82573F,
                    hair = 0x211C19,
                    hat = 0x211C19,
                    coat = 0x668774,
                    top = 0x668774,
                    legs = 0xC8B393,
                    skirt = 0,
                    shoe = 0x594031,
                    sole = 0x2E241E,
                    bag = 0,
                ),
            shoes = Shoe.LEATHER,
            tee = Tee.WORK_SHIRT,
            underShirt = 0xB53F3B,
            face =
                StreetFaceProfile(
                    nose = .19f,
                    tip = -.025f,
                    lips = .045f,
                    chin = .035f,
                    brow = .065f,
                    skullWidth = 1.03f,
                    skullHeight = 1.08f,
                    cheek = .025f,
                    jaw = .025f,
                    moustache = true,
                    goatee = true,
                ),
            armScale = .86f,
            chestDepth = 1.07f,
            backDepth = .85f,
            neckScale = .78f,
            neckForward = .12f,
            headLag = .20f,
            handScale = GROUP_HAND_BUILD / (.93f * .86f),
            walkStyle =
                Gait.Style(
                    stride = StreetGait.style(3).stride,
                    armNear = 18f,
                    armFar = 17f,
                    bob = .43f,
                    lean = 1.8f,
                    split = 0f,
                    roll = .60f,
                    hip = .65f,
                    spring = .70f,
                ),
        )
    /** Stockier sports-shirt friend with a full beard. */
    val GU4 =
        StreetLook(
            height = 1.01f,
            build = 1.45f,
            head = 1.10f,
            coat = Coat.NONE,
            hair = StreetHair.SHORT_RECEDING,
            bag = Bag.NONE,
            skirt = false,
            wear =
                Wear(
                    skin = 0xD6AC88,
                    hair = 0x4A3024,
                    hat = 0x4A3024,
                    coat = 0x3B73B6,
                    top = 0x263B60,
                    legs = 0x22262A,
                    skirt = 0,
                    shoe = 0x4C4D44,
                    sole = 0x252824,
                    bag = 0,
                ),
            shoes = Shoe.TRAINER,
            plainTrainers = true,
            shorts = true,
            tee = Tee.SPORT_SHIRT,
            face =
                StreetFaceProfile(
                    nose = .16f,
                    tip = .01f,
                    lips = .025f,
                    chin = .02f,
                    brow = .03f,
                    skullWidth = 1.15f,
                    skullHeight = 1.01f,
                    cheek = .12f,
                    jaw = .13f,
                    moustache = true,
                    beard = true,
                ),
            armScale = .82f,
            chestDepth = 1.02f,
            backDepth = 1.05f,
            neckScale = .90f,
            neckForward = .16f,
            headLag = .18f,
            belly = 1.20f,
            sportPanel = 0xD7DBDA,
            handScale = GROUP_HAND_BUILD / (1.45f * .82f),
            walkStyle =
                Gait.Style(
                    stride = StreetGait.style(3).stride,
                    armNear = 14f,
                    armFar = 15f,
                    bob = .38f,
                    lean = 1.9f,
                    split = 0f,
                    roll = .48f,
                    hip = .55f,
                    spring = .67f,
                ),
            shortsLength = .95f,
            sockHeight = .50f,
        )
    /** Shorter green-hoodie friend with the tall swept-up hairstyle from the supplied reference. */
    val GU5 =
        StreetLook(
            height = .96f,
            build = .96f,
            head = .99f,
            coat = Coat.NONE,
            hair = StreetHair.HIGH_QUIFF,
            bag = Bag.CANVAS,
            skirt = false,
            wear =
                Wear(
                    skin = 0xCAA080,
                    hair = 0x312B25,
                    hat = 0x312B25,
                    coat = 0x226953,
                    top = 0x226953,
                    legs = 0x9B967F,
                    skirt = 0,
                    shoe = 0x504032,
                    sole = 0x382E24,
                    bag = 0x625F4E,
                ),
            shoes = Shoe.SANDAL,
            shorts = true,
            tee = Tee.STRIPED_HOODIE,
            face =
                StreetFaceProfile(
                    nose = .21f,
                    tip = .015f,
                    lips = .015f,
                    chin = .035f,
                    brow = .065f,
                    skullWidth = 1.01f,
                    skullHeight = 1.08f,
                    cheek = .035f,
                    jaw = .025f,
                    goatee = true,
                ),
            armScale = .84f,
            chestDepth = 1.06f,
            backDepth = .88f,
            neckScale = .78f,
            neckForward = .13f,
            headLag = .18f,
            cargoShorts = true,
            shortsLength = .87f,
            handScale = GROUP_HAND_BUILD / (.96f * .84f),
            walkStyle =
                Gait.Style(
                    stride = StreetGait.style(3).stride,
                    armNear = 16f,
                    armFar = 17f,
                    bob = .40f,
                    lean = 1.7f,
                    split = 0f,
                    roll = .55f,
                    hip = .60f,
                    spring = .68f,
                ),
        )

    /** The five, in order: look index [StreetLooks.CREW_AT] + their place here. */
    val CREW = arrayOf(GU1, GU2, GU3, GU4, GU5)
}
