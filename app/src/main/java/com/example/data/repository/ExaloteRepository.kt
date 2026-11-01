package com.example.data.repository

import com.example.data.model.DEFAULT_PROMO_CODES
import com.example.data.model.DepositRequest
import com.example.data.model.EXALOTE_CATEGORIES
import com.example.data.model.MatchClaim
import com.example.data.model.MatchRegistration
import com.example.data.model.PromoCode
import com.example.data.model.TeammateInput
import com.example.data.model.TournamentMatch
import com.example.data.model.UserProfile
import com.example.data.model.WalletTransaction
import com.example.data.model.WithdrawalRequest
import com.example.data.model.calculateEntryFeeBreakdown
import com.example.data.model.calculatePrizeAmount
import com.example.data.model.calculatePromoBonus
import com.example.data.model.generateReferralCode
import com.example.data.model.isValidFfUid
import com.example.data.model.isValidUpiId
import com.example.data.model.isValidUtr
import com.example.data.model.requiredTeammatesForFormat
import com.example.data.model.roundCurrency
import com.example.data.remote.FirestoreErrorHandler
import com.example.data.remote.OperationType
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date
import kotlin.math.min
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ExaloteRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth
) {
  companion object {
    const val BOOTSTRAPPED_ADMIN_EMAIL = "foodievlog422@gmail.com"
    const val REFERRAL_SIGNUP_BONUS = 5.0
    const val REFERRAL_FIRST_DEPOSIT_BONUS = 10.0
  }

  private fun safeNowTimestamp(): Timestamp =
    Timestamp(Date(System.currentTimeMillis() - 1500L))

  private fun requireCurrentUid(): String =
    auth.currentUser?.uid ?: throw IllegalStateException("Please sign in first.")

  suspend fun ensureUserProfile(
    username: String,
    email: String
  ): Result<UserProfile> = runCatching {
    val uid = requireCurrentUid()
    val userRef = db.collection("users").document(uid)
    val snap = try {
      userRef.get().await()
    } catch (e: Exception) {
      FirestoreErrorHandler.handleException(e, OperationType.GET, "users/$uid", auth)
    }

    val generatedCode = generateReferralCode(uid)
    val ts = safeNowTimestamp()
    val isBootstrappedAdmin = email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)

    val profile = if (snap.exists()) {
      val existing = snap.toUserProfile()
      val finalCode = existing.referralCode.ifBlank { generatedCode }
      if (existing.referralCode.isBlank()) {
        try {
          userRef.update(
            mapOf(
              "referralCode" to finalCode,
              "updatedAt" to ts
            )
          ).await()
        } catch (e: Exception) {
          FirestoreErrorHandler.handleException(e, OperationType.UPDATE, "users/$uid", auth)
        }
      }
      ensureReferralCodeLookup(finalCode, uid, existing.username.ifBlank { username }, ts)
      if (isBootstrappedAdmin) {
        seedDefaultPromoCodesIfNeeded(ts)
      }
      existing.copy(referralCode = finalCode)
    } else {
      val cleanUsername = username.trim().ifBlank {
        email.substringBefore("@").ifBlank { "Survivor" }
      }.take(50)
      val cleanEmail = email.trim().ifBlank { "player@exalote.gg" }.take(110)

      val newProfile = UserProfile(
        userId = uid,
        username = cleanUsername,
        email = cleanEmail,
        ffUid = "",
        ffIgn = "",
        upiId = "",
        depositBalance = 0.0,
        winningsBalance = 0.0,
        bonusBalance = 20.0,
        walletBalance = 20.0,
        referralCode = generatedCode,
        referredBy = "",
        referralRewarded = false,
        totalReferrals = 0,
        referralBonusEarned = 0.0,
        createdAt = ts,
        updatedAt = ts
      )
      try {
        userRef.set(newProfile.toMap()).await()
      } catch (e: Exception) {
        FirestoreErrorHandler.handleException(e, OperationType.CREATE, "users/$uid", auth)
      }
      ensureReferralCodeLookup(generatedCode, uid, cleanUsername, ts)
      if (isBootstrappedAdmin) {
        seedDefaultPromoCodesIfNeeded(ts)
      }
      newProfile
    }
    profile
  }

  private suspend fun ensureReferralCodeLookup(
    code: String,
    uid: String,
    username: String,
    ts: Timestamp
  ) {
    if (code.isBlank()) return
    val ref = db.collection("referral_codes").document(code)
    runCatching {
      val snap = ref.get().await()
      if (!snap.exists()) {
        ref.set(
          mapOf(
            "code" to code,
            "userId" to uid,
            "username" to username.trim().ifBlank { "Player" }.take(50),
            "createdAt" to ts
          )
        ).await()
      }
    }
  }

  private suspend fun seedDefaultPromoCodesIfNeeded(ts: Timestamp) {
    DEFAULT_PROMO_CODES.forEach { promo ->
      runCatching {
        val promoRef = db.collection("promo_codes").document(promo.code)
        val snap = promoRef.get().await()
        if (!snap.exists()) {
          promoRef.set(
            mapOf(
              "code" to promo.code,
              "bonusPercent" to promo.bonusPercent,
              "maxBonus" to promo.maxBonus,
              "minDeposit" to promo.minDeposit,
              "isActive" to promo.isActive,
              "createdAt" to ts,
              "updatedAt" to ts
            )
          ).await()
        }
      }
    }
  }

  fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
    val path = "users/$userId"
    val registration: ListenerRegistration = db.collection("users").document(userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          close(
            runCatching {
              FirestoreErrorHandler.handleException(error, OperationType.GET, path, auth)
            }.exceptionOrNull() ?: error
          )
          return@addSnapshotListener
        }
        trySend(if (snapshot != null && snapshot.exists()) snapshot.toUserProfile() else null)
      }
    awaitClose { registration.remove() }
  }

  fun observeIsAdmin(userId: String, email: String?): Flow<Boolean> = callbackFlow {
    val isBootstrapped = email?.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) == true
    if (isBootstrapped) {
      trySend(true)
    }
    val registration = db.collection("admins").document(userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          trySend(isBootstrapped)
          return@addSnapshotListener
        }
        trySend(isBootstrapped || (snapshot != null && snapshot.exists()))
      }
    awaitClose { registration.remove() }
  }

  fun observePromoCodes(): Flow<List<PromoCode>> = callbackFlow {
    val registration = db.collection("promo_codes")
      .whereEqualTo("isActive", true)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          trySend(DEFAULT_PROMO_CODES)
          return@addSnapshotListener
        }
        val remote = snapshot?.documents?.map { it.toPromoCode() } ?: emptyList()
        val merged = linkedMapOf<String, PromoCode>()
        DEFAULT_PROMO_CODES.forEach { merged[it.code] = it }
        remote.forEach { merged[it.code] = it }
        trySend(merged.values.filter { it.isActive })
      }
    awaitClose { registration.remove() }
  }

  suspend fun savePromoCodeAsAdmin(
    code: String,
    bonusPercent: Int,
    maxBonus: Double,
    minDeposit: Double,
    isActive: Boolean
  ): Result<Unit> = runCatching {
    val cleanCode = code.trim().uppercase()
    require(cleanCode.matches(Regex("^[A-Z0-9_\\-]{3,30}$"))) {
      "Promo code must be 3-30 uppercase alphanumeric characters (e.g., WELCOME20)."
    }
    require(bonusPercent in 1..100) { "Bonus percent must be between 1% and 100%." }
    require(maxBonus in 1.0..10000.0) { "Max bonus must be between ₹1 and ₹10,000." }
    require(minDeposit in 10.0..100000.0) { "Min deposit must be at least ₹10." }

    val ts = safeNowTimestamp()
    val promoRef = db.collection("promo_codes").document(cleanCode)
    val existing = promoRef.get().await()
    val createdAt = if (existing.exists()) existing.getTimestamp("createdAt") ?: ts else ts
    val payload = mapOf(
      "code" to cleanCode,
      "bonusPercent" to bonusPercent,
      "maxBonus" to roundCurrency(maxBonus),
      "minDeposit" to roundCurrency(minDeposit),
      "isActive" to isActive,
      "createdAt" to createdAt,
      "updatedAt" to ts
    )
    try {
      promoRef.set(payload).await()
    } catch (e: Exception) {
      FirestoreErrorHandler.handleException(e, OperationType.WRITE, "promo_codes/$cleanCode", auth)
    }
  }

  /**
   * Refer & Earn ("Anti-Fake Account Logic"):
   * - Friend enters a valid referralCode -> gets ₹5 immediately in `bonusBalance` (non-withdrawable).
   * - Sets `referredBy = referrerUid` and `referralRewarded = false`.
   * - The referrer receives ₹10 in `bonusBalance` ONLY when this user's first deposit is Approved by Admin!
   */
  suspend fun applyReferralCode(rawCode: String): Result<Double> = runCatching {
    val uid = requireCurrentUid()
    val cleanCode = rawCode.trim().uppercase()
    require(cleanCode.matches(Regex("^[A-Z0-9]{4,20}$"))) {
      "Enter a valid Referral Code (e.g., EXN1A2B3)."
    }

    val lookupSnap = try {
      db.collection("referral_codes").document(cleanCode).get().await()
    } catch (e: Exception) {
      FirestoreErrorHandler.handleException(e, OperationType.GET, "referral_codes/$cleanCode", auth)
    }

    require(lookupSnap.exists()) {
      "Referral code '$cleanCode' was not found. Check your friend's code."
    }

    val referrerUid = lookupSnap.getString("userId").orEmpty()
    require(referrerUid.isNotBlank() && referrerUid != uid) {
      "You cannot apply your own referral code."
    }

    val userRef = db.collection("users").document(uid)
    val txRef = db.collection("transactions").document()
    val ts = safeNowTimestamp()

    try {
      db.runTransaction { tx ->
        val userSnap = tx.get(userRef)
        if (!userSnap.exists()) throw IllegalStateException("User profile not found.")
        val user = userSnap.toUserProfile()

        if (user.referredBy.isNotBlank()) {
          throw IllegalStateException("You have already claimed a referral code on this account.")
        }
        if (user.referralCode.equals(cleanCode, ignoreCase = true)) {
          throw IllegalStateException("You cannot use your own referral code.")
        }

        val newBonus = roundCurrency(user.bonusBalance + REFERRAL_SIGNUP_BONUS)
        val newWallet = roundCurrency(user.walletBalance + REFERRAL_SIGNUP_BONUS)

        tx.update(
          userRef,
          mapOf(
            "referredBy" to referrerUid,
            "bonusBalance" to newBonus,
            "walletBalance" to newWallet,
            "updatedAt" to ts
          )
        )

        tx.set(
          txRef,
          mapOf(
            "userId" to uid,
            "type" to "bonus",
            "amount" to REFERRAL_SIGNUP_BONUS,
            "description" to "Refer & Earn Signup Bonus (Code: $cleanCode)",
            "createdAt" to ts
          )
        )
        REFERRAL_SIGNUP_BONUS
      }.await()
    } catch (e: Exception) {
      if (e is IllegalStateException || e is IllegalArgumentException) throw e
      FirestoreErrorHandler.handleException(e, OperationType.UPDATE, "users/$uid", auth)
    }
  }

  suspend fun bindFreeFireProfile(
    username: String,
    ffUid: String,
    ffIgn: String,
    upiId: String
  ): Result<Unit> = runCatching {
    val uid = requireCurrentUid()
    val cleanUid = ffUid.trim()
    val cleanIgn = ffIgn.trim()
    val cleanUpi = upiId.trim()
    require(isValidFfUid(cleanUid)) {
      "Free Fire UID must be an 8 to 12 digit numeric ID."
    }
    require(cleanIgn.length in 2..60) {
      "Free Fire IGN must be between 2 and 60 characters."
    }
    if (cleanUpi.isNotEmpty()) {
      require(isValidUpiId(cleanUpi)) {
        "Invalid UPI ID format (expected example: player@okaxis)."
      }
    }

    val updates = mapOf(
      "username" to username.trim().ifBlank { "Survivor" }.take(50),
      "ffUid" to cleanUid,
      "ffIgn" to cleanIgn,
      "upiId" to cleanUpi,
      "updatedAt" to safeNowTimestamp()
    )
    try {
      db.collection("users").document(uid).update(updates).await()
    } catch (e: Exception) {
      FirestoreErrorHandler.handleException(e, OperationType.UPDATE, "users/$uid", auth)
    }
  }

  fun observePublishedMatches(): Flow<List<TournamentMatch>> = callbackFlow {
    val registration = db.collection("matches")
      .whereEqualTo("published", true)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          close(
            runCatching {
              FirestoreErrorHandler.handleException(error, OperationType.LIST, "matches", auth)
            }.exceptionOrNull() ?: error
          )
          return@addSnapshotListener
        }
        val list = snapshot?.documents?.map { it.toTournamentMatch() }
          ?.sortedBy { it.startTimeEpochMs } ?: emptyList()
        trySend(list)
      }
    awaitClose { registration.remove() }
  }

  fun observeUserRegistrations(userId: String): Flow<List<MatchRegistration>> = callbackFlow {
    val registration = db.collection("registrations")
      .whereEqualTo("userId", userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          trySend(emptyList())
          return@addSnapshotListener
        }
        val list = snapshot?.documents?.map { it.toMatchRegistration() }
          ?.sortedByDescending { it.createdAt } ?: emptyList()
        trySend(list)
      }
    awaitClose { registration.remove() }
  }

  fun observeAllRegistrationsForAdmin(): Flow<List<MatchRegistration>> = callbackFlow {
    val registration = db.collection("registrations")
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          trySend(emptyList())
          return@addSnapshotListener
        }
        val list = snapshot?.documents?.map { it.toMatchRegistration() }
          ?.sortedBy { it.slotNumber } ?: emptyList()
        trySend(list)
      }
    awaitClose { registration.remove() }
  }

  /**
   * Atomic Match Join & 3-Bucket Fee Deduction Order:
   * 1. First, calculate `usable_bonus = MIN(bonus_balance, ROUND(entry_fee * 0.10, 2))` and deduct up to 10% from `bonus_balance`.
   * 2. Second, deduct remaining fee from `deposit_balance`.
   * 3. Third, deduct any remaining shortfall from `winnings_balance`.
   * 4. Validate before deducting that `(usable_bonus + deposit_balance + winnings_balance) >= entry_fee`
   *    (do not count unusable bonus balance toward sufficiency).
   * 5. Keep master `wallet_balance` synced as `(deposit_balance + winnings_balance + bonus_balance)` and roll back atomically if slots are full or any check fails.
   */
  suspend fun joinMatchAtomically(
    matchId: String,
    teammates: List<TeammateInput>
  ): Result<MatchRegistration> = runCatching {
    val uid = requireCurrentUid()
    val userRef = db.collection("users").document(uid)
    val matchRef = db.collection("matches").document(matchId)
    val regId = "${matchId}_$uid"
    val regRef = db.collection("registrations").document(regId)
    val txRef = db.collection("transactions").document()
    val ts = safeNowTimestamp()

    try {
      db.runTransaction { transaction ->
        val userSnap = transaction.get(userRef)
        val matchSnap = transaction.get(matchRef)
        val existingRegSnap = transaction.get(regRef)

        if (!userSnap.exists()) throw IllegalStateException("User profile not found.")
        if (!matchSnap.exists()) throw IllegalStateException("Match not found.")
        if (existingRegSnap.exists()) {
          throw IllegalStateException("You have already joined this tournament.")
        }

        val user = userSnap.toUserProfile()
        val match = matchSnap.toTournamentMatch()

        if (!user.hasValidFreeFireBinding) {
          throw IllegalStateException("Bind your 8–12 digit Free Fire UID & IGN in Profile before joining.")
        }
        if (match.status != "Upcoming") {
          throw IllegalStateException("Registration closed for this match (Status: ${match.status}).")
        }
        if (match.joinedCount >= match.maxPlayers) {
          throw IllegalStateException("Match is full (${match.maxPlayers}/${match.maxPlayers} slots).")
        }

        val requiredTeammates = requiredTeammatesForFormat(match.format)
        if (teammates.size != requiredTeammates) {
          throw IllegalStateException("${match.format} format requires $requiredTeammates teammate(s).")
        }

        teammates.forEachIndexed { idx, mate ->
          if (!isValidFfUid(mate.ffUid)) {
            throw IllegalStateException("Teammate #${idx + 1} FF UID must be 8–12 digits.")
          }
          if (mate.ffIgn.trim().length !in 2..60) {
            throw IllegalStateException("Teammate #${idx + 1} FF IGN is required.")
          }
        }

        val allTeamUids = buildList {
          add(user.ffUid.trim())
          teammates.forEach { add(it.ffUid.trim()) }
        }
        if (allTeamUids.distinct().size != allTeamUids.size) {
          throw IllegalStateException("Duplicate Free Fire UIDs entered in your squad.")
        }

        val duplicateInMatch = allTeamUids.firstOrNull { it in match.registeredUids }
        if (duplicateInMatch != null) {
          throw IllegalStateException("Free Fire UID $duplicateInMatch is already registered in this match!")
        }

        // Exact 3-Bucket Sufficiency Check & Deduction Math
        val fee = roundCurrency(match.entryFee.coerceAtLeast(0.0))
        val usableBonus = roundCurrency(min(user.bonusBalance.coerceAtLeast(0.0), roundCurrency(fee * 0.10)))
        val totalUsable = roundCurrency(usableBonus + user.depositBalance + user.winningsBalance)
        if (totalUsable + 0.0001 < fee) {
          throw IllegalStateException(
            "Insufficient usable balance! Need ₹$fee, Usable: ₹$totalUsable (Max 10% Bonus Usable: ₹$usableBonus, Deposit: ₹${user.depositBalance}, Winnings: ₹${user.winningsBalance})."
          )
        }

        val breakdown = calculateEntryFeeBreakdown(
          entryFee = fee,
          bonusBalance = user.bonusBalance,
          depositBalance = user.depositBalance,
          winningsBalance = user.winningsBalance
        )
        if (!breakdown.canAfford) {
          throw IllegalStateException("Insufficient usable balance across Bonus (10%), Deposit, and Winnings buckets.")
        }

        val newBonus = roundCurrency(user.bonusBalance - breakdown.bonusUsed)
        val newDeposit = roundCurrency(user.depositBalance - breakdown.depositUsed)
        val newWinnings = roundCurrency(user.winningsBalance - breakdown.winningsUsed)
        val newWallet = roundCurrency(newDeposit + newWinnings + newBonus)

        val assignedSlot = match.joinedCount + 1
        val updatedRegisteredUids = (match.registeredUids + allTeamUids).take(100)
        val teamSummaryList = teammates.map { "${it.ffIgn.trim()} (${it.ffUid.trim()})" }

        val regObj = MatchRegistration(
          id = regId,
          matchId = match.id,
          matchTitle = match.title,
          userId = uid,
          username = user.username,
          ffUid = user.ffUid.trim(),
          ffIgn = user.ffIgn.trim(),
          status = "Approved",
          feeStatus = "paid",
          entryFeePaid = breakdown.entryFee,
          bonusUsed = breakdown.bonusUsed,
          depositUsed = breakdown.depositUsed,
          winningsUsed = breakdown.winningsUsed,
          slotNumber = assignedSlot,
          teamMembers = teamSummaryList,
          allUids = allTeamUids,
          createdAt = ts,
          updatedAt = ts
        )

        transaction.update(
          userRef,
          mapOf(
            "bonusBalance" to newBonus,
            "depositBalance" to newDeposit,
            "winningsBalance" to newWinnings,
            "walletBalance" to newWallet,
            "updatedAt" to ts
          )
        )

        transaction.update(
          matchRef,
          mapOf(
            "joinedCount" to assignedSlot,
            "registeredUids" to updatedRegisteredUids,
            "updatedAt" to ts
          )
        )

        transaction.set(regRef, regObj.toMap())

        transaction.set(
          txRef,
          mapOf(
            "userId" to uid,
            "type" to "entry_fee",
            "amount" to breakdown.entryFee,
            "description" to "Joined ${match.title} (Slot #$assignedSlot | Bonus: ₹${breakdown.bonusUsed}, Dep: ₹${breakdown.depositUsed}, Win: ₹${breakdown.winningsUsed})",
            "createdAt" to ts
          )
        )

        regObj
      }.await()
    } catch (e: Exception) {
      if (e is IllegalStateException || e is IllegalArgumentException) throw e
      FirestoreErrorHandler.handleException(e, OperationType.WRITE, "registrations/$regId", auth)
    }
  }

  fun observeUserClaims(userId: String): Flow<List<MatchClaim>> = callbackFlow {
    val registration = db.collection("claims")
      .whereEqualTo("userId", userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          trySend(emptyList())
          return@addSnapshotListener
        }
        val list = snapshot?.documents?.map { it.toMatchClaim() }
          ?.sortedByDescending { it.createdAt } ?: emptyList()
        trySend(list)
      }
    awaitClose { registration.remove() }
  }

  fun observeAllClaimsForAdmin(): Flow<List<MatchClaim>> = callbackFlow {
    val registration = db.collection("claims")
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          trySend(emptyList())
          return@addSnapshotListener
        }
        val list = snapshot?.documents?.map { it.toMatchClaim() }
          ?.sortedByDescending { it.createdAt } ?: emptyList()
        trySend(list)
      }
    awaitClose { registration.remove() }
  }

  suspend fun submitResultClaim(
    matchId: String,
    matchTitle: String,
    claimedKills: Int,
    claimedRank: Int,
    screenshotBase64: String,
    screenshotMime: String,
    reportSuspectIgn: String,
    reportReason: String
  ): Result<Unit> = runCatching {
    val uid = requireCurrentUid()
    require(claimedKills in 0..100) { "Claimed kills must be between 0 and 100." }
    require(claimedRank in 1..100) { "Claimed rank must be between 1 and 100." }
    require(screenshotBase64.length <= 145_000) { "Screenshot preview is too large." }

    val userSnap = db.collection("users").document(uid).get().await()
    val user = userSnap.toUserProfile()
    val claimId = "${matchId}_$uid"
    val ts = safeNowTimestamp()

    val claim = MatchClaim(
      id = claimId,
      matchId = matchId,
      matchTitle = matchTitle.take(140),
      userId = uid,
      username = user.username.ifBlank { "Survivor" },
      ffUid = user.ffUid.ifBlank { "12345678" },
      ffIgn = user.ffIgn.ifBlank { "Player" },
      claimedKills = claimedKills,
      claimedRank = claimedRank,
      screenshotBase64 = screenshotBase64,
      screenshotMime = screenshotMime.ifBlank { "image/jpeg" }.take(35),
      reportSuspectIgn = reportSuspectIgn.trim().take(90),
      reportReason = reportReason.trim().take(480),
      status = "pending",
      awardedAmount = 0.0,
      createdAt = ts,
      updatedAt = ts
    )
    try {
      db.collection("claims").document(claimId).set(claim.toMap()).await()
    } catch (e: Exception) {
      FirestoreErrorHandler.handleException(e, OperationType.CREATE, "claims/$claimId", auth)
    }
  }

  fun observeUserDeposits(userId: String): Flow<List<DepositRequest>> = callbackFlow {
    val registration = db.collection("deposits")
      .whereEqualTo("userId", userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          trySend(emptyList())
          return@addSnapshotListener
        }
        val list = snapshot?.documents?.map { it.toDepositRequest() }
          ?.sortedByDescending { it.createdAt } ?: emptyList()
        trySend(list)
      }
    awaitClose { registration.remove() }
  }

  fun observeAllDepositsForAdmin(): Flow<List<DepositRequest>> = callbackFlow {
    val registration = db.collection("deposits")
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          trySend(emptyList())
          return@addSnapshotListener
        }
        val list = snapshot?.documents?.map { it.toDepositRequest() }
          ?.sortedByDescending { it.createdAt } ?: emptyList()
        trySend(list)
      }
    awaitClose { registration.remove() }
  }

  /**
   * Submits a manual UPI deposit with 12-digit UTR and optional Promo Code (e.g., WELCOME20).
   * - Validates promo code active status, `minDeposit`, and one-time usage per user.
   * - Stores `promoCode` and `bonusAmount` on the `/deposits/utr_$cleanUtr` document.
   */
  suspend fun submitUpiDeposit(
    amount: Double,
    utrNumber: String,
    promoCode: String = ""
  ): Result<Double> = runCatching {
    val uid = requireCurrentUid()
    val cleanUtr = utrNumber.trim()
    val roundedAmount = roundCurrency(amount)
    require(roundedAmount in 10.0..100000.0) {
      "Deposit amount must be between ₹10 and ₹1,00,000."
    }
    require(isValidUtr(cleanUtr)) {
      "UTR Reference must be strictly 12 numeric digits."
    }

    val cleanPromo = promoCode.trim().uppercase()
    var calculatedBonus = 0.0

    if (cleanPromo.isNotBlank()) {
      val promoSnap = runCatching {
        db.collection("promo_codes").document(cleanPromo).get().await()
      }.getOrNull()

      val promo = if (promoSnap != null && promoSnap.exists()) {
        promoSnap.toPromoCode()
      } else {
        DEFAULT_PROMO_CODES.firstOrNull { it.code.equals(cleanPromo, ignoreCase = true) }
      }

      require(promo != null && promo.isActive) {
        "Promo code '$cleanPromo' is invalid or expired."
      }
      require(roundedAmount >= promo.minDeposit) {
        "Promo code '$cleanPromo' requires a minimum deposit of ₹${promo.minDeposit.toInt()}."
      }

      val userDepositsSnap = try {
        db.collection("deposits")
          .whereEqualTo("userId", uid)
          .get()
          .await()
      } catch (e: Exception) {
        FirestoreErrorHandler.handleException(e, OperationType.LIST, "deposits", auth)
      }

      val alreadyUsed = userDepositsSnap.documents
        .map { it.toDepositRequest() }
        .any {
          it.promoCode.equals(cleanPromo, ignoreCase = true) &&
            (it.status == "Approved" || it.status == "Pending")
        }
      require(!alreadyUsed) {
        "Promo code '$cleanPromo' has already been used on your account."
      }

      calculatedBonus = calculatePromoBonus(roundedAmount, promo)
    }

    val userSnap = db.collection("users").document(uid).get().await()
    val username = if (userSnap.exists()) {
      userSnap.getString("username").orEmpty().ifBlank { "Survivor" }
    } else {
      "Survivor"
    }

    val depositId = "utr_$cleanUtr"
    val depRef = db.collection("deposits").document(depositId)
    val ts = safeNowTimestamp()

    val dep = DepositRequest(
      id = depositId,
      userId = uid,
      username = username,
      amount = roundedAmount,
      utrNumber = cleanUtr,
      promoCode = cleanPromo,
      bonusAmount = calculatedBonus,
      status = "Pending",
      createdAt = ts,
      updatedAt = ts
    )

    try {
      depRef.set(dep.toMap()).await()
    } catch (e: Exception) {
      FirestoreErrorHandler.handleException(e, OperationType.CREATE, "deposits/$depositId", auth)
    }
    calculatedBonus
  }

  fun observeUserWithdrawals(userId: String): Flow<List<WithdrawalRequest>> = callbackFlow {
    val registration = db.collection("withdrawals")
      .whereEqualTo("userId", userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          trySend(emptyList())
          return@addSnapshotListener
        }
        val list = snapshot?.documents?.map { it.toWithdrawalRequest() }
          ?.sortedByDescending { it.createdAt } ?: emptyList()
        trySend(list)
      }
    awaitClose { registration.remove() }
  }

  fun observeAllWithdrawalsForAdmin(): Flow<List<WithdrawalRequest>> = callbackFlow {
    val registration = db.collection("withdrawals")
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          trySend(emptyList())
          return@addSnapshotListener
        }
        val list = snapshot?.documents?.map { it.toWithdrawalRequest() }
          ?.sortedByDescending { it.createdAt } ?: emptyList()
        trySend(list)
      }
    awaitClose { registration.remove() }
  }

  suspend fun requestWithdrawalWithEscrow(
    amount: Double,
    upiId: String
  ): Result<Unit> = runCatching {
    val uid = requireCurrentUid()
    val cleanUpi = upiId.trim()
    val roundedAmount = roundCurrency(amount)
    require(roundedAmount >= 50.0) { "Minimum withdrawal amount is ₹50." }
    require(isValidUpiId(cleanUpi)) { "Please enter a valid UPI ID (e.g., player@okaxis)." }

    val userRef = db.collection("users").document(uid)
    val wdRef = db.collection("withdrawals").document()
    val txRef = db.collection("transactions").document()
    val ts = safeNowTimestamp()

    try {
      db.runTransaction { transaction ->
        val userSnap = transaction.get(userRef)
        if (!userSnap.exists()) throw IllegalStateException("User profile not found.")
        val user = userSnap.toUserProfile()

        if (user.winningsBalance + 0.0001 < roundedAmount) {
          throw IllegalStateException(
            "Withdrawals are allowed strictly from Winnings Balance (Available: ₹${user.winningsBalance}). Deposit & Bonus balances are non-withdrawable."
          )
        }

        val newWinnings = roundCurrency(user.winningsBalance - roundedAmount)
        val newWallet = roundCurrency(user.depositBalance + newWinnings + user.bonusBalance)

        transaction.update(
          userRef,
          mapOf(
            "winningsBalance" to newWinnings,
            "walletBalance" to newWallet,
            "updatedAt" to ts
          )
        )

        val wd = WithdrawalRequest(
          id = wdRef.id,
          userId = uid,
          username = user.username.ifBlank { "Survivor" },
          amount = roundedAmount,
          upiId = cleanUpi,
          status = "Pending",
          createdAt = ts,
          updatedAt = ts
        )
        transaction.set(wdRef, wd.toMap())

        transaction.set(
          txRef,
          mapOf(
            "userId" to uid,
            "type" to "withdrawal",
            "amount" to roundedAmount,
            "description" to "Withdrawal escrow to UPI: $cleanUpi",
            "createdAt" to ts
          )
        )
      }.await()
    } catch (e: Exception) {
      if (e is IllegalStateException || e is IllegalArgumentException) throw e
      FirestoreErrorHandler.handleException(e, OperationType.WRITE, "withdrawals/${wdRef.id}", auth)
    }
  }

  fun observeUserTransactions(userId: String): Flow<List<WalletTransaction>> = callbackFlow {
    val registration = db.collection("transactions")
      .whereEqualTo("userId", userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          trySend(emptyList())
          return@addSnapshotListener
        }
        val list = snapshot?.documents?.map { it.toWalletTransaction() }
          ?.sortedByDescending { it.createdAt } ?: emptyList()
        trySend(list)
      }
    awaitClose { registration.remove() }
  }

  // --- ADMIN OPERATIONS ---

  suspend fun createMatchAsAdmin(
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
  ): Result<String> = runCatching {
    val uid = requireCurrentUid()
    val category = EXALOTE_CATEGORIES.find { it.code == categoryCode } ?: EXALOTE_CATEGORIES[2]
    val matchRef = db.collection("matches").document()
    val ts = safeNowTimestamp()

    val match = TournamentMatch(
      id = matchRef.id,
      title = title.trim().ifBlank { "EXN ${category.name}" }.take(140),
      categoryCode = category.code,
      categoryName = category.name,
      format = category.format,
      session = if (session == "morning") "morning" else "evening",
      matchDate = matchDate.trim().ifBlank { "2026-11-01" },
      startTime = startTime.trim().ifBlank { "18:00" },
      startTimeEpochMs = startTimeEpochMs.coerceAtLeast(1000L),
      maxPlayers = maxPlayers.coerceIn(2, 100),
      joinedCount = 0,
      registeredUids = emptyList(),
      entryFee = roundCurrency(entryFee),
      prizePool = roundCurrency(prizePool),
      perKillRate = roundCurrency(perKillRate),
      rank1Prize = roundCurrency(rank1Prize),
      rank2Prize = roundCurrency(rank2Prize),
      rank3Prize = roundCurrency(rank3Prize),
      status = "Upcoming",
      roomId = "",
      roomPassword = "",
      roomPublished = false,
      mapName = mapName.trim().ifBlank { "Bermuda" }.take(45),
      matchRules = matchRules.trim().take(950),
      prizesDistributed = false,
      published = true,
      createdBy = uid,
      createdAt = ts,
      updatedAt = ts
    )

    try {
      matchRef.set(match.toMap()).await()
      matchRef.id
    } catch (e: Exception) {
      FirestoreErrorHandler.handleException(e, OperationType.CREATE, "matches/${matchRef.id}", auth)
    }
  }

  suspend fun publishRoomCredentialsAsAdmin(
    matchId: String,
    roomId: String,
    roomPassword: String,
    roomPublished: Boolean,
    status: String
  ): Result<Unit> = runCatching {
    val updates = mapOf(
      "roomId" to roomId.trim().take(45),
      "roomPassword" to roomPassword.trim().take(45),
      "roomPublished" to roomPublished,
      "status" to status,
      "updatedAt" to safeNowTimestamp()
    )
    try {
      db.collection("matches").document(matchId).update(updates).await()
    } catch (e: Exception) {
      FirestoreErrorHandler.handleException(e, OperationType.UPDATE, "matches/$matchId", auth)
    }
  }

  /**
   * Admin Deposit Approval / Rejection:
   * - Credits base `deposit.amount` to `depositBalance`.
   * - Credits `deposit.bonusAmount` (from Promo Code like WELCOME20) strictly to `bonusBalance`.
   * - Anti-Cheat First-Deposit Referral Unlock:
   *   If `user.referredBy.isNotBlank() && !user.referralRewarded`, atomically credits ₹10 to the referrer's
   *   `bonusBalance`, increments referrer's `totalReferrals`, adds ₹10 to `referralBonusEarned`,
   *   records a `"bonus"` transaction for the referrer, and sets `referralRewarded = true` on the user.
   */
  suspend fun approveOrRejectDepositAsAdmin(
    depositId: String,
    approve: Boolean
  ): Result<Unit> = runCatching {
    val depRef = db.collection("deposits").document(depositId)
    val depositTxRef = db.collection("transactions").document()
    val promoTxRef = db.collection("transactions").document()
    val referralTxRef = db.collection("transactions").document()
    val ts = safeNowTimestamp()

    try {
      db.runTransaction { transaction ->
        // 1. ALL READS FIRST
        val depSnap = transaction.get(depRef)
        if (!depSnap.exists()) throw IllegalStateException("Deposit not found.")
        val dep = depSnap.toDepositRequest()
        if (dep.status != "Pending") {
          throw IllegalStateException("Deposit is already ${dep.status}.")
        }

        val userRef = db.collection("users").document(dep.userId)
        val userSnap = transaction.get(userRef)
        if (!userSnap.exists()) throw IllegalStateException("Depositor user profile not found.")
        val user = userSnap.toUserProfile()

        val shouldUnlockReferral = approve &&
          user.referredBy.isNotBlank() &&
          user.referredBy != user.userId &&
          !user.referralRewarded

        val referrerRef = if (shouldUnlockReferral) {
          db.collection("users").document(user.referredBy)
        } else {
          null
        }
        val referrerSnap = if (referrerRef != null) {
          transaction.get(referrerRef)
        } else {
          null
        }

        // 2. ALL WRITES AFTER READS
        if (approve) {
          val newDeposit = roundCurrency(user.depositBalance + dep.amount)
          val promoBonus = roundCurrency(dep.bonusAmount.coerceAtLeast(0.0))
          val newBonus = roundCurrency(user.bonusBalance + promoBonus)
          val newWallet = roundCurrency(newDeposit + user.winningsBalance + newBonus)

          val userUpdates = mutableMapOf<String, Any>(
            "depositBalance" to newDeposit,
            "bonusBalance" to newBonus,
            "walletBalance" to newWallet,
            "updatedAt" to ts
          )

          if (shouldUnlockReferral && referrerRef != null && referrerSnap != null && referrerSnap.exists()) {
            userUpdates["referralRewarded"] = true

            val referrer = referrerSnap.toUserProfile()
            val refNewBonus = roundCurrency(referrer.bonusBalance + REFERRAL_FIRST_DEPOSIT_BONUS)
            val refNewWallet = roundCurrency(referrer.depositBalance + referrer.winningsBalance + refNewBonus)
            val refNewTotalReferrals = referrer.totalReferrals + 1
            val refNewBonusEarned = roundCurrency(referrer.referralBonusEarned + REFERRAL_FIRST_DEPOSIT_BONUS)

            transaction.update(
              referrerRef,
              mapOf(
                "bonusBalance" to refNewBonus,
                "walletBalance" to refNewWallet,
                "totalReferrals" to refNewTotalReferrals,
                "referralBonusEarned" to refNewBonusEarned,
                "updatedAt" to ts
              )
            )

            transaction.set(
              referralTxRef,
              mapOf(
                "userId" to referrer.userId,
                "type" to "bonus",
                "amount" to REFERRAL_FIRST_DEPOSIT_BONUS,
                "description" to "Refer & Earn First-Deposit Unlock: ${user.username} (+₹10 Bonus)",
                "createdAt" to ts
              )
            )
          }

          transaction.update(userRef, userUpdates)
          transaction.update(depRef, mapOf("status" to "Approved", "updatedAt" to ts))

          transaction.set(
            depositTxRef,
            mapOf(
              "userId" to dep.userId,
              "type" to "deposit",
              "amount" to dep.amount,
              "description" to "UPI Deposit Approved (UTR: ${dep.utrNumber})",
              "createdAt" to ts
            )
          )

          if (promoBonus > 0.0) {
            transaction.set(
              promoTxRef,
              mapOf(
                "userId" to dep.userId,
                "type" to "bonus",
                "amount" to promoBonus,
                "description" to "Promo Code ${dep.promoCode.ifBlank { "BONUS" }} Extra Bonus (+₹$promoBonus)",
                "createdAt" to ts
              )
            )
          }
        } else {
          transaction.update(depRef, mapOf("status" to "Rejected", "updatedAt" to ts))
        }
      }.await()
    } catch (e: Exception) {
      if (e is IllegalStateException) throw e
      FirestoreErrorHandler.handleException(e, OperationType.UPDATE, "deposits/$depositId", auth)
    }
  }

  suspend fun approveOrRejectWithdrawalAsAdmin(
    withdrawalId: String,
    approve: Boolean
  ): Result<Unit> = runCatching {
    val wdRef = db.collection("withdrawals").document(withdrawalId)
    val txRef = db.collection("transactions").document()
    val ts = safeNowTimestamp()

    try {
      db.runTransaction { transaction ->
        val wdSnap = transaction.get(wdRef)
        if (!wdSnap.exists()) throw IllegalStateException("Withdrawal request not found.")
        val wd = wdSnap.toWithdrawalRequest()
        if (wd.status != "Pending") {
          throw IllegalStateException("Withdrawal is already ${wd.status}.")
        }

        if (approve) {
          transaction.update(wdRef, mapOf("status" to "Completed", "updatedAt" to ts))
        } else {
          val userRef = db.collection("users").document(wd.userId)
          val userSnap = transaction.get(userRef)
          if (!userSnap.exists()) throw IllegalStateException("User not found.")
          val user = userSnap.toUserProfile()

          val refundedWinnings = roundCurrency(user.winningsBalance + wd.amount)
          val newWallet = roundCurrency(user.depositBalance + refundedWinnings + user.bonusBalance)

          transaction.update(
            userRef,
            mapOf(
              "winningsBalance" to refundedWinnings,
              "walletBalance" to newWallet,
              "updatedAt" to ts
            )
          )
          transaction.update(wdRef, mapOf("status" to "Rejected", "updatedAt" to ts))
          transaction.set(
            txRef,
            mapOf(
              "userId" to wd.userId,
              "type" to "refund",
              "amount" to wd.amount,
              "description" to "Withdrawal Rejected — Refunded ₹${wd.amount} to Winnings Balance",
              "createdAt" to ts
            )
          )
        }
      }.await()
    } catch (e: Exception) {
      if (e is IllegalStateException) throw e
      FirestoreErrorHandler.handleException(e, OperationType.UPDATE, "withdrawals/$withdrawalId", auth)
    }
  }

  suspend fun verifyClaimAndDistributePrizeAsAdmin(
    claimId: String,
    verifiedKills: Int,
    verifiedRank: Int,
    lockMatchPrizes: Boolean
  ): Result<Double> = runCatching {
    val claimRef = db.collection("claims").document(claimId)
    val txRef = db.collection("transactions").document()
    val ts = safeNowTimestamp()

    try {
      db.runTransaction { transaction ->
        val claimSnap = transaction.get(claimRef)
        if (!claimSnap.exists()) throw IllegalStateException("Result claim not found.")
        val claim = claimSnap.toMatchClaim()
        if (claim.status != "pending") {
          throw IllegalStateException("This claim is already ${claim.status}! Duplicate payout blocked.")
        }

        val matchRef = db.collection("matches").document(claim.matchId)
        val matchSnap = transaction.get(matchRef)
        if (!matchSnap.exists()) throw IllegalStateException("Match not found.")
        val match = matchSnap.toTournamentMatch()

        val userRef = db.collection("users").document(claim.userId)
        val userSnap = transaction.get(userRef)
        if (!userSnap.exists()) throw IllegalStateException("Player user profile not found.")
        val user = userSnap.toUserProfile()

        val reward = calculatePrizeAmount(verifiedKills, verifiedRank, match)
        val newWinnings = roundCurrency(user.winningsBalance + reward)
        val newWallet = roundCurrency(user.depositBalance + newWinnings + user.bonusBalance)

        transaction.update(
          claimRef,
          mapOf(
            "claimedKills" to verifiedKills,
            "claimedRank" to verifiedRank,
            "status" to "verified",
            "awardedAmount" to reward,
            "updatedAt" to ts
          )
        )

        if (reward > 0.0) {
          transaction.update(
            userRef,
            mapOf(
              "winningsBalance" to newWinnings,
              "walletBalance" to newWallet,
              "updatedAt" to ts
            )
          )
          transaction.set(
            txRef,
            mapOf(
              "userId" to claim.userId,
              "type" to "prize_payout",
              "amount" to reward,
              "description" to "Won ₹$reward in ${match.title} (Rank #$verifiedRank, Kills: $verifiedKills)",
              "createdAt" to ts
            )
          )
        }

        if (lockMatchPrizes) {
          transaction.update(
            matchRef,
            mapOf(
              "prizesDistributed" to true,
              "status" to "Completed",
              "updatedAt" to ts
            )
          )
        }

        reward
      }.await()
    } catch (e: Exception) {
      if (e is IllegalStateException) throw e
      FirestoreErrorHandler.handleException(e, OperationType.UPDATE, "claims/$claimId", auth)
    }
  }

  suspend fun grantAdminRoleAsAdmin(targetUid: String, targetEmail: String): Result<Unit> =
    runCatching {
      val cleanUid = targetUid.trim()
      require(cleanUid.isNotEmpty()) { "Target UID cannot be empty." }
      val ts = safeNowTimestamp()
      try {
        db.collection("admins").document(cleanUid).set(
          mapOf(
            "uid" to cleanUid,
            "email" to targetEmail.trim().ifBlank { "admin@exalote.gg" },
            "createdAt" to ts
          )
        ).await()
      } catch (e: Exception) {
        FirestoreErrorHandler.handleException(e, OperationType.CREATE, "admins/$cleanUid", auth)
      }
    }
}

