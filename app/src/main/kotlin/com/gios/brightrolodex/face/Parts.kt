package com.gios.brightrolodex.face

/**
 * The parts library.
 *
 * A Mii, not a portrait. Drawing a recognisable face with a finger on a 3.9" screen is slow
 * enough that most people would leave every card blank, so the fast path is picking parts and
 * the pencil is for the bit that makes it *them*. Everything here is line art in one colour,
 * because the panel is monochrome and a filled shape at 24px in a list row is a black blob.
 *
 * Category order below is draw order, and it is why the categories are an enum rather than
 * strings: `Extra` last means glasses sit over eyes, `Beard` after `Mouth` means a beard frames
 * a mouth instead of being framed by it.
 *
 * Coordinates are face space — a 1000-unit square, see [FACE_SIZE] — with the head centred at
 * (500, 470). The useful landmarks, so a new part can be added without measuring: hairline
 * y≈165, brows y≈360, eyes y≈430, nose y≈520, mouth y≈645, chin y≈845, cheeks x≈300 and 700.
 */
enum class PartCategory(val label: String) {
    Head("HEAD"),
    Ears("EARS"),
    Hair("HAIR"),
    Brows("BROW"),
    Eyes("EYES"),
    Nose("NOSE"),
    Mouth("MOUTH"),
    Beard("BEARD"),
    Extra("MORE"),
}

/**
 * One stroke or filled shape within a part.
 *
 * [width] is in face units. 16 at a 160px render is a 2.5px line, which is about the thinnest
 * that survives the panel; 26 is the "thick" weight and 10 the "fine" one.
 */
class SubPath(
    val spec: String,
    val filled: Boolean = false,
    val width: Float = STROKE,
) {
    /**
     * Parsed once, lazily. Sixty-eight parts is a few hundred commands and parsing them all on
     * class load would be work done on the way to the launcher for a screen that draws six.
     */
    val cmds: List<PathCmd> by lazy(LazyThreadSafetyMode.PUBLICATION) { PathSpec.parse(spec) }

    companion object {
        const val STROKE = 16f
        const val THICK = 26f
        const val FINE = 10f
    }
}

class Part(
    val id: String,
    val category: PartCategory,
    val paths: List<SubPath>,
) {
    /**
     * Scale and rotation happen about this point.
     *
     * Derived from the part's own bounding box rather than declared per part: a pivot at the
     * category's nominal centre would make a mirrored ponytail or an off-centre scar swing
     * across the face when scaled, and nobody would guess that was why.
     */
    val pivotX: Float by lazy(LazyThreadSafetyMode.PUBLICATION) { pivot()[0] }
    val pivotY: Float by lazy(LazyThreadSafetyMode.PUBLICATION) { pivot()[1] }

    private fun pivot(): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        paths.forEach { sub ->
            val b = PathSpec.bounds(sub.cmds)
            if (b[0] < minX) minX = b[0]
            if (b[1] < minY) minY = b[1]
            if (b[2] > maxX) maxX = b[2]
            if (b[3] > maxY) maxY = b[3]
        }
        if (minX > maxX) return floatArrayOf(FACE_SIZE / 2f, FACE_SIZE / 2f)
        return floatArrayOf((minX + maxX) / 2f, (minY + maxY) / 2f)
    }
}

object Parts {

    const val DEFAULT_HEAD = "head_oval"

    private fun p(spec: String, filled: Boolean = false, width: Float = SubPath.STROKE) =
        SubPath(spec, filled, width)

    private fun part(id: String, category: PartCategory, vararg paths: SubPath) =
        Part(id, category, paths.toList())

