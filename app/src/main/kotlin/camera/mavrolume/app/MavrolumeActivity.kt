package camera.mavrolume.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import camera.mavrolume.app.ui.MavrolumeNavHost
import camera.mavrolume.app.ui.theme.MavrolumeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MavrolumeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MavrolumeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MavrolumeNavHost()
                }
            }
        }
    }
}
