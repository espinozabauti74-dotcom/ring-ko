package com.example

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.audio.BoxingSoundEngine
import com.example.game.FightViewModel
import com.example.game.VersusViewModel
import com.example.online.GoogleSignInGateCard
import com.example.online.OnlineFightScreen
import com.example.online.OnlineFightViewModel
import com.example.online.OnlineRepository
import com.example.online.attemptAutoSignIn
import com.example.ui.fight.FightScreen
import com.example.ui.hub.HubScreen
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.RingKoTheme
import com.example.ui.versus.VersusScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

object AppRoutes {
    const val HUB = "hub"
    const val FIGHT = "fight/{fighterId}"
    const val VERSUS = "versus/{p2Name}/{rounds}"
    const val ONLINE_FIGHT = "online/{matchId}"
    fun fightRoute(fighterId: String): String = "fight/$fighterId"
    fun versusRoute(p2Name: String, rounds: Int): String = "versus/${Uri.encode(p2Name)}/$rounds"
    fun onlineFightRoute(matchId: String): String = "online/${Uri.encode(matchId)}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            RingKoTheme {
                RingKoApp()
            }
        }
    }
}

@Composable
fun RingKoApp(
    fightViewModel: FightViewModel = viewModel(),
    versusViewModel: VersusViewModel = viewModel()
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val auth = remember { Firebase.auth }
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { currentUser = auth.currentUser },
            onUnauthenticated = {},
            scope = scope
        )
    }

    val sessionRecord by fightViewModel.sessionRecord.collectAsStateWithLifecycle()
    val economy by fightViewModel.economy.collectAsStateWithLifecycle()
    val settings by fightViewModel.settings.collectAsStateWithLifecycle()
    val fightState by fightViewModel.uiState.collectAsStateWithLifecycle()
    val versusState by versusViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.HUB
    ) {
        composable(AppRoutes.HUB) {
            HubScreen(
                currentUser = currentUser,
                sessionRecord = sessionRecord,
                economy = economy,
                settings = settings,
                onAuthSuccess = { currentUser = auth.currentUser },
                onStartFight = { fighterId ->
                    navController.navigate(AppRoutes.fightRoute(fighterId)) {
                        launchSingleTop = true
                    }
                },
                onStartVersus = { p2Name, rounds ->
                    navController.navigate(AppRoutes.versusRoute(p2Name, rounds)) {
                        launchSingleTop = true
                    }
                },
                onEnterOnlineMatch = { matchId ->
                    navController.navigate(AppRoutes.onlineFightRoute(matchId)) {
                        launchSingleTop = true
                    }
                },
                onBuyOrEquipGlove = fightViewModel::buyOrEquipGlove,
                onSavePlayerName = fightViewModel::setPlayerName,
                onToggleSound = fightViewModel::setSoundEnabled,
                onToggleHaptics = fightViewModel::setHapticsEnabled
            )
        }

        composable(
            route = AppRoutes.FIGHT,
            arguments = listOf(
                navArgument("fighterId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val fighterId = backStackEntry.arguments?.getString("fighterId") ?: "mateo"
            LaunchedEffect(fighterId) {
                fightViewModel.startMatch(fighterId)
            }

            FightScreen(
                state = fightState,
                onThrowPunch = fightViewModel::throwPunch,
                onSetGuard = fightViewModel::setGuard,
                onDodge = fightViewModel::dodge,
                onSetMoveInput = fightViewModel::setMoveInput,
                onSkipPresentation = fightViewModel::skipPresentation,
                onMashGetUp = fightViewModel::mashGetUp,
                onTogglePause = fightViewModel::togglePause,
                onRematch = { fightViewModel.startMatch(fighterId) },
                onExitToHub = {
                    fightViewModel.stopMatch()
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = AppRoutes.VERSUS,
            arguments = listOf(
                navArgument("p2Name") { type = NavType.StringType },
                navArgument("rounds") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val p2Name = backStackEntry.arguments?.getString("p2Name") ?: "Jugador 2"
            val rounds = backStackEntry.arguments?.getInt("rounds") ?: 3
            val p1Name = settings.playerName

            LaunchedEffect(p2Name, rounds) {
                versusViewModel.startVersusMatch(p1Name = p1Name, p2Name = p2Name, maxRounds = rounds)
            }

            VersusScreen(
                state = versusState,
                onMove = versusViewModel::setMove,
                onSetGuard = versusViewModel::setGuard,
                onDodge = versusViewModel::dodge,
                onThrowPunch = versusViewModel::throwPunch,
                onSkipPresentation = versusViewModel::skipPresentation,
                onTogglePause = versusViewModel::togglePause,
                onRematch = { versusViewModel.startVersusMatch(p1Name = p1Name, p2Name = p2Name, maxRounds = rounds) },
                onExit = {
                    versusViewModel.stopMatch()
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = AppRoutes.ONLINE_FIGHT,
            arguments = listOf(
                navArgument("matchId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getString("matchId") ?: ""
            val user = currentUser
            if (user == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ArenaObsidian)
                        .padding(24.dp)
                ) {
                    GoogleSignInGateCard(onAuthSuccess = { currentUser = auth.currentUser })
                }
            } else {
                val uid = user.uid
                key(uid) {
                    val onlineFightViewModel: OnlineFightViewModel = viewModel(
                        key = uid,
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY]) {
                                    "APPLICATION_KEY missing from CreationExtras"
                                }
                                OnlineFightViewModel(
                                    repository = OnlineRepository(app),
                                    soundEngine = BoxingSoundEngine(app),
                                    currentUserId = uid,
                                    soundEnabled = { settings.soundEnabled },
                                    hapticsEnabled = { settings.hapticsEnabled }
                                )
                            }
                        }
                    )

                    LaunchedEffect(matchId) {
                        if (matchId.isNotBlank()) {
                            onlineFightViewModel.attachMatch(matchId)
                        }
                    }

                    val onlineState by onlineFightViewModel.uiState.collectAsStateWithLifecycle()
                    OnlineFightScreen(
                        state = onlineState,
                        onMove = onlineFightViewModel::setMoveInput,
                        onSetGuard = onlineFightViewModel::setGuard,
                        onDodge = onlineFightViewModel::dodge,
                        onThrowPunch = onlineFightViewModel::throwPunch,
                        onSkipPresentation = onlineFightViewModel::skipPresentation,
                        onExit = {
                            onlineFightViewModel.cancelOrForfeitAndExit {
                                navController.popBackStack()
                            }
                        }
                    )
                }
            }
        }
    }
}
