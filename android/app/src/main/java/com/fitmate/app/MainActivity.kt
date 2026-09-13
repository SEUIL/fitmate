package com.fitmate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.lifecycle.lifecycleScope
import com.fitmate.app.samsunghealth.SamsungHealthManager
import com.fitmate.app.samsunghealth.SamsungHealthStatus
import com.fitmate.app.samsunghealth.SamsungHealthUiState
import com.fitmate.app.ui.SamsungHealthTestScreen
import com.fitmate.app.ui.FitMatePrototypeApp
import com.fitmate.app.ui.theme.FitmateTheme
import com.fitmate.app.ui.theme.AppearanceMode
import com.fitmate.app.ui.theme.AppearancePreference
import com.fitmate.app.ui.theme.LocalAppearancePreference
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var manager: SamsungHealthManager
    private var uiState by mutableStateOf(SamsungHealthUiState())
    private var showSamsungHealthTest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Fitmate)
        super.onCreate(savedInstanceState)
        manager = SamsungHealthManager(applicationContext)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences("fitmate_appearance", MODE_PRIVATE) }
            var appearance by remember { mutableStateOf(AppearanceMode.fromStored(preferences.getString("mode", null))) }
            val dark = when (appearance) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            CompositionLocalProvider(LocalAppearancePreference provides AppearancePreference(appearance) { selected ->
                preferences.edit().putString("mode", selected.name).apply()
                appearance = selected
            }) {
            FitmateTheme(darkTheme = dark) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (showSamsungHealthTest) {
                        Column(Modifier.padding(innerPadding)) {
                            Button(onClick = { showSamsungHealthTest = false }) { Text("FitMate 프로토타입으로 돌아가기") }
                            SamsungHealthTestScreen(uiState, ::requestPermissions, ::loadData, ::loadData, ::loadData, { manager.resolve(this@MainActivity) }, Modifier)
                        }
                    } else {
                        FitMatePrototypeApp(
                            onOpenSamsungTest = { showSamsungHealthTest = true },
                            samsungHealthState = uiState,
                            onRefreshSamsungHealth = ::loadData,
                            onLoadSamsungWorkouts = ::loadWorkouts,
                            onRequestSamsungExercisePermission = ::requestExercisePermission,
                        )
                    }
                }
            }
        }
        }
        refreshState()
    }

    override fun onResume() { super.onResume(); refreshState() }

    private fun refreshState() = lifecycleScope.launch {
        val previous = uiState
        uiState = manager.refresh().copy(
            workouts = previous.workouts,
            lastLoadedAtMillis = previous.lastLoadedAtMillis,
        )
    }

    private fun loadData() = lifecycleScope.launch {
        uiState = uiState.copy(status = SamsungHealthStatus.LOADING)
        uiState = manager.load()
    }

    private fun requestPermissions() = lifecycleScope.launch {
        uiState = uiState.copy(status = SamsungHealthStatus.LOADING)
        uiState = manager.requestPermissions(this@MainActivity)
    }

    private fun requestExercisePermission() = lifecycleScope.launch {
        uiState = uiState.copy(status = SamsungHealthStatus.LOADING)
        uiState = manager.requestExercisePermission(this@MainActivity)
    }

    private fun loadWorkouts(
        startedAt: java.time.Instant,
        endedAt: java.time.Instant,
        loaded: (SamsungHealthUiState) -> Unit,
    ) {
        lifecycleScope.launch {
            uiState = uiState.copy(status = SamsungHealthStatus.LOADING)
            uiState = manager.loadWorkouts(startedAt, endedAt)
            loaded(uiState)
        }
    }
}
