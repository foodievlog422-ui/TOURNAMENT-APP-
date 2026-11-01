const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function nowTs() {
  return new Date(Date.now() - 1000);
}

test("Unauthenticated user: cannot read matches or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("matches").where("published", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Email spoofing attack: unverified admin email cannot create match", async () => {
  const spoofAdminDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: "foodievlog422@gmail.com",
      email_verified: false,
    })
    .firestore();

  const ts = nowTs();
  await assertFails(
    spoofAdminDb.collection("matches").doc("match_1").set({
      title: "Evening Solo",
      categoryCode: "lone-wolf-body",
      categoryName: "Lone Wolf Body",
      format: "Solo",
      session: "evening",
      matchDate: "2026-11-01",
      startTime: "18:00",
      startTimeEpochMs: 1793556000000,
      maxPlayers: 48,
      joinedCount: 0,
      registeredUids: [],
      entryFee: 20,
      prizePool: 500,
      perKillRate: 10,
      rank1Prize: 200,
      rank2Prize: 100,
      rank3Prize: 50,
      status: "Upcoming",
      roomId: "",
      roomPassword: "",
      roomPublished: false,
      mapName: "Bermuda",
      matchRules: "No grenades",
      prizesDistributed: false,
      published: true,
      createdBy: "spoof_uid",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Verified admin can create match and PromoCode, signed-in player can query published matches", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: "foodievlog422@gmail.com",
      email_verified: true,
    })
    .firestore();

  const ts = nowTs();
  await assertSucceeds(
    adminDb.collection("matches").doc("match_1").set({
      title: "Bermuda Full Map Per Kill",
      categoryCode: "full-map-per-kill",
      categoryName: "Full Map Per Kill",
      format: "Solo",
      session: "evening",
      matchDate: "2026-11-01",
      startTime: "18:00",
      startTimeEpochMs: 1793556000000,
      maxPlayers: 48,
      joinedCount: 0,
      registeredUids: [],
      entryFee: 20,
      prizePool: 500,
      perKillRate: 10,
      rank1Prize: 200,
      rank2Prize: 100,
      rank3Prize: 50,
      status: "Upcoming",
      roomId: "",
      roomPassword: "",
      roomPublished: false,
      mapName: "Bermuda",
      matchRules: "Standard BR rules",
      prizesDistributed: false,
      published: true,
      createdBy: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Admin can create WELCOME20 promo code
  await assertSucceeds(
    adminDb.collection("promo_codes").doc("WELCOME20").set({
      code: "WELCOME20",
      bonusPercent: 20,
      maxBonus: 100,
      minDeposit: 50,
      isActive: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("matches").where("published", "==", true).get()
  );
  await assertSucceeds(
    aliceDb.collection("promo_codes").where("isActive", "==", true).get()
  );
  // Query without published == true filter fails for non-admin
  await assertFails(aliceDb.collection("matches").get());
});

test("User profile & Refer & Earn: owner can create profile, claim referral once (+5 bonus), cannot self-credit deposit", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "AlicePro",
      email: "alice@example.com",
      ffUid: "",
      ffIgn: "",
      upiId: "",
      depositBalance: 0,
      winningsBalance: 0,
      bonusBalance: 20,
      walletBalance: 20,
      referralCode: "EXNALICE",
      referredBy: "",
      referralRewarded: false,
      totalReferrals: 0,
      referralBonusEarned: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Register referral code lookup
  await assertSucceeds(
    bobDb.collection("referral_codes").doc("EXNBOB99").set({
      code: "EXNBOB99",
      userId: BOB_UID,
      username: "BobGamer",
      createdAt: ts,
    })
  );

  // Cross-user PII read blocked
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Valid 8-12 digit FF UID binding succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      ffUid: "1234567890",
      ffIgn: "ALICE_FF",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // One-time Referral Code claim (+5 INR to bonusBalance) succeeds when referredBy was empty
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      referredBy: BOB_UID,
      bonusBalance: 25,
      walletBalance: 25,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Second referral claim attempt fails because referredBy is no longer empty
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      referredBy: "another_uid",
      bonusBalance: 30,
      walletBalance: 30,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Owner attempting to self-increase depositBalance fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      depositBalance: 500,
      walletBalance: 525,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Deposits (with PromoCode WELCOME20) & Withdrawals: valid UTR & UPI enforced, cross-user isolation enforced", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowTs();

  await assertSucceeds(
    aliceDb.collection("deposits").doc("utr_123456789012").set({
      userId: ALICE_UID,
      username: "AlicePro",
      amount: 100,
      utrNumber: "123456789012",
      promoCode: "WELCOME20",
      bonusAmount: 20,
      status: "Pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Invalid UTR (not 12 digits) rejected
  await assertFails(
    aliceDb.collection("deposits").doc("utr_bad").set({
      userId: ALICE_UID,
      username: "AlicePro",
      amount: 100,
      utrNumber: "12345",
      promoCode: "",
      bonusAmount: 0,
      status: "Pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid withdrawal (>= 50 INR, valid UPI regex) succeeds
  await assertSucceeds(
    aliceDb.collection("withdrawals").doc("wd_1").set({
      userId: ALICE_UID,
      username: "AlicePro",
      amount: 50,
      upiId: "alice.gamer@okaxis",
      status: "Pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read Alice's deposit or withdrawal
  await assertFails(bobDb.collection("deposits").doc("utr_123456789012").get());
  await assertFails(bobDb.collection("withdrawals").doc("wd_1").get());
});
