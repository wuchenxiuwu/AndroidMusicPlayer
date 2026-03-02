package com.example.androidmusicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.androidmusicplayer.ui.components.BottomNavigationBar
import com.example.androidmusicplayer.ui.components.MainContent
import com.example.androidmusicplayer.ui.theme.AndroidMusicPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidMusicPlayerTheme {
                MusicPlayerApp()
            }
        }
    }
}

@Composable
fun MusicPlayerApp() {
    Scaffold(
        bottomBar = { BottomNavigationBar() }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            MainContent()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MusicPlayerAppPreview() {
    AndroidMusicPlayerTheme {
        MusicPlayerApp()
    }
}

