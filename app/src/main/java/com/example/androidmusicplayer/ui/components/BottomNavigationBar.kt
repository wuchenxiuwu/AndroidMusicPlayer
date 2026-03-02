package com.example.androidmusicplayer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

data class NavigationItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun BottomNavigationBar() {
    val navigationItems = listOf(
        NavigationItem("首页", Icons.Default.Home, "home"),
        NavigationItem("音乐库", Icons.Default.LibraryMusic, "library"),
        NavigationItem("播放列表", Icons.Default.PlaylistPlay, "playlists"),
        NavigationItem("搜索", Icons.Default.Search, "search")
    )
    
    var selectedItem by remember { mutableStateOf(0) }
    
    NavigationBar {
        navigationItems.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = selectedItem == index,
                onClick = { selectedItem = index }
            )
        }
    }
}

