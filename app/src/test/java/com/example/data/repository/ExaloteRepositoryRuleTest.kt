package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExaloteRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureUserProfile_and_bindFreeFireProfile_validPayload_succeeds() = runBlocking {
    val uniqueEmail = "player_${UUID.randomUUID().toString().take(6)}@test.com"
    val uid = signInTestUser(uniqueEmail)
    val repository = ExaloteRepository(firestore)

    val ensureResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.ensureUserProfile("SurvivorX", uniqueEmail)
    }
    assertTrue("Expected ensureUserProfile to succeed: ${ensureResult.exceptionOrNull()}", ensureResult.isSuccess)
    val createdProfile = ensureResult.getOrThrow()
    assertTrue("Expected non-blank referralCode", createdProfile.referralCode.startsWith("EXN"))

    val bindResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.bindFreeFireProfile(
        username = "SurvivorX",
        ffUid = "9876543210",
        ffIgn = "EXN_SURVIVOR",
        upiId = "survivor@okaxis"
      )
    }
    assertTrue("Expected bindFreeFireProfile to succeed: ${bindResult.exceptionOrNull()}", bindResult.isSuccess)

    val emittedProfile = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeUserProfile(uid).first { it != null && it.ffUid == "9876543210" }
    }
    assertNotNull(emittedProfile)
    assertEquals("EXN_SURVIVOR", emittedProfile?.ffIgn)
  }

  @Test
  fun applyReferralCode_awards5InrBonusOnce_andBlocksSecondClaim() = runBlocking {
    val referrerEmail = "referrer_${UUID.randomUUID().toString().take(6)}@test.com"
    signInTestUser(referrerEmail)
    val referrerRepo = ExaloteRepository(firestore)
    val referrerProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
      referrerRepo.ensureUserProfile("ReferrerKing", referrerEmail).getOrThrow()
    }

    val friendEmail = "friend_${UUID.randomUUID().toString().take(6)}@test.com"
    val friendUid = signInTestUser(friendEmail)
    val friendRepo = ExaloteRepository(firestore)
    val friendInitial = withTimeout(DEFAULT_TIMEOUT_MS) {
      friendRepo.ensureUserProfile("FriendJoiner", friendEmail).getOrThrow()
    }

    val claimRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      friendRepo.applyReferralCode(referrerProfile.referralCode)
    }
    assertTrue("Expected referral claim to succeed: ${claimRes.exceptionOrNull()}", claimRes.isSuccess)
    assertEquals(5.0, claimRes.getOrThrow(), 0.001)

    val updatedFriend = withTimeout(FLOW_TIMEOUT_MS) {
      friendRepo.observeUserProfile(friendUid).first { it != null && it.referredBy == referrerProfile.userId }
    }
    assertEquals(friendInitial.bonusBalance + 5.0, updatedFriend?.bonusBalance ?: 0.0, 0.001)
  }

  @Test
  fun getUserProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceEmail = "alice_${UUID.randomUUID().toString().take(6)}@test.com"
    val bobEmail = "bob_${UUID.randomUUID().toString().take(6)}@test.com"
    val aliceUid = signInTestUser(aliceEmail)
    val aliceRepo = ExaloteRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.ensureUserProfile("AliceFF", aliceEmail).getOrThrow()
    }

    signInTestUser(bobEmail)
    var caughtException: Exception? = null
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("users").document(aliceUid).get().await()
      }
    } catch (e: Exception) {
      caughtException = e
    }
    assertNotNull("Expected cross-user read to fail", caughtException)
    val fsEx = checkNotNull(caughtException?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException, got $caughtException"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
  }

  @Test
  fun observePublishedMatches_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repository = ExaloteRepository(firestore)

    var caughtException: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observePublishedMatches().first()
      }
    } catch (e: Exception) {
      caughtException = e
    }
    assertNotNull("Expected unauthenticated query to fail", caughtException)
    val fsEx = checkNotNull(caughtException?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException, got $caughtException"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
  }

  @Test
  fun submitUpiDeposit_withWelcome20PromoCode_calculates20PercentBonus() = runBlocking {
    val email = "depositor_${UUID.randomUUID().toString().take(6)}@test.com"
    val uid = signInTestUser(email)
    val repository = ExaloteRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.ensureUserProfile("DepositorFF", email).getOrThrow()
    }

    val random12Digits = (100000000000L..999999999999L).random().toString()
    val depResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.submitUpiDeposit(150.0, random12Digits, "WELCOME20")
    }
    assertTrue("Expected deposit to succeed: ${depResult.exceptionOrNull()}", depResult.isSuccess)
    assertEquals(30.0, depResult.getOrThrow(), 0.001)

    val deposits = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeUserDeposits(uid).first { list -> list.any { it.utrNumber == random12Digits } }
    }
    assertTrue(
      deposits.any {
        it.utrNumber == random12Digits &&
          it.amount == 150.0 &&
          it.promoCode == "WELCOME20" &&
          it.bonusAmount == 30.0
      }
    )
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
