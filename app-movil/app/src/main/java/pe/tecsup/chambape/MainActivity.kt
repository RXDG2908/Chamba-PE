package pe.tecsup.chambape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pe.tecsup.chambape.ui.ChambaPeApp
import pe.tecsup.chambape.ui.ChambaPeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChambaPeTheme {
                ChambaPeApp()
            }
        }
    }
}
