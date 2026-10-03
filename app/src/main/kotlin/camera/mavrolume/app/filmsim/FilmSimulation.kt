package camera.mavrolume.app.filmsim

/** Independent interpretations. Original film recipes and monochrome studies; no vendor processing. */
enum class Category(val displayName: String) { RECIPES("Film recipes"), STUDIES("Photo studies") }
enum class FilmSimulation(
    val displayName: String, val shortCode: String, val description: String,
    val saturation: Float, val contrast: Float, val warmth: Float, val lift: Float,
    val tint: Float, val shadowR: Float, val shadowG: Float, val shadowB: Float,
    val highlightR: Float, val highlightG: Float, val highlightB: Float,
    val recipes: String,
    val toe: Float, val shoulder: Float, val greenSat: Float, val blueSat: Float,
    val monoR: Float, val monoG: Float,
) {
    DAYBREAK("Daybreak 100", "GR01", "Portraits · food · soft warm skin", 0.88f, 0.94f, 0.025f, 0.025f, 0.0f, 0.0f, 0.0f, 0.0f, 0.015f, 0.004f, -0.008f, "100-D,400-N", 0.12f, 0.22f, -0.08f, -0.06f, 0.29f, 0.59f),
    HONEY("Honey 100", "GR02", "Sunlit travel · golden yellows", 1.12f, 1.05f, 0.06f, 0.018f, 0.0f, 0.01f, 0.006f, -0.015f, 0.04f, 0.025f, -0.035f, "100-B,400-B", 0.1f, 0.28f, -0.16f, -0.1f, 0.29f, 0.59f),
    PRISM("Prism 100", "GR03", "Outdoor street · saturated violet color", 1.3f, 1.17f, 0.005f, 0.006f, 0.035f, 0.025f, -0.012f, 0.035f, 0.01f, 0.0f, 0.015f, "100-D,400-U", 0.03f, 0.24f, -0.08f, -0.04f, 0.29f, 0.59f),
    AFTERGLOW("Afterglow 200", "GR04", "Golden hour · teal shadows and orange light", 1.04f, 1.18f, 0.015f, 0.012f, 0.0f, -0.075f, 0.035f, 0.045f, 0.09f, 0.025f, -0.06f, "200-D", 0.06f, 0.24f, -0.12f, -0.03f, 0.29f, 0.59f),
    NIGHTGLASS("Nightglass 400", "GR05", "Night streets · cool neon contrast", 1.1f, 1.3f, -0.05f, 0.008f, 0.015f, -0.045f, 0.015f, 0.08f, 0.025f, -0.012f, 0.045f, "200-P,400-D", 0.1f, 0.34f, -0.08f, -0.1f, 0.29f, 0.59f),
    RAIN("Rain 100", "GR06", "Rain and snow · restrained blue-grey", 0.63f, 0.96f, -0.045f, 0.03f, 0.0f, -0.02f, 0.01f, 0.035f, -0.015f, 0.0f, 0.02f, "100-N", 0.23f, 0.22f, -0.12f, -0.12f, 0.29f, 0.59f),
    PATINA("Patina 400", "GR07", "Vintage scenes · green shadows and earth", 0.96f, 1.08f, 0.035f, 0.035f, -0.012f, -0.018f, 0.04f, -0.018f, 0.025f, 0.012f, -0.015f, "400-N,400-U", 0.17f, 0.2f, -0.22f, -0.1f, 0.29f, 0.59f),
    SILVER("Silver 200", "GR08", "Balanced black and white", 0.0f, 1.08f, 0.0f, 0.018f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, "200-N", 0.12f, 0.22f, 0f, 0f, 0.29f, 0.59f),
    VIOLET_HOUR("Violet Hour 200", "GR09", "Sunset color study", 1.16f, 1.09f, 0.024f, 0.016f, 0.045f, 0.02f, -0.01f, 0.025f, 0.065f, -0.005f, 0.025f, "200-D", 0.12f, 0.24f, -0.1f, -0.06f, 0.29f, 0.59f),
    LINEN("Linen 100", "GR10", "Warm open tones", 1.07f, 0.88f, 0.05f, 0.035f, 0.0f, 0.015f, 0.008f, -0.01f, 0.025f, 0.015f, -0.02f, "100-N", 0.2f, 0.27f, -0.14f, -0.08f, 0.29f, 0.59f),
    COAST("Coast 100", "GR11", "Bright holiday color", 1.26f, 1.07f, 0.012f, 0.014f, 0.0f, -0.025f, 0.012f, 0.045f, 0.045f, 0.012f, -0.035f, "100-D", 0.07f, 0.32f, -0.08f, -0.16f, 0.29f, 0.59f),
    GRAPHITE("Graphite 400", "GR12", "Hard monochrome shadows", 0.0f, 1.42f, 0.0f, 0.004f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, "400-U", 0.04f, 0.19f, 0f, 0f, 0.29f, 0.59f),
    MOSS("Moss 200", "GR13", "Quiet woodland palette", 0.71f, 0.99f, -0.018f, 0.028f, -0.014f, -0.022f, 0.026f, 0.025f, -0.008f, 0.01f, 0.0f, "200-N", 0.14f, 0.23f, -0.23f, -0.09f, 0.29f, 0.59f),
    HORIZON("Horizon 100", "GR14", "Clear landscape color", 1.23f, 1.11f, -0.01f, 0.005f, -0.012f, -0.012f, 0.025f, 0.035f, 0.0f, 0.025f, 0.012f, "100-D", 0.14f, 0.25f, -0.09f, -0.12f, 0.29f, 0.59f),
    CROSSLIGHT("Crosslight 400", "GR15", "Experimental cross-processed color", 1.28f, 1.26f, 0.028f, 0.022f, -0.025f, -0.045f, 0.065f, 0.01f, 0.065f, 0.015f, -0.05f, "400-U", 0.11f, 0.23f, -0.18f, -0.1f, 0.29f, 0.59f),
    CONCRETE("Concrete 200", "GR16", "Urban concrete and color", 0.82f, 1.2f, -0.018f, 0.012f, 0.012f, -0.025f, 0.015f, 0.025f, 0.025f, 0.005f, -0.01f, "200-P", 0.12f, 0.24f, -0.11f, -0.07f, 0.29f, 0.59f),
    EVERYDAY("Everyday 100", "GR17", "Understated everyday color", 0.98f, 1.015f, 0.003f, 0.004f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, "100-N", 0.1f, 0.2f, -0.07f, -0.05f, 0.29f, 0.59f),
    BLUE_ROOM("Blue Room 400", "GR18", "Cool interior color", 0.9f, 1.13f, -0.065f, 0.02f, 0.008f, -0.025f, 0.015f, 0.065f, 0.015f, 0.008f, -0.01f, "400-N", 0.25f, 0.24f, -0.18f, -0.12f, 0.29f, 0.59f),
    SOFTLIGHT("Softlight 100", "GR19", "Soft daylight story", 0.84f, 0.97f, 0.018f, 0.042f, 0.006f, 0.008f, 0.016f, 0.005f, 0.022f, 0.016f, 0.008f, "100-P", 0.22f, 0.29f, -0.15f, -0.09f, 0.29f, 0.59f),
    STUDY_SUN_SHADOW("Sun & Shadow 100", "PH01", "separated warm colors and deep shade", 1.09f, 1.23f, 0.007f, 0.002f, 0f, -0.0056f, 0.00096f, 0.0064f, 0.0096f, 0.00216f, -0.0084f, "100-N", 0.03f, 0.25f, -0.04f, -0.03f, 0.29f, 0.59f),
    STUDY_COLOR_SHADE("Color Shade 100", "PH02", "colored midtones, quiet dark edges", 1.03f, 1.18f, -0.003f, 0.003f, 0f, -0.0077f, 0.00132f, 0.0088f, 0.004f, 0.0009f, -0.0035f, "100-N", 0.05f, 0.28f, -0.05f, -0.02f, 0.29f, 0.59f),
    STUDY_DUSK_COLOR("Dusk Color 100", "PH03", "warm lit surfaces, cool falling light", 1.06f, 1.2f, 0.004f, 0.004f, 0f, -0.0084f, 0.00144f, 0.0096f, 0.0112f, 0.00252f, -0.0098f, "100-N", 0.06f, 0.3f, -0.06f, -0.03f, 0.29f, 0.59f),
    STUDY_DENSE_COLOR("Dense Color 100", "PH04", "dense color and decisive blacks", 1.08f, 1.25f, 0.002f, 0.001f, 0f, -0.0042f, 0.00072f, 0.0048f, 0.0056f, 0.00126f, -0.0049f, "100-N", 0.025f, 0.23f, -0.1f, -0.05f, 0.29f, 0.59f),
    STUDY_OPEN_LIGHT("Open Light 100", "PH05", "clear warm planes and held whites", 1.04f, 1.17f, 0.008f, 0.002f, 0f, -0.0028f, 0.00048f, 0.0032f, 0.0088f, 0.00198f, -0.0077f, "100-N", 0.04f, 0.3f, -0.09f, -0.08f, 0.29f, 0.59f),
    STUDY_HUMAN_SILVER("Human Silver 200", "PH06", "human-scale silver documentary", 0f, 1.15f, 0f, 0.006f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "200-N", 0.13f, 0.25f, 0f, 0f, 0.29f, 0.59f),
    STUDY_NEON_COLOR("Neon Color 100", "PH07", "rich artificial light, muted greens", 0.96f, 1.16f, 0.003f, 0.005f, 0f, -0.0042f, 0.00072f, 0.0048f, 0.008f, 0.0018f, -0.007f, "100-N", 0.1f, 0.3f, -0.2f, -0.08f, 0.25f, 0.65f),
    STUDY_NIGHT_LAMPS("Night Lamps 100", "PH08", "dark available light, protected lamps", 0.89f, 1.2f, -0.004f, 0.005f, 0f, -0.007f, 0.0012f, 0.008f, 0.008f, 0.0018f, -0.007f, "100-N", 0.12f, 0.38f, -0.18f, -0.1f, 0.25f, 0.65f),
    STUDY_STREET_SILVER("Street Silver 200", "PH09", "detailed, weighty monochrome", 0f, 1.2f, 0f, 0.005f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "200-N", 0.1f, 0.26f, 0f, 0f, 0.25f, 0.65f),
    STUDY_DOCUMENTARY("Documentary 200", "PH10", "balanced documentary silver", 0f, 1.09f, 0f, 0.004f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "200-N", 0.13f, 0.2f, 0f, 0f, 0.25f, 0.65f),
    STUDY_SOFT_SILVER("Soft Silver 200", "PH11", "open grey daylight", 0f, 1.035f, 0f, 0.007f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "200-N", 0.22f, 0.25f, 0f, 0f, 0.25f, 0.65f),
    STUDY_SHADE_SILVER("Shade Silver 200", "PH12", "separated dark midtones", 0f, 1.14f, 0f, 0.003f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "200-N", 0.18f, 0.22f, 0f, 0f, 0.25f, 0.65f),
    STUDY_DARK_FORM("Dark Form 200", "PH13", "graphic blacks and sculpted highlights", 0f, 1.38f, 0f, 0.001f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "200-N", 0.02f, 0.18f, 0f, 0f, 0.36f, 0.54f),
    STUDY_PAPER_SILVER("Paper Silver 200", "PH14", "dense silver with gentler white paper", 0f, 1.28f, 0f, 0.003f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "200-N", 0.05f, 0.32f, 0f, 0f, 0.36f, 0.54f),
    STUDY_NIGHT_SILVER("Night Silver 200", "PH15", "deep dark planes and luminous edges", 0f, 1.44f, 0f, 0.001f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, "200-N", 0.015f, 0.24f, 0f, 0f, 0.36f, 0.54f);
    val category get() = if(name.startsWith("STUDY_")) Category.STUDIES else Category.RECIPES
    val defaultGrainIntensity get() = 0.04f
    val assetPath get() = "generated/identity"
    companion object {
        fun fromSaved(name: String): FilmSimulation = entries.find { it.name == name } ?: DAYBREAK
    }
}
