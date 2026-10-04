package com.r0ybt.taleframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.r0ybt.taleframe.state.StoryViewModel
import com.r0ybt.taleframe.ui.TaleFrameApp
import com.r0ybt.taleframe.ui.theme.TaleFrameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val model = ViewModelProvider(this)[StoryViewModel::class.java]
        setContent { TaleFrameTheme { TaleFrameApp(model) } }
    }
}
