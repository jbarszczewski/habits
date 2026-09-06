package com.jbarszczewski.habits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jbarszczewski.habits.ui.HabitsApp
import com.jbarszczewski.habits.ui.theme.HabitsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HabitsTheme {
                HabitsApp()
            }
        }
    }
}
