package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.LifesaverRepository
import com.example.ui.LifesaverApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.LifesaverViewModel
import com.example.viewmodel.LifesaverViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val database = AppDatabase.getDatabase(applicationContext)
    val repository = LifesaverRepository(
        applicationContext,
        database.taskDao(),
        database.scheduleBlockDao(),
        database.userProfileDao(),
        database.escalatingReminderDao()
    )
    val factory = LifesaverViewModelFactory(application, repository)
    val viewModel = ViewModelProvider(this, factory)[LifesaverViewModel::class.java]

    setContent {
      MyApplicationTheme {
        LifesaverApp(viewModel)
      }
    }
  }
}
