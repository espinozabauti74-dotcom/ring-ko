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
import com.example.chat.ChatArenaViewModel
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
import com.example.wifi.WIFI_DEFAULT_PORT
import com.example.wifi.WifiFightViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

object AppRoutes {
    const val HUB = "hub"
    const val FIGHT = "fight/{fighterId}"
    const val VERSUS = "versus/{p2Name}/{rounds}"
    const val ONLINE_FIGHT = "online/{matchId}"
    const val WIFI_FIGHT = "wifi/{role}/{hostIp}/{port}/{rounds}/{charId}"
    const val CHAT_FIGHT = "chat_fight"
    fun fightRoute(fighterId: String): String = "fight/$fighterId"
    fun versusRoute(p2Name: String, rounds: Int): String = "versus/${Uri.encode(p2Name)}/$rounds"
    fun onlineFightRoute(matchId: String): String = "online/${Uri.encode(matchId)}"
    fun wifiHostRoute(rounds: Int, charId: String): String =
        "wifi/host/none/$WIFI_DEFAULT_PORT/$rounds/${Uri.encode(charId)}"
    fun wifiGuestRoute(hostIp: String, port: Int, charId: String): String =
        "wifi/guest/${Uri.encode(hostIp)}/$port/3/${Uri.encode(charId)}"
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
    val appearance by fightViewModel.appearance.collectAsStateWithLifecycle()
    val fightState by fightViewModel.uiState.collectAsStateWithLifecycle()
    val versusState by versusViewModel.uiState.collectAsStateWithLifecycle()