    val all: List<Part> = listOf(

        /* ---------------- heads ---------------- */

        part("head_oval", PartCategory.Head, p("E 500 470 300 375")),
        part("head_round", PartCategory.Head, p("E 500 480 320 330")),
        part("head_long", PartCategory.Head, p("E 500 470 255 400")),
        part(
            "head_square", PartCategory.Head,
            p("M 245 265 Q 245 130 500 130 Q 755 130 755 265 L 755 610 Q 755 815 500 815 Q 245 815 245 610 Z"),
        ),
        part(
            "head_heart", PartCategory.Head,
            p("M 215 345 Q 215 130 500 130 Q 785 130 785 345 Q 785 620 500 835 Q 215 620 215 345 Z"),
        ),

        /* ---------------- ears ---------------- */

        part(
            "ears_small", PartCategory.Ears,
            p("M 210 425 Q 152 428 158 498 Q 164 558 214 548"),
            p("M 790 425 Q 848 428 842 498 Q 836 558 786 548"),
        ),
        part(
            "ears_big", PartCategory.Ears,
            p("M 208 390 Q 120 395 128 510 Q 136 605 218 585"),
            p("M 792 390 Q 880 395 872 510 Q 864 605 782 585"),
        ),
        part(
            "ears_pointed", PartCategory.Ears,
            p("M 212 430 L 140 380 L 165 520 Z"),
            p("M 788 430 L 860 380 L 835 520 Z"),
        ),

        /* ---------------- hair ---------------- */

        part("hair_buzz", PartCategory.Hair, p("M 232 350 Q 262 175 500 168 Q 738 175 768 350")),
        part(
            "hair_side_part", PartCategory.Hair,
            p("M 235 330 Q 272 165 520 160 Q 700 166 762 262"),
            p("M 402 168 Q 332 250 302 342"),
        ),
        part(
            "hair_curly", PartCategory.Hair,
            p("M 240 335 Q 248 252 300 258 Q 318 178 400 202 Q 452 142 522 192 Q 602 152 642 222 Q 730 224 762 335"),
        ),
        part("hair_afro", PartCategory.Hair, p("M 172 400 Q 152 100 500 90 Q 848 100 828 400")),
        part(
            "hair_bun", PartCategory.Hair,
            p("M 235 332 Q 265 172 500 166 Q 735 172 765 332"),
            p("E 500 92 78 68"),
        ),
        part(
            "hair_ponytail", PartCategory.Hair,
            p("M 235 332 Q 265 172 500 166 Q 735 172 765 332"),
            p("M 762 282 Q 862 332 842 522 Q 832 602 792 622"),
        ),
        part(
            "hair_long", PartCategory.Hair,
            p("M 232 340 Q 252 162 500 156 Q 748 162 768 340"),
            p("M 226 340 Q 190 560 212 782"),
            p("M 774 340 Q 810 560 788 782"),
        ),
        part(
            "hair_bob", PartCategory.Hair,
            p("M 226 332 Q 252 156 500 150 Q 748 156 774 332"),
            p("M 222 332 L 236 602"),
            p("M 778 332 L 764 602"),
        ),
        part(
            "hair_mohawk", PartCategory.Hair,
            p("M 500 162 L 500 58"),
            p("M 428 176 L 398 88"),
            p("M 572 176 L 602 88"),
            p("M 360 222 L 320 148"),
            p("M 640 222 L 680 148"),
        ),
        part(
            "hair_receding", PartCategory.Hair,
            p("M 250 332 Q 302 200 382 216"),
            p("M 750 332 Q 698 200 618 216"),
        ),
        part(
            "hair_spikes", PartCategory.Hair,
            p("M 236 330 L 288 214 L 322 300 L 386 178 L 420 288 L 500 158 L 580 288 L 614 178 L 678 300 L 712 214 L 764 330"),
        ),

        /* ---------------- brows ---------------- */

        part(
            "brows_flat", PartCategory.Brows,
            p("M 340 360 L 432 360"), p("M 568 360 L 660 360"),
        ),
        part(
            "brows_arch", PartCategory.Brows,
            p("M 336 366 Q 386 328 436 366"), p("M 564 366 Q 614 328 664 366"),
        ),
        part(
            "brows_thick", PartCategory.Brows,
            p("M 336 358 L 436 358", width = SubPath.THICK),
            p("M 564 358 L 664 358", width = SubPath.THICK),
        ),
        part(
            "brows_angry", PartCategory.Brows,
            p("M 336 338 L 436 372"), p("M 664 338 L 564 372"),
        ),
        part(
            "brows_sad", PartCategory.Brows,
            p("M 336 376 L 436 344"), p("M 664 376 L 564 344"),
        ),
        part(
            "brows_bushy", PartCategory.Brows,
            p("M 330 362 Q 360 330 392 358 Q 420 334 446 362", width = SubPath.THICK),
            p("M 670 362 Q 640 330 608 358 Q 580 334 554 362", width = SubPath.THICK),
        ),

        /* ---------------- eyes ---------------- */

        part(
            "eyes_dots", PartCategory.Eyes,
            p("E 410 430 22 22", filled = true), p("E 590 430 22 22", filled = true),
        ),
        part(
            "eyes_circles", PartCategory.Eyes,
            p("E 410 430 34 34"), p("E 590 430 34 34"),
        ),
        part(
            "eyes_lines", PartCategory.Eyes,
            p("M 370 430 L 452 430"), p("M 548 430 L 630 430"),
        ),
        part(
            "eyes_wide", PartCategory.Eyes,
            p("E 410 430 46 36"), p("E 410 430 15 15", filled = true),
            p("E 590 430 46 36"), p("E 590 430 15 15", filled = true),
        ),
        part(
            "eyes_big", PartCategory.Eyes,
            p("E 408 428 56 52"), p("E 414 434 22 22", filled = true),
            p("E 592 428 56 52"), p("E 586 434 22 22", filled = true),
        ),
        part(
            "eyes_sleepy", PartCategory.Eyes,
            p("M 368 422 Q 410 456 452 422"), p("M 548 422 Q 590 456 632 422"),
        ),
        part(
            "eyes_squint", PartCategory.Eyes,
            p("M 368 438 Q 410 414 452 438"), p("M 548 438 Q 590 414 632 438"),
        ),
        part(
            "eyes_wink", PartCategory.Eyes,
            p("E 410 430 34 34"), p("E 410 430 14 14", filled = true),
            p("M 548 424 Q 590 458 632 424"),
        ),
        part(
            "eyes_angry", PartCategory.Eyes,
            p("M 366 412 L 454 442"), p("E 412 442 16 16", filled = true),
            p("M 634 412 L 546 442"), p("E 588 442 16 16", filled = true),
        ),
        part(
            "eyes_side", PartCategory.Eyes,
            p("E 410 430 36 36"), p("E 432 430 15 15", filled = true),
            p("E 590 430 36 36"), p("E 612 430 15 15", filled = true),
        ),
        part(
            "eyes_closed", PartCategory.Eyes,
            p("M 368 430 Q 410 412 452 430"), p("M 548 430 Q 590 412 632 430"),
            p("M 372 448 L 356 462"), p("M 628 448 L 644 462"),
        ),

        /* ---------------- noses ---------------- */

        part("nose_dot", PartCategory.Nose, p("E 500 522 14 14", filled = true)),
        part("nose_line", PartCategory.Nose, p("M 500 464 L 500 536")),
        part("nose_hook", PartCategory.Nose, p("M 490 460 L 490 520 Q 490 546 522 540")),
        part("nose_wide", PartCategory.Nose, p("M 462 524 Q 500 552 538 524")),
        part("nose_button", PartCategory.Nose, p("E 500 524 30 22")),
        part("nose_pointed", PartCategory.Nose, p("M 500 452 L 464 536 L 536 536 Z")),
        part(
            "nose_nostrils", PartCategory.Nose,
            p("E 470 530 13 10", filled = true), p("E 530 530 13 10", filled = true),
        ),

        /* ---------------- mouths ---------------- */

        part("mouth_line", PartCategory.Mouth, p("M 420 646 L 580 646")),
        part("mouth_smile", PartCategory.Mouth, p("M 410 624 Q 500 702 590 624")),
        part("mouth_grin", PartCategory.Mouth, p("M 396 620 Q 500 730 604 620 Z")),
        part("mouth_frown", PartCategory.Mouth, p("M 410 682 Q 500 614 590 682")),
        part("mouth_open", PartCategory.Mouth, p("E 500 656 58 44")),
        part("mouth_smirk", PartCategory.Mouth, p("M 414 652 Q 506 690 586 628")),
        part(
            "mouth_teeth", PartCategory.Mouth,
            p("M 400 620 Q 500 722 600 620 Z"),
            p("M 452 636 L 452 690", width = SubPath.FINE),
            p("M 500 642 L 500 700", width = SubPath.FINE),
            p("M 548 636 L 548 690", width = SubPath.FINE),
        ),
        part("mouth_pucker", PartCategory.Mouth, p("E 500 650 30 30")),
        part("mouth_wide", PartCategory.Mouth, p("M 378 642 L 622 642", width = SubPath.THICK)),

        /* ---------------- facial hair ---------------- */

        part(
            "beard_mustache", PartCategory.Beard,
            p("M 414 606 Q 460 574 500 600 Q 540 574 586 606", width = SubPath.THICK),
        ),
        part(
            "beard_goatee", PartCategory.Beard,
            p("M 452 688 Q 500 764 548 688"),
            p("M 452 688 L 548 688", width = SubPath.FINE),
        ),
        part(
            "beard_full", PartCategory.Beard,
            p("M 226 470 Q 252 830 500 852 Q 748 830 774 470"),
            p("M 414 606 Q 460 574 500 600 Q 540 574 586 606"),
        ),
        part(
            "beard_chinstrap", PartCategory.Beard,
            p("M 240 468 Q 274 800 500 812 Q 726 800 760 468", width = SubPath.FINE),
        ),
        part(
            "beard_stubble", PartCategory.Beard,
            p("E 350 640 7 7", filled = true), p("E 400 690 7 7", filled = true),
            p("E 452 726 7 7", filled = true), p("E 500 744 7 7", filled = true),
            p("E 548 726 7 7", filled = true), p("E 600 690 7 7", filled = true),
            p("E 650 640 7 7", filled = true), p("E 372 700 7 7", filled = true),
            p("E 628 700 7 7", filled = true), p("E 500 690 7 7", filled = true),
        ),
        part("beard_soul_patch", PartCategory.Beard, p("E 500 700 20 15", filled = true)),
        part(
            "beard_muttonchops", PartCategory.Beard,
            p("M 234 452 Q 250 650 332 662", width = SubPath.THICK),
            p("M 766 452 Q 750 650 668 662", width = SubPath.THICK),
        ),

        /* ---------------- extras ---------------- */

        part(
            "extra_glasses_round", PartCategory.Extra,
            p("E 404 430 62 62"), p("E 596 430 62 62"),
            p("M 466 428 L 534 428", width = SubPath.FINE),
            p("M 342 420 L 248 396", width = SubPath.FINE),
            p("M 658 420 L 752 396", width = SubPath.FINE),
        ),
        part(
            "extra_glasses_square", PartCategory.Extra,
            p("M 336 386 L 470 386 L 470 474 L 336 474 Z"),
            p("M 530 386 L 664 386 L 664 474 L 530 474 Z"),
            p("M 470 424 L 530 424", width = SubPath.FINE),
            p("M 336 400 L 246 380", width = SubPath.FINE),
            p("M 664 400 L 754 380", width = SubPath.FINE),
        ),
        part(
            "extra_sunglasses", PartCategory.Extra,
            p("M 330 388 L 472 388 L 458 480 L 344 480 Z", filled = true),
            p("M 670 388 L 528 388 L 542 480 L 656 480 Z", filled = true),
            p("M 472 400 L 528 400"),
        ),
        part(
            "extra_cap", PartCategory.Extra,
            p("M 214 322 Q 246 142 500 142 Q 754 142 786 322 Z"),
            p("M 500 322 L 894 344 Q 902 292 776 296"),
        ),
        part(
            "extra_beanie", PartCategory.Extra,
            p("M 220 330 Q 252 128 500 124 Q 748 128 780 330 Z"),
            p("M 214 330 L 786 330", width = SubPath.THICK),
        ),
        part("extra_headband", PartCategory.Extra, p("M 218 302 L 782 302", width = SubPath.THICK)),
        part(
            "extra_earrings", PartCategory.Extra,
            p("E 202 546 18 18", filled = true), p("E 798 546 18 18", filled = true),
        ),
        part(
            "extra_freckles", PartCategory.Extra,
            p("E 320 512 8 8", filled = true), p("E 356 546 8 8", filled = true),
            p("E 300 560 8 8", filled = true), p("E 680 512 8 8", filled = true),
            p("E 644 546 8 8", filled = true), p("E 700 560 8 8", filled = true),
        ),
        part("extra_mole", PartCategory.Extra, p("E 604 560 13 13", filled = true)),
        part(
            "extra_scar", PartCategory.Extra,
            p("M 622 348 L 660 472", width = SubPath.FINE),
            p("M 600 380 L 646 396", width = SubPath.FINE),
            p("M 614 428 L 658 442", width = SubPath.FINE),
        ),
    )

    /**
     * Named `index` rather than `byId`. A property and a function may share a name in Kotlin,
     * but a lookup function whose body reads a map of the same name is a puzzle for the next
     * person and a resolution question for the compiler, for no benefit.
     */
    private val index: Map<String, Part> = all.associateBy { it.id }

    fun byId(id: String): Part? = index[id]

    fun inCategory(category: PartCategory): List<Part> = all.filter { it.category == category }
}
