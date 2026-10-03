package camera.mavrolume.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import camera.mavrolume.app.ui.theme.FilmAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    rawCapable: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // -- Export section --
            SectionHeader("Export")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Save Original",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Keep pre-LUT JPEG in Originals folder",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = prefs.saveOriginal,
                    onCheckedChange = { viewModel.setSaveOriginal(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = FilmAccent,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray,
                    ),
                )
            }

            // Save DNG toggle — only visible when device supports RAW
            if (rawCapable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Save DNG",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Save raw sensor data as DNG (~80 MB per photo)",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = prefs.saveDng,
                        onCheckedChange = { viewModel.setSaveDng(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = FilmAccent,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -- About section --
            SectionHeader("About")

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Mavrolume",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "v0.1.0",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = FilmAccent,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}
