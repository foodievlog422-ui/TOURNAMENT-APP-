package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DEFAULT_PROMO_CODES
import com.example.data.model.MatchRegistration
import com.example.data.model.TournamentMatch
import com.example.data.model.calculateEntryFeeBreakdown
import com.example.data.model.calculatePrizeAmount
import com.example.data.model.calculatePromoBonus
import com.example.data.model.generateReferralCode
import com.example.data.model.isRoomCredentialsVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Exalote Esports", appName)
  }

  @Test
  fun `three bucket fee deduction follows exact 10 percent bonus then deposit then winnings order and blocks unusable bonus`() {
    val breakdown = calculateEntryFeeBreakdown(
      entryFee = 100.0,
      bonusBalance = 50.0,
      depositBalance = 60.0,
      winningsBalance = 50.0
    )
    assertTrue(breakdown.canAfford)
    assertEquals(10.0, breakdown.bonusUsed, 0.001)
    assertEquals(60.0, breakdown.depositUsed, 0.001)
    assertEquals(30.0, breakdown.winningsUsed, 0.001)

    // Unusable bonus balance (>10% of entry fee) must NOT be counted toward sufficiency
    val insufficientDueToBonusCap = calculateEntryFeeBreakdown(
      entryFee = 100.0,
      bonusBalance = 200.0, // Only 10.0 is usable
      depositBalance = 40.0,
      winningsBalance = 40.0 // Total usable = 10 + 40 + 40 = 90 < 100
    )
    assertFalse(insufficientDueToBonusCap.canAfford)
  }

  @Test
  fun `promo code WELCOME20 calculates 20 percent bonus capped at 100 on min deposit 50`() {
    val welcome20 = DEFAULT_PROMO_CODES.first { it.code == "WELCOME20" }
    assertEquals(0.0, calculatePromoBonus(40.0, welcome20), 0.001) // Below minDeposit 50
    assertEquals(10.0, calculatePromoBonus(50.0, welcome20), 0.001) // 20% of 50 = 10
    assertEquals(40.0, calculatePromoBonus(200.0, welcome20), 0.001) // 20% of 200 = 40
    assertEquals(100.0, calculatePromoBonus(800.0, welcome20), 0.001) // Capped at maxBonus 100
  }

  @Test
  fun `referral code generator produces deterministic EXN code`() {
    val code = generateReferralCode("user_98a7b6c5")
    assertEquals("EXNUSER9", code)
  }

  @Test
  fun `prize calculation combines per kill rate and rank prize`() {
    val match = TournamentMatch(
      perKillRate = 15.0,
      rank1Prize = 250.0,
      rank2Prize = 150.0,
      rank3Prize = 100.0
    )
    assertEquals(325.0, calculatePrizeAmount(kills = 5, rank = 1, match = match), 0.001)
  }

  @Test
  fun `room credentials only visible to approved players when published or within 15 minutes`() {
    val now = 1_000_000_000L
    val matchHiddenFar = TournamentMatch(
      roomId = "889911",
      roomPassword = "77",
      roomPublished = false,
      startTimeEpochMs = now + 60 * 60 * 1000L
    )
    val reg = MatchRegistration(status = "Approved", feeStatus = "paid")

    assertFalse(isRoomCredentialsVisible(matchHiddenFar, null, now))
    assertFalse(isRoomCredentialsVisible(matchHiddenFar, reg, now))
    assertTrue(isRoomCredentialsVisible(matchHiddenFar.copy(roomPublished = true), reg, now))
    assertTrue(
      isRoomCredentialsVisible(
        matchHiddenFar.copy(startTimeEpochMs = now + 10 * 60 * 1000L),
        reg,
        now
      )
    )
  }
}
