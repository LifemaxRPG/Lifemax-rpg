package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage

/** A canvas that draws nothing and counts what it was asked for. */
internal class CountingCanvas : RoomCanvas {
    var calls = 0

    override fun clip(x: Int, y: Int, w: Int, h: Int) {
        calls++
    }

    override fun unclip() {
        calls++
    }

    override fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) {
        calls++
    }

    override fun image(
        image: RoomImage,
        sx: Int,
        sy: Int,
        sw: Int,
        sh: Int,
        dx: Int,
        dy: Int,
        dw: Int,
        dh: Int,
    ) {
        calls++
    }

    override fun quad(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
        argb: Int,
    ) {
        calls++
    }

    override fun multiply(rgb: Int, x: Int, y: Int, w: Int, h: Int) {
        calls++
    }

    override fun overlay(mask: ArgbImage, version: Int, x: Int, y: Int) {
        calls++
    }

    override fun sheet(
        image: ArgbImage,
        slot: Int,
        version: Int,
        sx: Int,
        sy: Int,
        sw: Int,
        sh: Int,
        dx: Int,
        dy: Int,
        alpha: Float,
    ) {
        calls++
    }
}
