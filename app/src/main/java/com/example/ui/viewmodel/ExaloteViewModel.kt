package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DEFAULT_PROMO_CODES
import com.example.data.model.DepositRequest
import com.example.data.model.MatchClaim
import com.example.data.model.MatchRegistration
import com.example.data.model.PromoCode
import com.example.data.model.TeammateInput
import com.example.data.model.TournamentMatch
import com.example.data.model.UserProfile
import com.example.data.model.WalletTransaction
import com.example.data.model.WithdrawalRequest
import com.example.data.repository.ExaloteRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiThinkingState {
  data object Idle : AiThinkingState
  data object Thinking : AiThinkingState
  data class Streaming(val partialText: String) : AiThinkingState
  data class Success(val text: String) : AiThinkingState
  data class Error(val message: String) : AiThinkingState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExaloteViewModel(
  private val repository: ExaloteRepository,
  val currentUserId: String,
  val currentUserEmail: String?,
  val currentUserDisplayName: String?
) : ViewModel() {

  private val highThinkingModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-4-argon",
      generationConfig = generationConfig {
        thinkingConfig = thinkingConfig {
          thinkingLevel = ThinkingLevel.HIGH
        }
      }
    )
  }

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _errorBanner = MutableStateFlow<String?>(null)
  val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _aiState = MutableStateFlow<AiThinkingState>(AiThinkingState.Idle)
  val aiState: StateFlow<AiThinkingState> = _aiState.asStateFlow()

  private val _liveRoomRevealAlert = MutableStateFlow<TournamentMatch?>(null)
  val liveRoomRevealAlert: StateFlow<TournamentMatch?> = _liveRoomRevealAlert.asStateFlow()

  private val seenPublishedRoomMatchIds = mutableSetOf<String>()

  val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
    .catch { e ->
      Log.w(TAG, "Error observing userProfile", e)
      emit(null)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val isAdmin: StateFlow<Boolean> = repository.observeIsAdmin(currentUserId, currentUserEmail)
    .catch { emit(currentUserEmail?.equals("foodievlog422@gmail.com", ignoreCase = true) == true) }
    .stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000L),
      currentUserEmail?.equals("foodievlog422@gmail.com", ignoreCase = true) == true
    )

  val promoCodes: StateFlow<List<PromoCode>> = repository.observePromoCodes()
    .catch { emit(DEFAULT_PROMO_CODES) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DEFAULT_PROMO_CODES)

  val matchesState: StateFlow<UiState<List<TournamentMatch>>> = repository.observePublishedMatches()
    .map<List<TournamentMatch>, UiState<List<TournamentMatch>>> { UiState.Success(it) }
    .catch { e ->
      Log.w(TAG, "Error observing matches", e)
      emit(UiState.Error(e.message ?: "Failed to load matches"))
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val userRegistrations: StateFlow<List<MatchRegistration>> =
    repository.observeUserRegistrations(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val userClaims: StateFlow<List<MatchClaim>> =
    repository.observeUserClaims(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val userDeposits: StateFlow<List<DepositRequest>> =
    repository.observeUserDeposits(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val userWithdrawals: StateFlow<List<WithdrawalRequest>> =
    repository.observeUserWithdrawals(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val userTransactions: StateFlow<List<WalletTransaction>> =
    repository.observeUserTransactions(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val adminRegistrations: StateFlow<List<MatchRegistration>> = isAdmin
    .flatMapLatest { admin ->
      if (admin) repository.observeAllRegistrationsForAdmin() else flowOf(emptyList())
    }
    .catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val adminClaims: StateFlow<List<MatchClaim>> = isAdmin
    .flatMapLatest { admin ->
      if (admin) repository.observeAllClaimsForAdmin() else flowOf(emptyList())
    }
    .catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val adminDeposits: StateFlow<List<DepositRequest>> = isAdmin
    .flatMapLatest { admin ->
      if (admin) repository.observeAllDepositsForAdmin() else flowOf(emptyList())
    }
    .catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val adminWithdrawals: StateFlow<List<WithdrawalRequest>> = isAdmin
    .flatMapLatest { admin ->
      if (admin) repository.observeAllWithdrawalsForAdmin() else flowOf(emptyList())
    }
    .catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  init {
    viewModelScope.launch {
      repository.ensureUserProfile(
        username = currentUserDisplayName ?: "Survivor",
        email = currentUserEmail ?: "player@exalote.gg"
      )
    }

    // Real-time Room Reveal detector: triggers instant alert when Admin publishes Room ID for a joined match
    viewModelScope.launch {
      combine(matchesState, userRegistrations) { mState, regs ->
        val matches = (mState as? UiState.Success)?.data ?: emptyList()
        val joinedMatchIds = regs.filter { it.status == "Approved" || it.feeStatus == "paid" }
          .map { it.matchId }
          .toSet()
        matches.filter {
          it.id in joinedMatchIds && it.roomPublished && it.roomId.isNotBlank()
        }
      }.collect { revealedJoinedMatches ->
        for (match in revealedJoinedMatches) {
          if (seenPublishedRoomMatchIds.add(match.id)) {
            _liveRoomRevealAlert.value = match
          }
        }
      }
    }
  }

  fun dismissRoomRevealAlert() {
    _liveRoomRevealAlert.value = null
  }

  fun clearBanners() {
    _statusBanner.value = null
    _errorBanner.value = null
  }

  fun saveProfileBinding(username: String, ffUid: String, ffIgn: String, upiId: String) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.bindFreeFireProfile(username, ffUid, ffIgn, upiId)
      _isBusy.value = false
      res.onSuccess {
        _statusBanner.value = "Free Fire MAX UID ($ffUid) & IGN ($ffIgn) bound successfully!"
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Failed to bind Free Fire profile."
      }
    }
  }

  fun applyReferralCode(rawCode: String) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.applyReferralCode(rawCode)
      _isBusy.value = false
      res.onSuccess { bonus ->
        _statusBanner.value =
          "Referral code applied! Credited +₹${String.format("%.0f", bonus)} to your Bonus Balance (Referrer gets +₹10 when your 1st deposit is approved)."
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Could not apply referral code."
      }
    }
  }

  fun joinMatch(matchId: String, teammates: List<TeammateInput>, onJoined: () -> Unit = {}) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.joinMatchAtomically(matchId, teammates)
      _isBusy.value = false
      res.onSuccess { reg ->
        _statusBanner.value =
          "Joined ${reg.matchTitle}! Slot #${reg.slotNumber} assigned (Bonus: ₹${reg.bonusUsed}, Deposit: ₹${reg.depositUsed}, Winnings: ₹${reg.winningsUsed})."
        onJoined()
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Could not join match."
      }
    }
  }

  fun submitClaim(
    matchId: String,
    matchTitle: String,
    claimedKills: Int,
    claimedRank: Int,
    screenshotBase64: String,
    screenshotMime: String,
    reportSuspectIgn: String,
    reportReason: String,
    onDone: () -> Unit = {}
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.submitResultClaim(
        matchId = matchId,
        matchTitle = matchTitle,
        claimedKills = claimedKills,
        claimedRank = claimedRank,
        screenshotBase64 = screenshotBase64,
        screenshotMime = screenshotMime,
        reportSuspectIgn = reportSuspectIgn,
        reportReason = reportReason
      )
      _isBusy.value = false
      res.onSuccess {
        _statusBanner.value = "Result claim & screenshot submitted for Admin verification!"
        onDone()
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Failed to submit result claim."
      }
    }
  }

  fun submitDeposit(amount: Double, utrNumber: String, promoCode: String = "") {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.submitUpiDeposit(amount, utrNumber, promoCode)
      _isBusy.value = false
      res.onSuccess { bonusAmt ->
        val bonusText = if (bonusAmt > 0.0) {
          " + ₹${String.format("%.2f", bonusAmt)} Bonus (${promoCode.trim().uppercase()})"
        } else {
          ""
        }
        _statusBanner.value =
          "UPI Deposit (₹$amount$bonusText | UTR: $utrNumber) queued for live Admin approval!"
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Failed to submit UPI deposit."
      }
    }
  }

  fun requestWithdrawal(amount: Double, upiId: String) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.requestWithdrawalWithEscrow(amount, upiId)
      _isBusy.value = false
      res.onSuccess {
        _statusBanner.value =
          "Withdrawal of ₹$amount to $upiId placed in escrow from Winnings Balance!"
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Withdrawal request failed."
      }
    }
  }

  // --- ADMIN OPERATIONS ---

  fun createMatchAsAdmin(
    title: String,
    categoryCode: String,
    session: String,
    matchDate: String,
    startTime: String,
    startTimeEpochMs: Long,
    maxPlayers: Int,
    entryFee: Double,
    prizePool: Double,
    perKillRate: Double,
    rank1Prize: Double,
    rank2Prize: Double,
    rank3Prize: Double,
    mapName: String,
    matchRules: String
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.createMatchAsAdmin(
        title = title,
        categoryCode = categoryCode,
        session = session,
        matchDate = matchDate,
        startTime = startTime,
        startTimeEpochMs = startTimeEpochMs,
        maxPlayers = maxPlayers,
        entryFee = entryFee,
        prizePool = prizePool,
        perKillRate = perKillRate,
        rank1Prize = rank1Prize,
        rank2Prize = rank2Prize,
        rank3Prize = rank3Prize,
        mapName = mapName,
        matchRules = matchRules
      )
      _isBusy.value = false
      res.onSuccess {
        _statusBanner.value = "Tournament match created and published live!"
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Failed to create match."
      }
    }
  }

  fun seedOfficialExaloteMatches() {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val now = System.currentTimeMillis()
      val presets = listOf(
        Triple("EXN Bermuda Per-Kill Pro", "full-map-per-kill", "Bermuda"),
        Triple("EXN Lone Wolf Headshot 1v1", "headshot-1v1", "Iron Cage"),
        Triple("EXN Headshot 2v2 Showdown", "headshot-2v2", "Iron Cage"),
        Triple("EXN Clash Squad 4v4 Finals", "clash-squad-4v4", "Purgatory"),
        Triple("EXN Desert Eagle 1v1 Duel", "desert-eagle-1v1", "Bermuda")
      )
      var createdCount = 0
      for ((idx, item) in presets.withIndex()) {
        val (title, code, map) = item
        val isSoloFull = code == "full-map-per-kill"
        val res = repository.createMatchAsAdmin(
          title = title,
          categoryCode = code,
          session = if (idx % 2 == 0) "evening" else "morning",
          matchDate = "2026-11-01",
          startTime = "${16 + idx}:00",
          startTimeEpochMs = now + (idx + 1) * 3600_000L,
          maxPlayers = if (isSoloFull) 48 else if (code == "headshot-2v2") 4 else 2,
          entryFee = if (isSoloFull) 30.0 else 50.0,
          prizePool = if (isSoloFull) 1000.0 else 180.0,
          perKillRate = if (isSoloFull) 15.0 else 0.0,
          rank1Prize = if (isSoloFull) 300.0 else 180.0,
          rank2Prize = if (isSoloFull) 150.0 else 0.0,
          rank3Prize = if (isSoloFull) 80.0 else 0.0,
          mapName = map,
          matchRules = "Official EXALOTE EXN Rules: Level 40+ Free Fire MAX account required. No Gloo Wall break scripts or macros."
        )
        if (res.isSuccess) createdCount++
      }
      _isBusy.value = false
      if (createdCount > 0) {
        _statusBanner.value = "Seeded $createdCount official EXALOTE EXN live matches!"
      } else {
        _errorBanner.value = "Could not seed matches. Verify Admin privileges."
      }
    }
  }

  fun publishRoomAsAdmin(
    matchId: String,
    roomId: String,
    roomPassword: String,
    roomPublished: Boolean,
    status: String
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.publishRoomCredentialsAsAdmin(
        matchId = matchId,
        roomId = roomId,
        roomPassword = roomPassword,
        roomPublished = roomPublished,
        status = status
      )
      _isBusy.value = false
      res.onSuccess {
        _statusBanner.value = if (roomPublished) {
          "Room ID ($roomId) & Password published live to all joined players!"
        } else {
          "Match room settings & status ($status) updated."
        }
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Failed to update room credentials."
      }
    }
  }

  fun handleDepositDecisionAsAdmin(depositId: String, approve: Boolean) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.approveOrRejectDepositAsAdmin(depositId, approve)
      _isBusy.value = false
      res.onSuccess {
        _statusBanner.value = if (approve) {
          "Deposit approved! Credited Deposit Balance, Promo Bonus (if any), and First-Deposit Referral Unlock (if applicable)!"
        } else {
          "Deposit request marked as Rejected."
        }
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Failed to process deposit."
      }
    }
  }

  fun handleWithdrawalDecisionAsAdmin(withdrawalId: String, approve: Boolean) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.approveOrRejectWithdrawalAsAdmin(withdrawalId, approve)
      _isBusy.value = false
      res.onSuccess {
        _statusBanner.value = if (approve) {
          "Withdrawal marked Completed!"
        } else {
          "Withdrawal Rejected & escrow automatically refunded to user's Winnings Balance!"
        }
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Failed to process withdrawal."
      }
    }
  }

  fun verifyClaimAndPayoutAsAdmin(
    claimId: String,
    verifiedKills: Int,
    verifiedRank: Int,
    lockMatchPrizes: Boolean
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.verifyClaimAndDistributePrizeAsAdmin(
        claimId = claimId,
        verifiedKills = verifiedKills,
        verifiedRank = verifiedRank,
        lockMatchPrizes = lockMatchPrizes
      )
      _isBusy.value = false
      res.onSuccess { reward ->
        _statusBanner.value =
          "Verified! Credited ₹$reward to player's Winnings Balance" +
            if (lockMatchPrizes) " & locked match prizes_distributed = true." else "."
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Failed to verify claim & distribute prize."
      }
    }
  }

  fun savePromoCodeAsAdmin(
    code: String,
    bonusPercent: Int,
    maxBonus: Double,
    minDeposit: Double,
    isActive: Boolean
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.savePromoCodeAsAdmin(code, bonusPercent, maxBonus, minDeposit, isActive)
      _isBusy.value = false
      res.onSuccess {
        _statusBanner.value = "Promo code ${code.trim().uppercase()} ($bonusPercent% up to ₹$maxBonus) saved live!"
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Failed to save promo code."
      }
    }
  }

  fun grantAdminRole(targetUid: String, targetEmail: String) {
    viewModelScope.launch {
      _isBusy.value = true
      clearBanners()
      val res = repository.grantAdminRoleAsAdmin(targetUid, targetEmail)
      _isBusy.value = false
      res.onSuccess {
        _statusBanner.value = "Granted Admin access to UID: $targetUid"
      }.onFailure { e ->
        _errorBanner.value = e.message ?: "Failed to grant admin role."
      }
    }
  }

  /**
   * High-Thinking AI Engine (gemini-4-argon with ThinkingLevel.HIGH, no maxOutputTokens)
   * Used for complex tournament anti-cheat claim audits, prize distribution checks, and Free Fire MAX strategy.
   */
  fun runHighThinkingAnalysis(userQuery: String) {
    viewModelScope.launch {
      _aiState.value = AiThinkingState.Thinking
      val profile = userProfile.value
      val matches = (matchesState.value as? UiState.Success)?.data ?: emptyList()
      val claims = if (isAdmin.value) adminClaims.value else userClaims.value

      val contextSummary = buildString {
        appendLine("You are the EXALOTE EXN High-Thinking Esports Tournament & Anti-Cheat Analyst.")
        appendLine("Current User: ${profile?.username ?: "Player"} | FF IGN: ${profile?.ffIgn ?: "Unbound"} | FF UID: ${profile?.ffUid ?: "Unbound"}")
        appendLine("Wallet Buckets -> Deposit: ₹${profile?.depositBalance ?: 0.0}, Winnings: ₹${profile?.winningsBalance ?: 0.0}, Bonus: ₹${profile?.bonusBalance ?: 0.0}")
        appendLine("Referral Stats -> Code: ${profile?.referralCode ?: "N/A"}, Total Referrals: ${profile?.totalReferrals ?: 0}, Referral Bonus Earned: ₹${profile?.referralBonusEarned ?: 0.0}")
        appendLine("Active Matches (${matches.size}):")
        matches.take(8).forEach { m ->
          appendLine("- ${m.title} [${m.format} | ${m.mapName}] Fee: ₹${m.entryFee}, Pool: ₹${m.prizePool}, PerKill: ₹${m.perKillRate}, Rank1: ₹${m.rank1Prize}, Slots: ${m.joinedCount}/${m.maxPlayers}, Status: ${m.status}, PrizesLocked: ${m.prizesDistributed}")
        }
        if (claims.isNotEmpty()) {
          appendLine("Recent Result Claims & Hacker Reports (${claims.size}):")
          claims.take(10).forEach { c ->
            appendLine("- Match: ${c.matchTitle} | Player: ${c.ffIgn} (${c.ffUid}) | Claimed Rank #${c.claimedRank}, Kills: ${c.claimedKills} | Status: ${c.status} | Suspect IGN: ${c.reportSuspectIgn.ifBlank { "None" }} | Reason: ${c.reportReason.ifBlank { "None" }}")
          }
        }
        appendLine("\nTask / Query: $userQuery")
      }

      val accumulated = StringBuilder()
      try {
        withTimeout(60_000L) {
          highThinkingModel.generateContentStream(contextSummary).collect { chunk ->
            val piece = chunk.text.orEmpty()
            accumulated.append(piece)
            _aiState.value = AiThinkingState.Streaming(accumulated.toString())
          }
        }
        if (accumulated.isNotBlank()) {
          _aiState.value = AiThinkingState.Success(accumulated.toString())
        } else {
          _aiState.value = AiThinkingState.Error("Model returned an empty response.")
        }
      } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "High-thinking analysis timed out", e)
        _aiState.value = AiThinkingState.Error("High-thinking analysis timed out after 60 seconds.")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "High-thinking analysis failed", e)
        _aiState.value = AiThinkingState.Error(e.localizedMessage ?: "AI analysis failed.")
      }
    }
  }

  private companion object {
    const val TAG = "ExaloteVM"
  }
}
