package com.example.hayequipoapp

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.example.hayequipoapp.ui.auth.AuthViewModel
import com.example.hayequipoapp.ui.navigation.HayEquipoNavHost
import com.example.hayequipoapp.ui.navigation.Routes
import com.example.hayequipoapp.ui.theme.HayEquipoTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HayEquipoApplication : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HayEquipoTheme {
                val authViewModel: AuthViewModel = hiltViewModel()
                val navController = rememberNavController()
                val startDestination = if (authViewModel.isLoggedIn) Routes.HOME else Routes.LOGIN

                HayEquipoNavHost(
                    navController    = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}
