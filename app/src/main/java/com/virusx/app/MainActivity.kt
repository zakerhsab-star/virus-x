package com.virusx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.virusx.app.data.repository.AuthRepository
import com.virusx.app.ui.navigation.VirusXNavHost
import com.virusx.app.ui.theme.VirusXTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VirusXTheme {
                VirusXNavHost(authRepository = authRepository)
            }
        }
    }
}
