package com.lifemax.rpg.tools

import com.lifemax.rpg.feature.habits.domain.*
import com.lifemax.rpg.feature.habits.presentation.scene.Sprite
import com.lifemax.rpg.feature.habits.presentation.scene.StreetRaster
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode

/** JVM-only designer preview: real StreetRig geometry and real production StreetRaster pixels. */
object StreetCameoPreview {
    private fun image(s: Sprite) =
        BufferedImage(s.w, s.h, BufferedImage.TYPE_INT_ARGB).also {
            it.setRGB(0, 0, s.w, s.h, s.pixels, 0, s.w)
        }

    private fun sprite(look: StreetLook, frame: Int): Sprite =
        StreetRaster.bake(StreetRig.frame(look, frame).shapes, 6.4f * 1.14f)

    private fun Graphics2D.quality() {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC,
        )
        setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
        )
    }

    private fun Graphics2D.at(
        s: Sprite,
        footX: Float,
        footY: Float,
        scale: Float = 1f,
        flip: Boolean = false,
    ) {
        val old = transform
        translate(footX.toDouble(), footY.toDouble())
        scale(if (flip) -scale.toDouble() else scale.toDouble(), scale.toDouble())
        translate(-s.ox.toDouble(), -s.oy.toDouble())
        drawImage(image(s), 0, 0, null)
        transform = old
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val gu2 = args.getOrNull(1) == "GU2"
        val gu3 = args.getOrNull(1) == "GU3"
        val gu4 = args.getOrNull(1) == "GU4"
        val gu5 = args.getOrNull(1) == "GU5"
        val look =
            when {
                gu5 -> StreetCameos.GU5
                gu4 -> StreetCameos.GU4
                gu3 -> StreetCameos.GU3
                gu2 -> StreetCameos.GU2
                else -> StreetCameos.GU1
            }
        val dir = File(args.firstOrNull() ?: "/tmp/gu-pedestrian-preview").also { it.mkdirs() }
        val frames =
            Array(StreetGait.FRAMES) { f ->
                sprite(look, f).also {
                    if (f % 8 == 0) println("Rendered walk frame $f/${StreetGait.FRAMES}")
                }
            }
        val stand =
            StreetRaster.bake(
                StreetRig.frame(look, StreetRig.STAND_AT, glance = .8f).shapes,
                6.4f * 1.14f,
            )
        val atlas = BufferedImage(2048, 2048, BufferedImage.TYPE_INT_ARGB)
        atlas.createGraphics().apply {
            for (f in frames.indices) at(frames[f], (f % 8) * 256f + 128f, (f / 8) * 256f + 238f)
            dispose()
        }
        ImageIO.write(atlas, "png", File(dir, "walk-atlas.png"))
        val sheet = BufferedImage(1320, 880, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.quality()
        g.paint = GradientPaint(0f, 0f, Color(0x111A24), 1320f, 880f, Color(0x080D15))
        g.fillRect(0, 0, 1320, 880)
        g.color = Color(0x94B1A9)
        g.font = Font("SansSerif", Font.BOLD, 15)
        g.drawString("HABITS  /  STREET CAST  /  LOCAL CHARACTER STUDY", 54, 53)
        g.color = Color(0xF0EEE7)
        g.font = Font("SansSerif", Font.BOLD, 35)
        g.drawString(
            when {
                gu5 -> "GU5 · High hair"
                gu4 -> "GU4 · Sports shirt"
                gu3 -> "GU3 · Work shirt"
                gu2 -> "GU2 · Plaid shirt"
                else -> "GU1 · Striped shirt"
            },
            54,
            104,
        )
        g.color = Color(0x98A9B4)
        g.font = Font("SansSerif", Font.PLAIN, 17)
        g.drawString(
            if (gu5) "Tall swept-up hair • striped green hoodie • canvas satchel • sandals"
            else if (gu4) "Fuller silhouette • blue sports shirt • shared hands"
            else if (gu3) "Chest pocket corrected • red undershirt • clean green work shirt"
            else if (gu2) "Forward-facing cap • matching refined hands • shorter frame"
            else "Matching refined hands • balanced silhouette • calmer walk",
            54,
            139,
        )
        val positions = arrayOf("RELAXED STANCE", "WALKING", "OPPOSITE DIRECTION")
        for (i in 0..2) {
            val x = 42 + i * 414
            g.color = Color(0x17222E)
            g.fillRoundRect(x, 182, 396, 550, 24, 24)
            g.color = Color(0x22323D)
            g.fillRoundRect(x + 22, 646, 352, 3, 3, 3)
            g.color = Color(0x090E14)
            g.fillOval(x + 124, 630, 160, 18)
            val s = if (i == 0) stand else frames[if (i == 1) 9 else 41]
            g.at(s, x + 195f, 641f, 2.18f, i == 2)
            g.color = Color(0xB6C6CD)
            g.font = Font("SansSerif", Font.BOLD, 13)
            g.drawString(positions[i], x + 26, 216)
        }
        g.color = Color(look.wear.top)
        g.fillRoundRect(54, 771, 16, 16, 5, 5)
        g.color = Color(0xC7D2D8)
        g.font = Font("SansSerif", Font.PLAIN, 16)
        g.drawString(
            when {
                gu5 -> "Green striped hoodie"
                gu4 -> "Blue two-tone shirt"
                gu3 -> "Green shirt over red tee"
                gu2 -> "Blue plaid shirt"
                else -> "Loose striped tee"
            },
            81,
            785,
        )
        g.color = Color(look.wear.legs)
        g.fillRoundRect(310, 771, 16, 16, 5, 5)
        g.color = Color(0xC7D2D8)
        g.drawString(
            when {
                gu5 -> "Khaki cargo shorts"
                gu4 -> "Black sports shorts"
                gu3 -> "Beige chinos"
                gu2 -> "Olive cargo shorts"
                else -> "Long green shorts"
            },
            337,
            785,
        )
        g.color = Color(look.wear.shoe)
        g.fillRoundRect(598, 771, 16, 16, 5, 5)
        g.color = Color(0xC7D2D8)
        g.drawString(
            when {
                gu5 -> "Brown sandals"
                gu4 -> "Dark gray shoes"
                gu3 -> "Brown leather shoes"
                gu2 -> "Gray lace-up shoes"
                else -> "Brandless blue shoes"
            },
            625,
            785,
        )
        g.color = Color(0x7F929E)
        g.font = Font("SansSerif", Font.PLAIN, 14)
        g.drawString(
            "Model prototype only • not selected in ordinary street traffic • nothing pushed or released",
            54,
            835,
        )
        g.dispose()
        ImageIO.write(sheet, "png", File(dir, "character-study.png"))

        // Export every native pose at subpixel alignment for a video without GIF timing/palette
        // limits.
        val sequence = File(dir, "frames").also { it.mkdirs() }
        for (f in frames.indices) {
            val canvas = BufferedImage(460, 390, BufferedImage.TYPE_INT_RGB)
            canvas.createGraphics().apply {
                quality()
                color = Color(0x101A26)
                fillRect(0, 0, 460, 390)
                color = Color(0x091018)
                fillOval(170, 332, 125, 14)
                at(frames[f], 225f, 340f, 1.6f)
                dispose()
            }
            ImageIO.write(canvas, "png", File(sequence, "walk-%03d.png".format(f)))
        }

        // An opaque-background GIF avoids disposal artifacts on mobile image viewers.
        val writer = ImageIO.getImageWritersBySuffix("gif").next()
        ImageIO.createImageOutputStream(File(dir, "walk.gif")).use { output ->
            writer.output = output
            writer.prepareWriteSequence(null)
            for (f in frames.indices step 2) {
                val canvas = BufferedImage(460, 390, BufferedImage.TYPE_INT_RGB)
                canvas.createGraphics().apply {
                    quality()
                    color = Color(0x101A26)
                    fillRect(0, 0, 460, 390)
                    color = Color(0x091018)
                    fillOval(170, 332, 125, 14)
                    at(frames[f], 225f, 340f, 1.6f)
                    dispose()
                }
                val type = javax.imageio.ImageTypeSpecifier.createFromRenderedImage(canvas)
                val metadata = writer.getDefaultImageMetadata(type, writer.defaultWriteParam)
                val root = metadata.getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
                val control =
                    (0 until root.length)
                        .map { root.item(it) }
                        .first { it.nodeName == "GraphicControlExtension" } as IIOMetadataNode
                control.setAttribute("disposalMethod", "none")
                control.setAttribute("userInputFlag", "FALSE")
                control.setAttribute("transparentColorFlag", "FALSE")
                control.setAttribute("delayTime", "4")
                control.setAttribute("transparentColorIndex", "0")
                if (f == 0) {
                    val extensions = IIOMetadataNode("ApplicationExtensions")
                    val loop = IIOMetadataNode("ApplicationExtension")
                    loop.setAttribute("applicationID", "NETSCAPE")
                    loop.setAttribute("authenticationCode", "2.0")
                    loop.userObject = byteArrayOf(1, 0, 0)
                    extensions.appendChild(loop)
                    root.appendChild(extensions)
                }
                metadata.setFromTree("javax_imageio_gif_image_1.0", root)
                writer.writeToSequence(
                    javax.imageio.IIOImage(canvas, null, metadata),
                    writer.defaultWriteParam,
                )
            }
            writer.endWriteSequence()
        }
        writer.dispose()
        println("Preview written to ${dir.absolutePath}")
    }
}
