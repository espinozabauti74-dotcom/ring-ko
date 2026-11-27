const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_boxer_123";
const BOB_UID = "bob_boxer_456";
const CHARLIE_UID = "charlie_intruder_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function buildValidMatchCreate(hostUid = ALICE_UID, roomCode = "RING42") {
  const now = new Date(Date.now() - 2000);
  return {
    roomCode,
    hostUid,
    participantUids: [hostUid],
    hostName: "El Tornado",
    hostGloveId: "classic_red",
    status: "WAITING",
    maxRounds: 3,
    hostHp: 150,
    guestHp: 150,
    hostStamina: 100,
    guestStamina: 100,
    hostPosX: -0.35,
    guestPosX: 0.35,
    hostGuarding: false,
    guestGuarding: false,
    hostDodgeSeq: 0,
    guestDodgeSeq: 0,
    hostPunchType: "NONE",
    guestPunchType: "NONE",
    hostPunchSeq: 0,
    guestPunchSeq: 0,
    hostPunchDamage: 0,
    guestPunchDamage: 0,
    hostHitsLanded: 0,
    guestHitsLanded: 0,
    hostKnockdowns: 0,
    guestKnockdowns: 0,
    createdAt: now,
    updatedAt: now,
  };
}

// --- 1. USERS COLLECTION TESTS ---

test("Unauthenticated user cannot read or write /users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "El Tornado",
      equippedGloveId: "classic_red",
      onlineWins: 0,
      onlineLosses: 0,
      onlineKos: 0,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Owner can create and update their own BoxerProfile, while cross-user access fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "El Tornado",
      equippedGloveId: "classic_red",
      onlineWins: 0,
      onlineLosses: 0,
      onlineKos: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      onlineWins: 1,
      onlineKos: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt or ghost field fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read & write fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hack",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

// --- 2. ONLINE_MATCHES COLLECTION TESTS ---

test("Unauthenticated user cannot read, list, or create online_matches", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("online_matches").where("status", "==", "WAITING").get());
  await assertFails(unauthDb.collection("online_matches").doc("RING42").set(buildValidMatchCreate()));
});

test("Host can create WAITING room, Bob can list WAITING rooms and join, Charlie cannot hijack", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Create valid room (no @DocumentId `id` key)
  await assertSucceeds(
    aliceDb.collection("online_matches").doc("RING42").set(buildValidMatchCreate(ALICE_UID, "RING42"))
  );

  // Reject creating with @DocumentId 'id' or invalid future timestamp
  await assertFails(
    aliceDb.collection("online_matches").doc("RING99").set({
      ...buildValidMatchCreate(ALICE_UID, "RING99"),
      id: "RING99",
    })
  );
  await assertFails(
    aliceDb.collection("online_matches").doc("RING88").set({
      ...buildValidMatchCreate(ALICE_UID, "RING88"),
      createdAt: new Date(Date.now() + 60000),
    })
  );

  // Filtered query for open rooms succeeds; unfiltered list fails once active
  await assertSucceeds(
    bobDb.collection("online_matches").where("status", "==", "WAITING").get()
  );

  // Host cannot join own room
  await assertFails(
    aliceDb.collection("online_matches").doc("RING42").update({
      guestUid: ALICE_UID,
      guestName: "Self",
      guestGloveId: "classic_red",
      participantUids: [ALICE_UID, ALICE_UID],
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob joins room cleanly
  await assertSucceeds(
    bobDb.collection("online_matches").doc("RING42").update({
      guestUid: BOB_UID,
      guestName: "Benja",
      guestGloveId: "cobalt_flash",
      participantUids: [ALICE_UID, BOB_UID],
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Charlie cannot read ACTIVE match or unfiltered list
  await assertFails(charlieDb.collection("online_matches").doc("RING42").get());
  await assertFails(charlieDb.collection("online_matches").get());

  // Host & Guest can each update ONLY their own combat fields
  await assertSucceeds(
    aliceDb.collection("online_matches").doc("RING42").update({
      hostPosX: -0.1,
      hostPunchType: "JAB",
      hostPunchSeq: 1,
      hostPunchDamage: 12,
      hostStamina: 92,
      updatedAt: new Date(Date.now() - 900),
    })
  );

  await assertSucceeds(
    bobDb.collection("online_matches").doc("RING42").update({
      guestHp: 138,
      guestGuarding: true,
      guestHitsLanded: 1,
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Cross-fighter field tampering fails (Host cannot overwrite guestHp directly during ACTIVE)
  await assertFails(
    aliceDb.collection("online_matches").doc("RING42").update({
      guestHp: 0,
      updatedAt: new Date(Date.now() - 700),
    })
  );

  // Finish bout locks terminal state
  await assertSucceeds(
    bobDb.collection("online_matches").doc("RING42").update({
      guestHp: 0,
      guestKnockdowns: 1,
      status: "FINISHED",
      winnerUid: ALICE_UID,
      winMethod: "KO",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Subsequent updates on FINISHED match must fail
  await assertFails(
    aliceDb.collection("online_matches").doc("RING42").update({
      hostHp: 150,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Cannot delete an ACTIVE/FINISHED match
  await assertFails(aliceDb.collection("online_matches").doc("RING42").delete());
});

test("Host can cancel/delete WAITING room before guest joins", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("online_matches").doc("CANCEL1").set(buildValidMatchCreate(ALICE_UID, "CANCEL1"))
  );
  await assertFails(bobDb.collection("online_matches").doc("CANCEL1").delete());
  await assertSucceeds(aliceDb.collection("online_matches").doc("CANCEL1").delete());
});