// --- MAPPERS ---

fun UserProfile.toMap(): Map<String, Any> = mapOf(
  "userId" to userId,
  "username" to username,
  "email" to email,
  "ffUid" to ffUid,
  "ffIgn" to ffIgn,
  "upiId" to upiId,
  "depositBalance" to depositBalance,
  "winningsBalance" to winningsBalance,
  "bonusBalance" to bonusBalance,
  "walletBalance" to walletBalance,
  "referralCode" to referralCode,
  "referredBy" to referredBy,
  "referralRewarded" to referralRewarded,
  "totalReferrals" to totalReferrals,
  "referralBonusEarned" to referralBonusEarned,
  "createdAt" to createdAt,
  "updatedAt" to updatedAt
)

fun DocumentSnapshot.toUserProfile(): UserProfile {
  val uidVal = getString("userId") ?: id
  return UserProfile(
    userId = uidVal,
    username = getString("username") ?: "",
    email = getString("email") ?: "",
    ffUid = getString("ffUid") ?: "",
    ffIgn = getString("ffIgn") ?: "",
    upiId = getString("upiId") ?: "",
    depositBalance = getDouble("depositBalance") ?: 0.0,
    winningsBalance = getDouble("winningsBalance") ?: 0.0,
    bonusBalance = getDouble("bonusBalance") ?: 0.0,
    walletBalance = getDouble("walletBalance") ?: 0.0,
    referralCode = getString("referralCode")?.takeIf { it.isNotBlank() } ?: generateReferralCode(uidVal),
    referredBy = getString("referredBy") ?: "",
    referralRewarded = getBoolean("referralRewarded") ?: false,
    totalReferrals = (getLong("totalReferrals") ?: 0L).toInt(),
    referralBonusEarned = getDouble("referralBonusEarned") ?: 0.0,
    createdAt = getTimestamp("createdAt") ?: Timestamp.now(),
    updatedAt = getTimestamp("updatedAt") ?: Timestamp.now()
  )
}

