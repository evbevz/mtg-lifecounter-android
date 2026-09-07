package com.mtg.lifecounter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class PlayerData(
    val life: Int = 20,
    val poison: Int = 0,
    val lifeBase: Int = 0
)

class GameViewModel : ViewModel() {

    var players by mutableStateOf(listOf(PlayerData(), PlayerData()))
        private set

    var activePlayerIndex by mutableIntStateOf(0)
        private set

    var diceValue by mutableIntStateOf(1)
        private set

    var isDiceRolling by mutableStateOf(false)
        private set

    val activePlayer: PlayerData
        get() = players[activePlayerIndex]

    fun setLife(life: Int) {
        updateActivePlayer { it.copy(life = life) }
    }

    fun adjustPoison(delta: Int) {
        updateActivePlayer {
            it.copy(poison = (it.poison + delta).coerceIn(0, 20))
        }
    }

    fun scrollLifeBase(delta: Int) {
        updateActivePlayer { it.copy(lifeBase = it.lifeBase + delta) }
    }

    fun switchPlayer(index: Int) {
        if (index in players.indices) {
            activePlayerIndex = index
        }
    }

    fun rollDice() {
        if (isDiceRolling) return
        viewModelScope.launch {
            isDiceRolling = true
            repeat(12) { i ->
                diceValue = Random.nextInt(1, 7)
                delay(80L + i * 40L)
            }
            isDiceRolling = false
        }
    }

    fun resetGame() {
        players = listOf(PlayerData(), PlayerData())
        activePlayerIndex = 0
    }

    private fun updateActivePlayer(transform: (PlayerData) -> PlayerData) {
        players = players.toMutableList().also {
            it[activePlayerIndex] = transform(it[activePlayerIndex])
        }
    }
}
