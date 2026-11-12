package com.virusx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.virusx.app.ui.theme.VirusXBackground
import com.virusx.app.ui.theme.VirusXSecondaryWhisper
import com.virusx.app.ui.theme.VirusXTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VirusXTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VirusXBackground)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "VirusX",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "Core Engine & Supabase Connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VirusXSecondaryWhisper,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}