fun DocumentSnapshot.toPromoCode(): PromoCode = PromoCode(
  code = getString("code") ?: id,
  bonusPercent = (getLong("bonusPercent") ?: 10L).toInt(),
  maxBonus = getDouble("maxBonus") ?: 100.0,
  minDeposit = getDouble("minDeposit") ?: 50.0,
  isActive = getBoolean("isActive") ?: true,
  createdAt = getTimestamp("createdAt") ?: Timestamp.now(),
  updatedAt = getTimestamp("updatedAt") ?: Timestamp.now()
)

fun TournamentMatch.toMap(): Map<String, Any> = mapOf(
  "title" to title,
  "categoryCode" to categoryCode,
  "categoryName" to categoryName,
  "format" to format,
  "session" to session,
  "matchDate" to matchDate,
  "startTime" to startTime,
  "startTimeEpochMs" to startTimeEpochMs,
  "maxPlayers" to maxPlayers,
  "joinedCount" to joinedCount,
  "registeredUids" to registeredUids,
  "entryFee" to entryFee,
  "prizePool" to prizePool,
  "perKillRate" to perKillRate,
  "rank1Prize" to rank1Prize,
  "rank2Prize" to rank2Prize,
  "rank3Prize" to rank3Prize,
  "status" to status,
  "roomId" to roomId,
  "roomPassword" to roomPassword,
  "roomPublished" to roomPublished,
  "mapName" to mapName,
  "matchRules" to matchRules,
  "prizesDistributed" to prizesDistributed,
  "published" to published,
  "createdBy" to createdBy,
  "createdAt" to createdAt,
  "updatedAt" to updatedAt
)

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toTournamentMatch(): TournamentMatch = TournamentMatch(
  id = id,
  title = getString("title") ?: "",
  categoryCode = getString("categoryCode") ?: "full-map-per-kill",
  categoryName = getString("categoryName") ?: "Full Map Per Kill",
  format = getString("format") ?: "Solo",
  session = getString("session") ?: "evening",
  matchDate = getString("matchDate") ?: "2026-11-01",
  startTime = getString("startTime") ?: "18:00",
  startTimeEpochMs = getLong("startTimeEpochMs") ?: 0L,
  maxPlayers = (getLong("maxPlayers") ?: 48L).toInt(),
  joinedCount = (getLong("joinedCount") ?: 0L).toInt(),
  registeredUids = (get("registeredUids") as? List<String>) ?: emptyList(),
  entryFee = getDouble("entryFee") ?: 0.0,
  prizePool = getDouble("prizePool") ?: 0.0,
  perKillRate = getDouble("perKillRate") ?: 0.0,
  rank1Prize = getDouble("rank1Prize") ?: 0.0,
  rank2Prize = getDouble("rank2Prize") ?: 0.0,
  rank3Prize = getDouble("rank3Prize") ?: 0.0,
  status = getString("status") ?: "Upcoming",
  roomId = getString("roomId") ?: "",
  roomPassword = getString("roomPassword") ?: "",
  roomPublished = getBoolean("roomPublished") ?: false,
  mapName = getString("mapName") ?: "Bermuda",
  matchRules = getString("matchRules") ?: "",
  prizesDistributed = getBoolean("prizesDistributed") ?: false,
  published = getBoolean("published") ?: true,
  createdBy = getString("createdBy") ?: "",
  createdAt = getTimestamp("createdAt") ?: Timestamp.now(),
  updatedAt = getTimestamp("updatedAt") ?: Timestamp.now()
)

