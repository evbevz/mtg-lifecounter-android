package com.mtg.lifecounter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mtg.lifecounter.ui.MAX_LIFE_BASE

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

    fun setLifeBase(lifeBase: Int) {
        updateActivePlayer { it.copy(lifeBase = lifeBase.coerceIn(0, MAX_LIFE_BASE)) }
    }

    fun switchPlayer(index: Int) {
        if (index in players.indices) {
            activePlayerIndex = index
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
