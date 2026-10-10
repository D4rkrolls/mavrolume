package camera.mavrolume.app.filmsim

/** Immutable, shutter-snapshotted controls shared by live preview and JPEG export. */
data class FilmSettings(
    val strength: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val grain: Float = 0.06f,
    val grainSize: Float = 1.5f,
    val bloom: Float = 0f,
    val halation: Float = 0f,
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val dynamicRange: Float = 0f,
    val mids: Float = 0f,
    val fade: Float = 0f,
    val mute: Float = 0f,
    val softness: Float = 0f,
    val aberration: Float = 0f,
) {
    fun uniforms(sim: FilmSimulation): Map<String, Float> = linkedMapOf(
        "uLegacy" to 0f,
        "uStrength" to strength, "uSaturation" to saturation,
        "uTemperature" to temperature, "uTint" to tint,
        "uGrain" to grain, "uGrainSize" to grainSize,
        "uBloom" to bloom, "uHalation" to halation,
        "uExposure" to exposure, "uBrightness" to brightness,
        "uContrast" to contrast, "uDynamicRange" to dynamicRange,
        "uMids" to mids, "uFade" to fade, "uMute" to mute,
        "uSoftness" to softness, "uAberration" to aberration,
        "uProfileSat" to sim.saturation, "uProfileContrast" to sim.contrast,
        "uProfileWarmth" to sim.warmth, "uProfileLift" to sim.lift,
        "uProfileTint" to sim.tint,
        "uShadowR" to sim.shadowR, "uShadowG" to sim.shadowG, "uShadowB" to sim.shadowB,
        "uToe" to sim.toe, "uShoulder" to sim.shoulder,
        "uGreenSat" to sim.greenSat, "uBlueSat" to sim.blueSat,
        "uMonoR" to sim.monoR, "uMonoG" to sim.monoG,
        "uHighlightR" to sim.highlightR, "uHighlightG" to sim.highlightG, "uHighlightB" to sim.highlightB,
    )
    fun encode(): String = listOf(strength,saturation,temperature,tint,grain,grainSize,bloom,halation,
        exposure,brightness,contrast,dynamicRange,mids,fade,mute,softness,aberration).joinToString(",")
    companion object {
        fun forProfile(sim: FilmSimulation): FilmSettings {
            val base = FilmRecipes.apply(sim.recipes.substringBefore(','))
            return when (sim) {
                FilmSimulation.DAYBREAK -> base.copy(contrast = .93f, dynamicRange = .2f, grain = .16f)
                FilmSimulation.HONEY -> base.copy(contrast = 1.14f, saturation = 1.08f, temperature = .13f, grain = .22f)
                FilmSimulation.PRISM -> base.copy(contrast = 1.18f, saturation = 1.13f, grain = .21f)
                FilmSimulation.AFTERGLOW -> base.copy(contrast = 1.14f, temperature = .04f, grain = .26f)
                FilmSimulation.NIGHTGLASS -> base.copy(contrast = 1.18f, brightness = -.025f, grain = .32f)
                FilmSimulation.RAIN -> base.copy(contrast = .98f, saturation = .88f, dynamicRange = .12f, grain = .18f)
                FilmSimulation.PATINA -> base.copy(contrast = 1.08f, saturation = .92f, grain = .34f)
                FilmSimulation.SILVER -> base.copy(contrast = 1.08f, grain = .28f)
                FilmSimulation.GRAPHITE -> base.copy(contrast = 1.24f, brightness = -.025f, grain = .42f)
                FilmSimulation.COAST -> base.copy(contrast = 1.06f, saturation = 1.1f, grain = .17f)
                FilmSimulation.STUDY_SUN_SHADOW -> base.copy(contrast = 1.12f, saturation = .96f, temperature = .045f, grain = .2f, grainSize = 1.1f, dynamicRange = -.08f)
                FilmSimulation.STUDY_COLOR_SHADE -> base.copy(contrast = 1.02f, saturation = .88f, temperature = -.035f, grain = .23f, grainSize = 1.2f, fade = .08f)
                FilmSimulation.STUDY_DUSK_COLOR -> base.copy(contrast = 1.08f, saturation = .9f, temperature = .08f, grain = .28f, grainSize = 1.45f, fade = .1f)
                FilmSimulation.STUDY_DENSE_COLOR -> base.copy(contrast = 1.16f, saturation = 1.08f, grain = .28f, grainSize = 1.3f, dynamicRange = -.12f)
                FilmSimulation.STUDY_OPEN_LIGHT -> base.copy(contrast = .9f, saturation = .92f, temperature = .06f, grain = .18f, dynamicRange = .2f, fade = .06f)
                FilmSimulation.STUDY_HUMAN_SILVER -> base.copy(contrast = 1.02f, grain = .28f, grainSize = 1.4f, dynamicRange = .12f)
                FilmSimulation.STUDY_NEON_COLOR -> base.copy(contrast = 1.12f, saturation = 1.07f, grain = .29f, grainSize = 1.5f, dynamicRange = -.1f)
                FilmSimulation.STUDY_NIGHT_LAMPS -> base.copy(contrast = 1.09f, saturation = .86f, grain = .4f, grainSize = 1.9f, brightness = -.025f)
                FilmSimulation.STUDY_STREET_SILVER -> base.copy(contrast = 1.14f, grain = .34f, grainSize = 1.65f, dynamicRange = -.08f)
                FilmSimulation.STUDY_DOCUMENTARY -> base.copy(contrast = .96f, grain = .23f, grainSize = 1.2f, dynamicRange = .1f)
                FilmSimulation.STUDY_SOFT_SILVER -> base.copy(contrast = .88f, grain = .2f, grainSize = 1.1f, fade = .07f, dynamicRange = .2f)
                FilmSimulation.STUDY_SHADE_SILVER -> base.copy(contrast = .98f, grain = .27f, grainSize = 1.4f, brightness = -.015f)
                FilmSimulation.STUDY_DARK_FORM -> base.copy(contrast = 1.22f, grain = .36f, grainSize = 1.7f, dynamicRange = -.14f)
                FilmSimulation.STUDY_PAPER_SILVER -> base.copy(contrast = 1.04f, grain = .31f, grainSize = 1.55f, fade = .045f)
                FilmSimulation.STUDY_NIGHT_SILVER -> base.copy(contrast = 1.24f, grain = .44f, grainSize = 2f, brightness = -.03f)
                else -> base
            }
        }
        fun decode(text: String?): FilmSettings = runCatching {
            val v = requireNotNull(text).split(',').map { it.toFloat().also { n -> require(n.isFinite()) } }
            require(v.size == 15 || v.size == 17)
            FilmSettings(v[0].coerceIn(0f,1f),v[1].coerceIn(0f,2f),v[2].coerceIn(-1f,1f),
                v[3].coerceIn(-1f,1f),v[4].coerceIn(0f,1f),v[5].coerceIn(0.5f,4f),
                v[6].coerceIn(0f,1f),v[7].coerceIn(0f,1f),v[8].coerceIn(-2f,2f),
                v[9].coerceIn(-0.3f,0.3f),v[10].coerceIn(0.5f,1.5f),v[11].coerceIn(-1f,1f),
                v[12].coerceIn(-1f,1f),v[13].coerceIn(0f,1f),v[14].coerceIn(0f,1f),
                v.getOrElse(15) { 0f }.coerceIn(0f,1f),v.getOrElse(16) { 0f }.coerceIn(0f,1f))
        }.getOrDefault(FilmSettings())
    }
}