fun MatchRegistration.toMap(): Map<String, Any> = mapOf(
  "matchId" to matchId,
  "matchTitle" to matchTitle,
  "userId" to userId,
  "username" to username,
  "ffUid" to ffUid,
  "ffIgn" to ffIgn,
  "status" to status,
  "feeStatus" to feeStatus,
  "entryFeePaid" to entryFeePaid,
  "bonusUsed" to bonusUsed,
  "depositUsed" to depositUsed,
  "winningsUsed" to winningsUsed,
  "slotNumber" to slotNumber,
  "teamMembers" to teamMembers,
  "allUids" to allUids,
  "createdAt" to createdAt,
  "updatedAt" to updatedAt
)

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toMatchRegistration(): MatchRegistration = MatchRegistration(
  id = id,
  matchId = getString("matchId") ?: "",
  matchTitle = getString("matchTitle") ?: "",
  userId = getString("userId") ?: "",
  username = getString("username") ?: "",
  ffUid = getString("ffUid") ?: "",
  ffIgn = getString("ffIgn") ?: "",
  status = getString("status") ?: "Approved",
  feeStatus = getString("feeStatus") ?: "paid",
  entryFeePaid = getDouble("entryFeePaid") ?: 0.0,
  bonusUsed = getDouble("bonusUsed") ?: 0.0,
  depositUsed = getDouble("depositUsed") ?: 0.0,
  winningsUsed = getDouble("winningsUsed") ?: 0.0,
  slotNumber = (getLong("slotNumber") ?: 1L).toInt(),
  teamMembers = (get("teamMembers") as? List<String>) ?: emptyList(),
  allUids = (get("allUids") as? List<String>) ?: emptyList(),
  createdAt = getTimestamp("createdAt") ?: Timestamp.now(),
  updatedAt = getTimestamp("updatedAt") ?: Timestamp.now()
)

