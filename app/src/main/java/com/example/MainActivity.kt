package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.TournamentMatch
import com.example.data.remote.configureAppCheck
import com.example.data.repository.ExaloteRepository
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BadgePill
import com.example.ui.screens.DatabaseAndAdminConfigDialog
import com.example.ui.screens.MatchesLobbyScreen
import com.example.ui.screens.ProfileAndAiScreen
import com.example.ui.screens.ThreeBucketWalletScreen
import com.example.ui.theme.ExaloteEmerald
import com.example.ui.theme.ExaloteGold
import com.example.ui.theme.ExaloteOrange
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ExaloteViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase calls
    configureAppCheck(this, intent)

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          AppNavigation()
        }
      }
    }
  }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
  val context = LocalContext.current
  val databaseId = remember { context.getString(R.string.firestore_database_id) }
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  var showUnauthDbModal by remember { mutableStateOf(false) }
  var showWebPortalMode by rememberSaveable { mutableStateOf(false) }

  if (showWebPortalMode) {
    com.example.ui.screens.InteractiveWebPortalScreen(
      onBackToNativeApp = { showWebPortalMode = false }
    )
    return
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(
      onOpenDbConfigModal = { showUnauthDbModal = true },
      onLaunchWebPortal = { showWebPortalMode = true }
    )
    if (showUnauthDbModal) {
      DatabaseAndAdminConfigDialog(
        databaseId = databaseId,
        currentUserId = "",
        currentUserEmail = "",
        isAdmin = false,
        onGrantAdmin = { _, _ -> },
        onDismiss = { showUnauthDbModal = false }
      )
    }
  } else {
    val vm: ExaloteViewModel = viewModel(
      key = user.uid,
      factory = viewModelFactory {
        initializer {
          val app = checkNotNull(this[APPLICATION_KEY])
          val dbId = app.getString(R.string.firestore_database_id)
          val db = FirebaseFirestore.getInstance(dbId)
          ExaloteViewModel(
            repository = ExaloteRepository(db),
            currentUserId = user.uid,
            currentUserEmail = user.email,
            currentUserDisplayName = user.displayName
          )
        }
      }
    )
    AuthenticatedExaloteApp(
      viewModel = vm,
      databaseId = databaseId,
      onLaunchWebPortal = { showWebPortalMode = true }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedExaloteApp(
  viewModel: ExaloteViewModel,
  databaseId: String,
  onLaunchWebPortal: () -> Unit = {}
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val clipboardManager = LocalClipboardManager.current
  val credentialManager = remember { CredentialManager.create(context) }

  val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
  val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
  val matchesState by viewModel.matchesState.collectAsStateWithLifecycle()
  val userRegistrations by viewModel.userRegistrations.collectAsStateWithLifecycle()
  val userClaims by viewModel.userClaims.collectAsStateWithLifecycle()
  val userDeposits by viewModel.userDeposits.collectAsStateWithLifecycle()
  val userWithdrawals by viewModel.userWithdrawals.collectAsStateWithLifecycle()
  val userTransactions by viewModel.userTransactions.collectAsStateWithLifecycle()
  val promoCodes by viewModel.promoCodes.collectAsStateWithLifecycle()

  val adminClaims by viewModel.adminClaims.collectAsStateWithLifecycle()
  val adminDeposits by viewModel.adminDeposits.collectAsStateWithLifecycle()
  val adminWithdrawals by viewModel.adminWithdrawals.collectAsStateWithLifecycle()

  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
  val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val aiState by viewModel.aiState.collectAsStateWithLifecycle()
  val liveRoomRevealAlert by viewModel.liveRoomRevealAlert.collectAsStateWithLifecycle()

  var selectedNavIndex by rememberSaveable { mutableIntStateOf(0) }
  var showDbConfigDialog by rememberSaveable { mutableStateOf(false) }

  // BackHandler for secondary tabs returning to Home Arena (index 0)
  BackHandler(enabled = selectedNavIndex != 0) {
    selectedNavIndex = 0
  }

  val matches: List<TournamentMatch> = when (val st = matchesState) {
    is UiState.Success -> st.data
    else -> emptyList()
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.EmojiEvents,
              contentDescription = "Logo",
              tint = ExaloteOrange,
              modifier = Modifier.size(24.dp)
            )
            Column {
              Text(
                text = "EXALOTE EXN",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
              )
              Text(
                text = if (userProfile?.hasValidFreeFireBinding == true) {
                  "IGN: ${userProfile?.ffIgn} (${userProfile?.ffUid})"
                } else {
                  "FF UID Unbound • Tap Profile"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (userProfile?.hasValidFreeFireBinding == true) ExaloteEmerald else ExaloteGold
              )
            }
          }
        },
        actions = {
          // Quick 3-Bucket Balance Pill
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable { selectedNavIndex = 2 }
              .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Filled.AccountBalanceWallet,
              contentDescription = "Wallet",
              tint = ExaloteGold,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "₹${String.format("%.0f", userProfile?.walletBalance ?: 0.0)}",
              style = MaterialTheme.typography.labelLarge,
              color = ExaloteGold
            )
          }
          TextButton(
            onClick = onLaunchWebPortal,
            modifier = Modifier.testTag("topbar_web_portal_button")
          ) {
            Text(
              text = "Web Mode",
              style = MaterialTheme.typography.labelMedium,
              color = ExaloteEmerald,
              fontWeight = FontWeight.Bold
            )
          }
          IconButton(
            onClick = { showDbConfigDialog = true },
            modifier = Modifier.testTag("topbar_db_config_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Security,
              contentDescription = "Database & Admin Config",
              tint = if (isAdmin) ExaloteGold else Color.White
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface
      ) {
        NavigationBarItem(
          selected = selectedNavIndex == 0,
          onClick = { selectedNavIndex = 0 },
          icon = { Icon(Icons.Filled.SportsEsports, contentDescription = "Arena") },
          label = { Text("Arena") },
          modifier = Modifier.testTag("nav_arena")
        )
        NavigationBarItem(
          selected = selectedNavIndex == 1,
          onClick = { selectedNavIndex = 1 },
          icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = "My Matches") },
          label = { Text("Joined (${userRegistrations.size})") },
          modifier = Modifier.testTag("nav_my_matches")
        )
        NavigationBarItem(
          selected = selectedNavIndex == 2,
          onClick = { selectedNavIndex = 2 },
          icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Wallet") },
          label = { Text("Wallet") },
          modifier = Modifier.testTag("nav_wallet")
        )
        NavigationBarItem(
          selected = selectedNavIndex == 3,
          onClick = { selectedNavIndex = 3 },
          icon = { Icon(Icons.Filled.Person, contentDescription = "Profile & AI") },
          label = { Text("Profile") },
          modifier = Modifier.testTag("nav_profile")
        )
        if (isAdmin) {
          NavigationBarItem(
            selected = selectedNavIndex == 4,
            onClick = { selectedNavIndex = 4 },
            icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Admin") },
            label = { Text("Admin") },
            modifier = Modifier.testTag("nav_admin")
          )
        }
      }
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      if (isBusy) {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth(),
          color = ExaloteOrange
        )
      }

      // Live Status / Error Feedback Banner
      AnimatedVisibility(visible = statusBanner != null || errorBanner != null) {
        val isErr = errorBanner != null
        val text = errorBanner ?: statusBanner ?: ""
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (isErr) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            else ExaloteEmerald.copy(alpha = 0.2f)
          ),
          shape = RoundedCornerShape(12.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = text,
              style = MaterialTheme.typography.bodyMedium,
              color = if (isErr) MaterialTheme.colorScheme.error else ExaloteEmerald,
              modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.clearBanners() }) {
              Icon(imageVector = Icons.Filled.Close, contentDescription = "Dismiss")
            }
          }
        }
      }

      Box(modifier = Modifier.weight(1f)) {
        when (selectedNavIndex) {
          0 -> MatchesLobbyScreen(
            matches = matches,
            registrations = userRegistrations,
            claims = userClaims,
            userProfile = userProfile,
            isAdmin = isAdmin,
            onJoinMatch = { matchId, teammates ->
              viewModel.joinMatch(matchId, teammates)
            },
            onSubmitClaim = { matchId, title, kills, rank, base64, mime, suspect, reason ->
              viewModel.submitClaim(matchId, title, kills, rank, base64, mime, suspect, reason)
            },
            onNavigateToProfileBinding = { selectedNavIndex = 3 },
            onSeedMatchesIfEmpty = { viewModel.seedOfficialExaloteMatches() }
          )

          1 -> {
            val joinedIds = userRegistrations.map { it.matchId }.toSet()
            val joinedMatches = matches.filter { it.id in joinedIds }
            MatchesLobbyScreen(
              matches = joinedMatches,
              registrations = userRegistrations,
              claims = userClaims,
              userProfile = userProfile,
              isAdmin = false,
              onJoinMatch = { matchId, teammates ->
                viewModel.joinMatch(matchId, teammates)
              },
              onSubmitClaim = { matchId, title, kills, rank, base64, mime, suspect, reason ->
                viewModel.submitClaim(matchId, title, kills, rank, base64, mime, suspect, reason)
              },
              onNavigateToProfileBinding = { selectedNavIndex = 3 },
              onSeedMatchesIfEmpty = {}
            )
          }

          2 -> ThreeBucketWalletScreen(
            userProfile = userProfile,
            deposits = userDeposits,
            withdrawals = userWithdrawals,
            transactions = userTransactions,
            promoCodes = promoCodes,
            onSubmitDeposit = { amt, utr, promo -> viewModel.submitDeposit(amt, utr, promo) },
            onRequestWithdrawal = { amt, upi -> viewModel.requestWithdrawal(amt, upi) }
          )

          3 -> ProfileAndAiScreen(
            userProfile = userProfile,
            isAdmin = isAdmin,
            aiState = aiState,
            onSaveBinding = { name, uid, ign, upi ->
              viewModel.saveProfileBinding(name, uid, ign, upi)
            },
            onApplyReferralCode = { code ->
              viewModel.applyReferralCode(code)
            },
            onRunHighThinkingQuery = { query ->
              viewModel.runHighThinkingAnalysis(query)
            },
            onOpenDbConfigModal = { showDbConfigDialog = true },
            onSignOut = {
              signOutUser(
                credentialManager = credentialManager,
                onSignOutComplete = {},
                scope = scope
              )
            }
          )

          4 -> AdminDashboardScreen(
            isAdmin = isAdmin,
            matches = matches,
            claims = adminClaims,
            deposits = adminDeposits,
            withdrawals = adminWithdrawals,
            onCreateMatch = { title, code, session, date, time, epoch, maxP, fee, pool, killRate, r1, r2, r3, map, rules ->
              viewModel.createMatchAsAdmin(
                title = title,
                categoryCode = code,
                session = session,
                matchDate = date,
                startTime = time,
                startTimeEpochMs = epoch,
                maxPlayers = maxP,
                entryFee = fee,
                prizePool = pool,
                perKillRate = killRate,
                rank1Prize = r1,
                rank2Prize = r2,
                rank3Prize = r3,
                mapName = map,
                matchRules = rules
              )
            },
            onSeedMatches = { viewModel.seedOfficialExaloteMatches() },
            onPublishRoom = { id, rId, rPass, pub, st ->
              viewModel.publishRoomAsAdmin(id, rId, rPass, pub, st)
            },
            onVerifyClaimAndDistributePrize = { claimId, k, r, lock ->
              viewModel.verifyClaimAndPayoutAsAdmin(claimId, k, r, lock)
            },
            onApproveOrRejectDeposit = { depId, approve ->
              viewModel.handleDepositDecisionAsAdmin(depId, approve)
            },
            onApproveOrRejectWithdrawal = { wdId, approve ->
              viewModel.handleWithdrawalDecisionAsAdmin(wdId, approve)
            },
            onRunAiAuditOnClaims = { prompt ->
              viewModel.runHighThinkingAnalysis(prompt)
              selectedNavIndex = 3
            }
          )
        }
      }
    }
  }

  // Real-Time Custom Room ID & Password Live Reveal Alert Dialog
  val revealedMatch = liveRoomRevealAlert
  if (revealedMatch != null) {
    AlertDialog(
      onDismissRequest = { viewModel.dismissRoomRevealAlert() },
      icon = {
        Icon(
          imageVector = Icons.Filled.Key,
          contentDescription = "Room Published",
          tint = ExaloteEmerald,
          modifier = Modifier.size(36.dp)
        )
      },
      title = {
        Text("CUSTOM ROOM ID PUBLISHED LIVE!", style = MaterialTheme.typography.titleLarge)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Match: ${revealedMatch.title} (${revealedMatch.mapName})",
            style = MaterialTheme.typography.titleMedium,
            color = ExaloteGold
          )
          Text("Room ID: ${revealedMatch.roomId}", style = MaterialTheme.typography.headlineMedium)
          Text(
            "Password: ${revealedMatch.roomPassword.ifBlank { "None" }}",
            style = MaterialTheme.typography.titleLarge,
            color = ExaloteEmerald
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            clipboardManager.setText(AnnotatedString(revealedMatch.roomId))
            viewModel.dismissRoomRevealAlert()
          },
          colors = ButtonDefaults.buttonColors(containerColor = ExaloteEmerald)
        ) {
          Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copy Room ID", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissRoomRevealAlert() }) {
          Text("Close")
        }
      }
    )
  }

  // Built-in Database & Admin Config Modal
  if (showDbConfigDialog) {
    DatabaseAndAdminConfigDialog(
      databaseId = databaseId,
      currentUserId = viewModel.currentUserId,
      currentUserEmail = viewModel.currentUserEmail ?: "",
      isAdmin = isAdmin,
      onGrantAdmin = { uid, email -> viewModel.grantAdminRole(uid, email) },
      onDismiss = { showDbConfigDialog = false }
    )
  }
}