    val chatArenaViewModel: ChatArenaViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                ChatArenaViewModel(
                    soundEngine = BoxingSoundEngine(app),
                    soundEnabled = { fightViewModel.settings.value.soundEnabled },
                    hapticsEnabled = { fightViewModel.settings.value.hapticsEnabled }
                )
            }
        }
    )
    val chatRoomState by chatArenaViewModel.chatState.collectAsStateWithLifecycle()
    val chatFightState by chatArenaViewModel.fightState.collectAsStateWithLifecycle()

    LaunchedEffect(settings.playerName, economy.equippedGloveId, appearance) {
        chatArenaViewModel.syncPlayerConfig(
            playerName = settings.playerName,
            gloveId = economy.equippedGloveId,
            appearance = appearance
        )
    }

    LaunchedEffect(chatRoomState.activeBoutId) {
        if (chatRoomState.activeBoutId != null &&
            navController.currentDestination?.route != AppRoutes.CHAT_FIGHT
        ) {
            navController.navigate(AppRoutes.CHAT_FIGHT) {
                launchSingleTop = true
            }
        }
    }

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
                appearance = appearance,
                chatRoomState = chatRoomState,
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
                onHostWifiMatch = { rounds, charId ->
                    navController.navigate(AppRoutes.wifiHostRoute(rounds, charId)) {
                        launchSingleTop = true
                    }
                },
                onJoinWifiMatch = { hostIp, port, charId ->
                    navController.navigate(AppRoutes.wifiGuestRoute(hostIp, port, charId)) {
                        launchSingleTop = true
                    }
                },
                onCreateChatRoom = { title ->
                    chatArenaViewModel.createChatRoom(
                        roomTitle = title,
                        playerName = settings.playerName,
                        gloveId = economy.equippedGloveId,
                        appearance = appearance
                    )
                },
                onJoinChatRoom = { code ->
                    chatArenaViewModel.joinChatRoom(
                        rawCode = code,
                        playerName = settings.playerName,
                        gloveId = economy.equippedGloveId,
                        appearance = appearance
                    )
                },
                onLeaveChatRoom = chatArenaViewModel::leaveChatRoom,
                onSendChatMessage = chatArenaViewModel::sendChatMessage,
                onSelectChatCharacter = chatArenaViewModel::selectCharacter,
                onSelectChatRounds = chatArenaViewModel::selectRounds,
                onSendChatChallenge = chatArenaViewModel::sendChallenge,
                onCancelChatChallenge = chatArenaViewModel::cancelOutgoingChallenge,
                onAcceptChatChallenge = chatArenaViewModel::acceptIncomingChallenge,
                onDeclineChatChallenge = chatArenaViewModel::declineIncomingChallenge,
                onBuyOrEquipGlove = fightViewModel::buyOrEquipGlove,
                onUpdateAppearance = fightViewModel::updateAppearance,
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

            LaunchedEffect(p2Name, rounds, appearance, economy.equippedGlove) {
                versusViewModel.startVersusMatch(
                    p1Name = p1Name,
                    p2Name = p2Name,
                    maxRounds = rounds,
                    p1Appearance = appearance,
                    p1Glove = economy.equippedGlove
                )
            }

            VersusScreen(
                state = versusState,
                onMove = versusViewModel::setMove,
                onSetGuard = versusViewModel::setGuard,
                onDodge = versusViewModel::dodge,
                onThrowPunch = versusViewModel::throwPunch,
                onSkipPresentation = versusViewModel::skipPresentation,
                onTogglePause = versusViewModel::togglePause,
                onRematch = {
                    versusViewModel.startVersusMatch(
                        p1Name = p1Name,
                        p2Name = p2Name,
                        maxRounds = rounds,
                        p1Appearance = appearance,
                        p1Glove = economy.equippedGlove
                    )
                },
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

        composable(
            route = AppRoutes.WIFI_FIGHT,
            arguments = listOf(
                navArgument("role") { type = NavType.StringType },
                navArgument("hostIp") { type = NavType.StringType },
                navArgument("port") { type = NavType.IntType },
                navArgument("rounds") { type = NavType.IntType },
                navArgument("charId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "host"
            val hostIp = backStackEntry.arguments?.getString("hostIp") ?: "none"
            val port = backStackEntry.arguments?.getInt("port") ?: WIFI_DEFAULT_PORT
            val rounds = backStackEntry.arguments?.getInt("rounds") ?: 3
            val charId = backStackEntry.arguments?.getString("charId") ?: "custom"

            val wifiFightViewModel: WifiFightViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        WifiFightViewModel(
                            context = app,
                            soundEngine = BoxingSoundEngine(app),
                            soundEnabled = { settings.soundEnabled },
                            hapticsEnabled = { settings.hapticsEnabled }
                        )
                    }
                }
            )

            LaunchedEffect(role, hostIp, port, rounds, charId, settings.playerName, economy.equippedGloveId, appearance) {
                if (role == "host") {
                    wifiFightViewModel.startHost(
                        myName = settings.playerName,
                        myGloveId = economy.equippedGloveId,
                        rounds = rounds,
                        characterId = charId,
                        appearance = appearance
                    )
                } else {
                    wifiFightViewModel.startGuest(
                        hostIp = hostIp,
                        port = port,
                        myName = settings.playerName,
                        myGloveId = economy.equippedGloveId,
                        characterId = charId,
                        appearance = appearance
                    )
                }
            }

            val wifiState by wifiFightViewModel.uiState.collectAsStateWithLifecycle()
            OnlineFightScreen(
                state = wifiState,
                onMove = wifiFightViewModel::setMoveInput,
                onSetGuard = wifiFightViewModel::setGuard,
                onDodge = wifiFightViewModel::dodge,
                onThrowPunch = wifiFightViewModel::throwPunch,
                onSkipPresentation = wifiFightViewModel::skipPresentation,
                onExit = {
                    wifiFightViewModel.cancelOrForfeitAndExit {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(AppRoutes.CHAT_FIGHT) {
            OnlineFightScreen(
                state = chatFightState,
                onMove = chatArenaViewModel::setMoveInput,
                onSetGuard = chatArenaViewModel::setGuard,
                onDodge = chatArenaViewModel::dodge,
                onThrowPunch = chatArenaViewModel::throwPunch,
                onSkipPresentation = chatArenaViewModel::skipPresentation,
                onExit = {
                    chatArenaViewModel.exitFightToChat()
                    navController.popBackStack()
                }
            )
        }
    }
}