fun MatchClaim.toMap(): Map<String, Any> = mapOf(
  "matchId" to matchId,
  "matchTitle" to matchTitle,
  "userId" to userId,
  "username" to username,
  "ffUid" to ffUid,
  "ffIgn" to ffIgn,
  "claimedKills" to claimedKills,
  "claimedRank" to claimedRank,
  "screenshotBase64" to screenshotBase64,
  "screenshotMime" to screenshotMime,
  "reportSuspectIgn" to reportSuspectIgn,
  "reportReason" to reportReason,
  "status" to status,
  "awardedAmount" to awardedAmount,
  "createdAt" to createdAt,
  "updatedAt" to updatedAt
)

fun DocumentSnapshot.toMatchClaim(): MatchClaim = MatchClaim(
  id = id,
  matchId = getString("matchId") ?: "",
  matchTitle = getString("matchTitle") ?: "",
  userId = getString("userId") ?: "",
  username = getString("username") ?: "",
  ffUid = getString("ffUid") ?: "",
  ffIgn = getString("ffIgn") ?: "",
  claimedKills = (getLong("claimedKills") ?: 0L).toInt(),
  claimedRank = (getLong("claimedRank") ?: 1L).toInt(),
  screenshotBase64 = getString("screenshotBase64") ?: "",
  screenshotMime = getString("screenshotMime") ?: "image/jpeg",
  reportSuspectIgn = getString("reportSuspectIgn") ?: "",
  reportReason = getString("reportReason") ?: "",
  status = getString("status") ?: "pending",
  awardedAmount = getDouble("awardedAmount") ?: 0.0,
  createdAt = getTimestamp("createdAt") ?: Timestamp.now(),
  updatedAt = getTimestamp("updatedAt") ?: Timestamp.now()
)

