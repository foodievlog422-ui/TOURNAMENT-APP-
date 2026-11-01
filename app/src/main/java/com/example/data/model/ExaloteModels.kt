package com.example.data.model

import com.google.firebase.Timestamp
import kotlin.math.min
import kotlin.math.round

data class ExaloteCategory(
  val code: String,
  val name: String,
  val format: String,
  val defaultMaxPlayers: Int
)

val EXALOTE_CATEGORIES = listOf(
  ExaloteCategory("lone-wolf-body", "Lone Wolf Body", "Solo", 2),
  ExaloteCategory("headshot-1v1", "Headshot 1v1", "1v1", 2),
  ExaloteCategory("full-map-per-kill", "Full Map Per Kill", "Solo", 48),
  ExaloteCategory("headshot-2v2", "Headshot 2v2", "2v2", 4),
  ExaloteCategory("survival-duo", "Battle Royale Duo", "Duo", 48),
  ExaloteCategory("clash-squad-4v4", "Clash Squad 4v4", "4v4", 8),
  ExaloteCategory("br-squad", "Battle Royale Squad", "Squad", 48),
  ExaloteCategory("desert-eagle-1v1", "Desert Eagle 1v1", "1v1", 2)
)

data class UserProfile(
  val userId: String = "",
  val username: String = "",
  val email: String = "",
  val ffUid: String = "",
  val ffIgn: String = "",
  val upiId: String = "",
  val depositBalance: Double = 0.0,
  val winningsBalance: Double = 0.0,
  val bonusBalance: Double = 0.0,
  val walletBalance: Double = 0.0,
  val referralCode: String = "",
  val referredBy: String = "",
  val referralRewarded: Boolean = false,
  val totalReferrals: Int = 0,
  val referralBonusEarned: Double = 0.0,
  val createdAt: Timestamp = Timestamp.now(),
  val updatedAt: Timestamp = Timestamp.now()
) {
  val hasValidFreeFireBinding: Boolean
    get() = isValidFfUid(ffUid) && ffIgn.trim().length in 2..60
}

data class ReferralCodeLookup(
  val code: String = "",
  val userId: String = "",
  val username: String = "",
  val createdAt: Timestamp = Timestamp.now()
)

data class PromoCode(
  val code: String = "",
  val bonusPercent: Int = 10,
  val maxBonus: Double = 100.0,
  val minDeposit: Double = 50.0,
  val isActive: Boolean = true,
  val createdAt: Timestamp = Timestamp.now(),
  val updatedAt: Timestamp = Timestamp.now()
)

val DEFAULT_PROMO_CODES = listOf(
  PromoCode(
    code = "WELCOME20",
    bonusPercent = 20,
    maxBonus = 100.0,
    minDeposit = 50.0,
    isActive = true
  ),
  PromoCode(
    code = "EXN50",
    bonusPercent = 15,
    maxBonus = 150.0,
    minDeposit = 100.0,
    isActive = true
  )
)

data class TournamentMatch(
  val id: String = "",
  val title: String = "",
  val categoryCode: String = "full-map-per-kill",
  val categoryName: String = "Full Map Per Kill",
  val format: String = "Solo",
  val session: String = "evening",
  val matchDate: String = "2026-11-01",
  val startTime: String = "18:00",
  val startTimeEpochMs: Long = 0L,
  val maxPlayers: Int = 48,
  val joinedCount: Int = 0,
  val registeredUids: List<String> = emptyList(),
  val entryFee: Double = 0.0,
  val prizePool: Double = 0.0,
  val perKillRate: Double = 0.0,
  val rank1Prize: Double = 0.0,
  val rank2Prize: Double = 0.0,
  val rank3Prize: Double = 0.0,
  val status: String = "Upcoming",
  val roomId: String = "",
  val roomPassword: String = "",
  val roomPublished: Boolean = false,
  val mapName: String = "Bermuda",
  val matchRules: String = "",
  val prizesDistributed: Boolean = false,
  val published: Boolean = true,
  val createdBy: String = "",
  val createdAt: Timestamp = Timestamp.now(),
  val updatedAt: Timestamp = Timestamp.now()
)

data class TeammateInput(
  val ffUid: String = "",
  val ffIgn: String = ""
)

