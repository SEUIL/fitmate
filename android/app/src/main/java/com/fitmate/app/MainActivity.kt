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
import androidx.lifecycle.lifecycleScope
import com.fitmate.app.samsunghealth.SamsungHealthManager
import com.fitmate.app.samsunghealth.SamsungHealthStatus
import com.fitmate.app.samsunghealth.SamsungHealthUiState
import com.fitmate.app.ui.SamsungHealthTestScreen
import com.fitmate.app.ui.theme.FitmateTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var manager: SamsungHealthManager
    private var uiState by mutableStateOf(SamsungHealthUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = SamsungHealthManager(applicationContext)
        enableEdgeToEdge()
        setContent {
            FitmateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SamsungHealthTestScreen(uiState, ::requestPermissions, ::loadData, ::loadData, ::loadData, { manager.resolve(this@MainActivity) }, Modifier.padding(innerPadding))
                }
            }
        }
        refreshState()
    }

    override fun onResume() { super.onResume(); refreshState() }

    private fun refreshState() = lifecycleScope.launch {
        uiState = manager.refresh()
    }

    private fun loadData() = lifecycleScope.launch {
        uiState = uiState.copy(status = SamsungHealthStatus.LOADING)
        uiState = manager.load()
    }

    private fun requestPermissions() = lifecycleScope.launch {
        uiState = uiState.copy(status = SamsungHealthStatus.LOADING)
        uiState = manager.requestPermissions(this@MainActivity)
    }
}