fun DepositRequest.toMap(): Map<String, Any> = mapOf(
  "userId" to userId,
  "username" to username,
  "amount" to amount,
  "utrNumber" to utrNumber,
  "promoCode" to promoCode,
  "bonusAmount" to bonusAmount,
  "status" to status,
  "createdAt" to createdAt,
  "updatedAt" to updatedAt
)

fun DocumentSnapshot.toDepositRequest(): DepositRequest = DepositRequest(
  id = id,
  userId = getString("userId") ?: "",
  username = getString("username") ?: "",
  amount = getDouble("amount") ?: 0.0,
  utrNumber = getString("utrNumber") ?: "",
  promoCode = getString("promoCode") ?: "",
  bonusAmount = getDouble("bonusAmount") ?: 0.0,
  status = getString("status") ?: "Pending",
  createdAt = getTimestamp("createdAt") ?: Timestamp.now(),
  updatedAt = getTimestamp("updatedAt") ?: Timestamp.now()
)

fun WithdrawalRequest.toMap(): Map<String, Any> = mapOf(
  "userId" to userId,
  "username" to username,
  "amount" to amount,
  "upiId" to upiId,
  "status" to status,
  "createdAt" to createdAt,
  "updatedAt" to updatedAt
)

fun DocumentSnapshot.toWithdrawalRequest(): WithdrawalRequest = WithdrawalRequest(
  id = id,
  userId = getString("userId") ?: "",
  username = getString("username") ?: "",
  amount = getDouble("amount") ?: 0.0,
  upiId = getString("upiId") ?: "",
  status = getString("status") ?: "Pending",
  createdAt = getTimestamp("createdAt") ?: Timestamp.now(),
  updatedAt = getTimestamp("updatedAt") ?: Timestamp.now()
)

fun DocumentSnapshot.toWalletTransaction(): WalletTransaction = WalletTransaction(
  id = id,
  userId = getString("userId") ?: "",
  type = getString("type") ?: "deposit",
  amount = getDouble("amount") ?: 0.0,
  description = getString("description") ?: "",
  createdAt = getTimestamp("createdAt") ?: Timestamp.now()
)