data class MatchRegistration(
  val id: String = "",
  val matchId: String = "",
  val matchTitle: String = "",
  val userId: String = "",
  val username: String = "",
  val ffUid: String = "",
  val ffIgn: String = "",
  val status: String = "Approved",
  val feeStatus: String = "paid",
  val entryFeePaid: Double = 0.0,
  val bonusUsed: Double = 0.0,
  val depositUsed: Double = 0.0,
  val winningsUsed: Double = 0.0,
  val slotNumber: Int = 1,
  val teamMembers: List<String> = emptyList(),
  val allUids: List<String> = emptyList(),
  val createdAt: Timestamp = Timestamp.now(),
  val updatedAt: Timestamp = Timestamp.now()
)

data class MatchClaim(
  val id: String = "",
  val matchId: String = "",
  val matchTitle: String = "",
  val userId: String = "",
  val username: String = "",
  val ffUid: String = "",
  val ffIgn: String = "",
  val claimedKills: Int = 0,
  val claimedRank: Int = 1,
  val screenshotBase64: String = "",
  val screenshotMime: String = "image/jpeg",
  val reportSuspectIgn: String = "",
  val reportReason: String = "",
  val status: String = "pending",
  val awardedAmount: Double = 0.0,
  val createdAt: Timestamp = Timestamp.now(),
  val updatedAt: Timestamp = Timestamp.now()
) {
  val hasHackerReport: Boolean
    get() = reportSuspectIgn.isNotBlank() || reportReason.isNotBlank()
}

data class DepositRequest(
  val id: String = "",
  val userId: String = "",
  val username: String = "",
  val amount: Double = 0.0,
  val utrNumber: String = "",
  val promoCode: String = "",
  val bonusAmount: Double = 0.0,
  val status: String = "Pending",
  val createdAt: Timestamp = Timestamp.now(),
  val updatedAt: Timestamp = Timestamp.now()
)

data class WithdrawalRequest(
  val id: String = "",
  val userId: String = "",
  val username: String = "",
  val amount: Double = 0.0,
  val upiId: String = "",
  val status: String = "Pending",
  val createdAt: Timestamp = Timestamp.now(),
  val updatedAt: Timestamp = Timestamp.now()
)

data class WalletTransaction(
  val id: String = "",
  val userId: String = "",
  val type: String = "deposit", // deposit, withdrawal, entry_fee, prize_payout, refund, bonus
  val amount: Double = 0.0,
  val description: String = "",
  val createdAt: Timestamp = Timestamp.now()
)

data class EntryFeeBreakdown(
  val entryFee: Double,
  val bonusUsed: Double,
  val depositUsed: Double,
  val winningsUsed: Double,
  val canAfford: Boolean
) {
  val totalFee: Double
    get() = entryFee

  val shortfall: Double
    get() = roundCurrency((entryFee - (bonusUsed + depositUsed + winningsUsed)).coerceAtLeast(0.0))
}

fun roundCurrency(value: Double): Double = round(value * 100.0) / 100.0

fun isValidFfUid(uid: String): Boolean = uid.trim().matches(Regex("^[0-9]{8,12}$"))

fun isValidUtr(utr: String): Boolean = utr.trim().matches(Regex("^[0-9]{12}$"))

