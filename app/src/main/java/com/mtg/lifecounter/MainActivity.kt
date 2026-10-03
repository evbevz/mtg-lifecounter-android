package com.mtg.lifecounter

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtg.lifecounter.dice.DiceView
import com.mtg.lifecounter.ui.GameScreen
import com.mtg.lifecounter.ui.theme.MTGLifeCounterTheme

class MainActivity : ComponentActivity() {

    private lateinit var diceView: DiceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        diceView = DiceView(this)
        val composeView = ComposeView(this).apply {
            setContent {
                MTGLifeCounterTheme {
                    val viewModel: GameViewModel = viewModel()
                    GameScreen(
                        viewModel = viewModel,
                        dice = diceView,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // The dice layer sits above the Compose content and is sized to the game area by GameScreen.
        setContentView(FrameLayout(this).apply {
            addView(composeView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(
                diceView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        })
    }

    override fun onResume() {
        super.onResume()
        diceView.onResume()
    }

    override fun onPause() {
        diceView.onPause()
        super.onPause()
    }
}
