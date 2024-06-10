package com.example.battleship

import android.os.Bundle
import android.widget.AdapterView
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

class HostActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var gameId: String
    private lateinit var turnIndicator: TextView
    private lateinit var enemyGrid: GridView
    private lateinit var playerGrid: GridView

    private lateinit var enemyGridAdapter: GridAdapter
    private lateinit var playerGridAdapter: GridAdapter

    private lateinit var enemyGridItems: MutableList<GridItem>
    private lateinit var playerGridItems: MutableList<GridItem>

    private lateinit var gameRef: DocumentReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        gameId = intent.getStringExtra("gameId") ?: ""
        gameRef = db.collection("games").document(gameId)

        turnIndicator = findViewById(R.id.turnIndicator)
        enemyGrid = findViewById(R.id.enemyGrid)
        playerGrid = findViewById(R.id.playerGrid)

        initGrids()
        listenForUpdates()
    }

    private fun initGrids() {
        enemyGridItems = MutableList(64) { GridItem(false, false, it) }
        playerGridItems = MutableList(64) { GridItem(false, false, it) }

        enemyGridAdapter = GridAdapter(this, enemyGridItems)
        playerGridAdapter = GridAdapter(this, playerGridItems)

        enemyGrid.adapter = enemyGridAdapter
        playerGrid.adapter = playerGridAdapter

        enemyGrid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            handleEnemyGridClick(position)
        }

        // Load player's ships
        gameRef.get().addOnSuccessListener { document ->
            if (document != null) {
                val game = document.toObject(Game::class.java)
                if (game != null && game.player1Ships.isEmpty()) {
                    val ships = generateShips()
                    gameRef.update("player1Ships", ships)
                }
            }
        }
    }

    private fun generateShips(): List<GridItem> {
        val shipSizes = listOf(4, 3, 3, 2, 2)
        val ships = mutableListOf<GridItem>()
        val random = java.util.Random()

        for (size in shipSizes) {
            var placed = false
            while (!placed) {
                val start = random.nextInt(64)
                val horizontal = random.nextBoolean()
                if (canPlaceShip(start, size, horizontal)) {
                    for (i in 0 until size) {
                        val pos = if (horizontal) start + i else start + i * 8
                        ships.add(GridItem(isShip = true, isHit = false, position = pos))
                    }
                    placed = true
                }
            }
        }
        return ships
    }

    private fun canPlaceShip(start: Int, size: Int, horizontal: Boolean): Boolean {
        for (i in 0 until size) {
            val pos = if (horizontal) start + i else start + i * 8
            if (pos >= 64 || (horizontal && start % 8 + i >= 8)) {
                return false
            }
            if (playerGridItems[pos].isShip) {
                return false
            }
        }
        return true
    }

    private fun handleEnemyGridClick(position: Int) {
        gameRef.get().addOnSuccessListener { document ->
            if (document != null) {
                val game = document.toObject(Game::class.java)
                if (game != null && game.turn == auth.currentUser?.uid) {
                    val item = enemyGridItems[position]
                    if (!item.isHit) {
                        item.isHit = true
                        if (isShipAtPosition(game.player2Ships, position)) {
                            item.isShip = true
                        }
                        gameRef.update("player1Hits", enemyGridItems)
                        enemyGridAdapter.notifyDataSetChanged()
                        switchTurn(game)
                    }
                } else {
                    Toast.makeText(this, "Not your turn", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isShipAtPosition(ships: List<GridItem>, position: Int): Boolean {
        return ships.any { it.position == position && it.isShip }
    }

    private fun switchTurn(game: Game) {
        val nextTurn = if (game.turn == game.player1) game.player2 else game.player1
        gameRef.update("turn", nextTurn).addOnSuccessListener {
            updateTurnIndicator()
        }
    }

    private fun updateTurnIndicator() {
        val currentUser = auth.currentUser?.uid
        gameRef.get().addOnSuccessListener { document ->
            if (document != null) {
                val game = document.toObject(Game::class.java)
                if (game != null) {
                    val currentTurn = game.turn
                    turnIndicator.text = if (currentTurn == currentUser) "Your Turn" else "Opponent's Turn"
                }
            }
        }
    }

    private fun checkForWin(game: Game) {
        val currentUser = auth.currentUser?.uid
        val enemyShips = if (currentUser == game.player1) game.player2Ships else game.player1Ships
        if (enemyShips.all { it.isHit }) {
            Toast.makeText(this, "You won!", Toast.LENGTH_LONG).show()
            gameRef.delete()
        }
    }

    private fun listenForUpdates() {
        gameRef.addSnapshotListener { document, e ->
            if (e != null) {
                return@addSnapshotListener
            }

            if (document != null && document.exists()) {
                val game = document.toObject(Game::class.java)
                if (game != null) {
                    updateGrids(game)
                    updateTurnIndicator()
                }
            }
        }
    }

    private fun updateGrids(game: Game) {
        val currentUser = auth.currentUser?.uid

        // Update player's grid based on opponent's hits
        val enemyHits = if (currentUser == game.player1) game.player1Hits else game.player2Hits
        for (gridItem in enemyHits) {
            playerGridItems[gridItem.position].apply {
                isHit = gridItem.isHit
                isShip = gridItem.isShip
            }
        }
        playerGridAdapter.notifyDataSetChanged()

        // Update opponent's grid based on player's hits
        val playerHits = if (currentUser == game.player1) game.player2Hits else game.player1Hits
        for (gridItem in playerHits) {
            enemyGridItems[gridItem.position].apply {
                isHit = gridItem.isHit
                isShip = gridItem.isShip
            }
        }
        enemyGridAdapter.notifyDataSetChanged()
    }
}
