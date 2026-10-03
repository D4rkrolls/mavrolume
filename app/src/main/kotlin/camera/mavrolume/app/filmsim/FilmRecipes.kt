package camera.mavrolume.app.filmsim

/** Independent, documented quality/tone system. Numbers are creative texture levels, not ISO. */
object FilmRecipes {
    val qualities = listOf("DIGI","100","200","400","800","1600","3200")
    val tones = linkedMapOf("C" to "Crush", "U" to "Ultra", "P" to "Pro", "D" to "Dynamic",
        "N" to "Neutral", "R" to "Refined", "S" to "Softer", "F" to "Faded", "E" to "Expired", "B" to "Bold")
    data class Texture(val grain: Float,val size: Float,val bloom: Float,val halation: Float,
        val softness: Float,val aberration: Float)
    private val texture = listOf(
        Texture(0f,0.8f,0f,0f,0f,0f),
        Texture(.045f,0.9f,0f,0f,.02f,0f),
        Texture(.075f,1.1f,.012f,.012f,.05f,0f),
        Texture(.12f,1.35f,.035f,.025f,.10f,.01f),
        Texture(.20f,1.8f,.09f,.07f,.18f,.035f),
        Texture(.32f,2.5f,.17f,.14f,.28f,.07f),
        Texture(.48f,3.4f,.28f,.24f,.42f,.12f),
    )
    fun apply(recipe: String): FilmSettings {
        val q = texture[qualities.indexOf(recipe.substringBefore('-')).coerceAtLeast(0)]
        val base = FilmSettings(grain=q.grain,grainSize=q.size,bloom=q.bloom,halation=q.halation,
            softness=q.softness,aberration=q.aberration)
        return when(recipe.substringAfter('-',"N")) {
            "C" -> base.copy(contrast=1.34f,dynamicRange=-.35f,saturation=1.07f,mids=-.08f)
            "U" -> base.copy(contrast=1.23f,dynamicRange=-.22f,saturation=1.11f)
            "P" -> base.copy(contrast=1.14f,dynamicRange=-.1f,saturation=1.06f)
            "D" -> base.copy(contrast=1.08f,dynamicRange=.14f,saturation=1.03f)
            "R" -> base.copy(contrast=.96f,saturation=.92f,dynamicRange=.1f)
            "S" -> base.copy(contrast=.88f,dynamicRange=.17f,saturation=.94f)
            "F" -> base.copy(contrast=.91f,fade=.24f,saturation=.88f)
            "E" -> base.copy(contrast=.88f,fade=.34f,saturation=.82f,temperature=.08f)
            "B" -> base.copy(contrast=1.17f,saturation=1.13f,dynamicRange=-.1f)
            else -> base
        }
    }
}