fun isValidUpiId(upi: String): Boolean =
  upi.trim().matches(Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$"))

fun generateReferralCode(uid: String): String {
  val clean = uid.filter { it.isLetterOrDigit() }.uppercase()
  val suffix = if (clean.length >= 5) clean.take(5) else clean.padEnd(5, 'X')
  return "EXN$suffix"
}

fun calculatePromoBonus(amount: Double, promo: PromoCode?): Double {
  if (promo == null || !promo.isActive) return 0.0
  val cleanAmount = roundCurrency(amount)
  if (cleanAmount < promo.minDeposit) return 0.0
  val rawBonus = roundCurrency(cleanAmount * (promo.bonusPercent / 100.0))
  return min(rawBonus, roundCurrency(promo.maxBonus))
}

fun requiredTeammatesForFormat(format: String): Int =
  when (format.trim()) {
    "2v2", "Duo" -> 1
    "4v4", "Squad" -> 3
    else -> 0
  }

/**
 * Atomic 3-Bucket Entry Fee Deduction Order:
 * 1. First, calculate `usable_bonus = MIN(bonus_balance, ROUND(entry_fee * 0.10, 2))` and deduct up to 10% from `bonus_balance`.
 * 2. Second, deduct the remaining fee from `deposit_balance`.
 * 3. Third, deduct any remaining shortfall from `winnings_balance`.
 * 4. Validate before deducting that `(usable_bonus + deposit_balance + winnings_balance) >= entry_fee`
 *    (do not count unusable bonus balance toward sufficiency).
 * 5. Keep master `wallet_balance` synced as `(deposit_balance + winnings_balance + bonus_balance)`.
 */
fun calculateEntryFeeBreakdown(
  entryFee: Double,
  bonusBalance: Double,
  depositBalance: Double,
  winningsBalance: Double
): EntryFeeBreakdown {
  val fee = roundCurrency(entryFee.coerceAtLeast(0.0))
  if (fee <= 0.0) {
    return EntryFeeBreakdown(0.0, 0.0, 0.0, 0.0, true)
  }

  val cleanBonus = roundCurrency(bonusBalance.coerceAtLeast(0.0))
  val cleanDeposit = roundCurrency(depositBalance.coerceAtLeast(0.0))
  val cleanWinnings = roundCurrency(winningsBalance.coerceAtLeast(0.0))

  // Step 1: usable_bonus = MIN(bonus_balance, ROUND(entry_fee * 0.10, 2))
  val maxBonusAllowed = roundCurrency(fee * 0.10)
  val usableBonus = roundCurrency(min(cleanBonus, maxBonusAllowed))

  // Step 4: Validate sufficiency BEFORE deducting (never count unusable bonus balance)
  val totalUsableFunds = roundCurrency(usableBonus + cleanDeposit + cleanWinnings)
  if (totalUsableFunds + 0.0001 < fee) {
    val remAfterBonus = roundCurrency((fee - usableBonus).coerceAtLeast(0.0))
    val depAttempt = roundCurrency(min(cleanDeposit, remAfterBonus))
    val remAfterDep = roundCurrency((remAfterBonus - depAttempt).coerceAtLeast(0.0))
    val winAttempt = roundCurrency(min(cleanWinnings, remAfterDep))
    return EntryFeeBreakdown(
      entryFee = fee,
      bonusUsed = usableBonus,
      depositUsed = depAttempt,
      winningsUsed = winAttempt,
      canAfford = false
    )
  }

  // Step 2: Deduct remaining from deposit_balance
  val remainingAfterBonus = roundCurrency((fee - usableBonus).coerceAtLeast(0.0))
  val depositUsed = roundCurrency(min(cleanDeposit, remainingAfterBonus))

  // Step 3: Deduct any remaining shortfall from winnings_balance
  val remainingAfterDeposit = roundCurrency((remainingAfterBonus - depositUsed).coerceAtLeast(0.0))
  val winningsUsed = roundCurrency(min(cleanWinnings, remainingAfterDeposit))

  val totalCovered = roundCurrency(usableBonus + depositUsed + winningsUsed)
  val canAfford = totalCovered + 0.0001 >= fee
  return EntryFeeBreakdown(
    entryFee = fee,
    bonusUsed = usableBonus,
    depositUsed = depositUsed,
    winningsUsed = winningsUsed,
    canAfford = canAfford
  )
}

fun calculatePrizeAmount(kills: Int, rank: Int, match: TournamentMatch): Double {
  val killReward = kills.coerceAtLeast(0) * match.perKillRate.coerceAtLeast(0.0)
  val rankReward = when (rank) {
    1 -> match.rank1Prize
    2 -> match.rank2Prize
    3 -> match.rank3Prize
    else -> 0.0
  }
  return roundCurrency(killReward + rankReward)
}

fun isRoomCredentialsVisible(
  match: TournamentMatch,
  registration: MatchRegistration?,
  currentTimeMs: Long
): Boolean {
  if (registration == null) return false
  val isApprovedAndPaid = registration.status == "Approved" || registration.feeStatus == "paid"
  if (!isApprovedAndPaid) return false
  if (match.roomId.isBlank()) return false

  val within15Minutes =
    match.startTimeEpochMs > 0L && (match.startTimeEpochMs - currentTimeMs) <= 15 * 60 * 1000L
  return match.roomPublished || within15Minutes
}
