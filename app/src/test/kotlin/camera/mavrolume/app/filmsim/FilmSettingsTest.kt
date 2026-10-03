package camera.mavrolume.app.filmsim

import org.junit.Assert.*
import org.junit.Test

class FilmSettingsTest {
    @Test fun savedProfileNamesResolveAndUnknownNamesFallBack() {
        FilmSimulation.entries.forEach { profile ->
            assertEquals(profile, FilmSimulation.fromSaved(profile.name))
        }
        assertEquals(FilmSimulation.DAYBREAK,FilmSimulation.fromSaved("unknown"))
    }
    @Test fun everyRecipeSurvivesPersistenceWithoutClamping() {
        val recipes = FilmSimulation.entries.flatMap { it.recipes.split(',') } +
            FilmRecipes.qualities.flatMap { q -> FilmRecipes.tones.keys.map { t -> "$q-$t" } }
        recipes.forEach { name ->
            val settings = FilmRecipes.apply(name)
            assertEquals(name,settings,FilmSettings.decode(settings.encode()))
        }
    }

    @Test fun qualityControlsActuallyChangeTextureAndLensEffects() {
        val digi = FilmRecipes.apply("DIGI-N")
        val everyday = FilmRecipes.apply("400-N")
        val coarse = FilmRecipes.apply("1600-N")
        assertEquals(0f,digi.grain)
        assertEquals(0f,digi.bloom)
        assertTrue(coarse.grain > everyday.grain)
        assertTrue(coarse.grainSize > everyday.grainSize)
        assertTrue(coarse.bloom > everyday.bloom)
        assertTrue(coarse.halation > everyday.halation)
        assertTrue(coarse.softness > everyday.softness)
        assertTrue(coarse.aberration > everyday.aberration)
    }
    @Test fun olderFifteenValueSettingsStillDecode() {
        val old = List(15) { "0.5" }.joinToString(",")
        val decoded = FilmSettings.decode(old)
        assertEquals(0f,decoded.softness)
        assertEquals(0f,decoded.aberration)
        assertEquals(0.5f,decoded.grain)
    }
    @Test fun settingsSurvivePersistence() {
        val settings = FilmSettings(strength = 0.4f, temperature = -0.7f, grainSize = 3f, exposure = 1.2f, mute = 0.9f)
        assertEquals(settings,FilmSettings.decode(settings.encode()))
    }
    @Test fun corruptPreferencesCannotPoisonGpuUniforms() {
        for (bad in listOf(null,"","NaN,0","1,2,3",List(15) { "Infinity" }.joinToString(","))) {
            assertEquals(FilmSettings(),FilmSettings.decode(bad))
        }
        val clamped = FilmSettings.decode(List(15) { "999" }.joinToString(","))
        assertEquals(1f,clamped.strength)
        assertEquals(4f,clamped.grainSize)
        assertEquals(2f,clamped.exposure)
    }
    @Test fun everyControlIsDeclaredInTheSharedShader() {
        FilmSimulation.entries.forEach { profile ->
            FilmSettings().uniforms(profile).forEach { (name,value) ->
                assertTrue("Missing $name",MavrolumeShader.body.contains(name))
                assertTrue(value.isFinite())
            }
        }
    }
    @Test fun profileRecipesAreDistinct() {
        assertEquals(FilmSimulation.entries.size,FilmSimulation.entries.map { FilmSettings().uniforms(it) }.distinct().size)
        assertEquals(0f,FilmSimulation.SILVER.saturation)
    }
}
