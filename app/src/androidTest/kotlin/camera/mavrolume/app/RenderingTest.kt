package camera.mavrolume.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import camera.mavrolume.app.filmsim.*
import camera.mavrolume.app.crop.CropEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderingTest {
    @Test fun neutralGpuExportPreservesColorAndOrientation() = runBlocking {
        val source = Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888)
        for(y in 0 until 48) for(x in 0 until 64) source.setPixel(x,y,Color.rgb(x*4,y*5,80))
        val result = ImageProcessor().applySimulation(source,FilmSimulation.DAYBREAK,FilmSettings(strength = 0f,grain = 0f))
        for((x,y) in listOf(3 to 2,60 to 45,30 to 20)) {
            val a = source.getPixel(x,y); val b = result.getPixel(x,y)
            assertTrue(kotlin.math.abs(Color.red(a)-Color.red(b)) <= 2)
            assertTrue(kotlin.math.abs(Color.green(a)-Color.green(b)) <= 2)
            assertTrue(kotlin.math.abs(Color.blue(a)-Color.blue(b)) <= 2)
        }
        source.recycle(); result.recycle()
    }
    @Test fun monochromeAndDiffusionRunOnGpu() = runBlocking {
        val source = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220,90,45)) }
        val result = ImageProcessor().applySimulation(source,FilmSimulation.SILVER,FilmSettings(grain = 0f,bloom = 0.6f))
        val c = result.getPixel(32,32)
        assertTrue(kotlin.math.abs(Color.red(c)-Color.green(c)) <= 1)
        assertTrue(kotlin.math.abs(Color.green(c)-Color.blue(c)) <= 1)
        assertTrue(Color.red(c) in 10..245)
        source.recycle(); result.recycle()
    }
    @Test fun tiledExportPreservesDimensionsAndBoundaryPixels() = runBlocking {
        val source = Bitmap.createBitmap(1200,1100,Bitmap.Config.ARGB_8888)
        for(y in 0 until source.height) for(x in 0 until source.width) source.setPixel(x,y,Color.rgb(x%256,y%256,80))
        val result = ImageProcessor().applySimulation(source,FilmSimulation.DAYBREAK,FilmSettings(strength = 0f,grain = 0f))
        assertEquals(source.width,result.width); assertEquals(source.height,result.height)
        for(x in listOf(0,1023,1024,1199)) for(y in listOf(0,1023,1024,1099)) {
            val a = source.getPixel(x,y); val b = result.getPixel(x,y)
            assertTrue(kotlin.math.abs(Color.red(a)-Color.red(b)) <= 2)
            assertTrue(kotlin.math.abs(Color.green(a)-Color.green(b)) <= 2)
        }
        source.recycle(); result.recycle()
    }
    @Test fun cropCoversAllRequestedRatios() {
        val source = Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
        for(ratio in listOf(4f/3f,3f/2f,16f/9f,1f,65f/24f)) {
            val out = CropEngine.cropToRatio(source,ratio)
            assertEquals(ratio,out.width.toFloat()/out.height,0.02f)
            assertTrue(out.width <= source.width && out.height <= source.height)
            if(out !== source) out.recycle()
        }
        source.recycle()
    }
}
