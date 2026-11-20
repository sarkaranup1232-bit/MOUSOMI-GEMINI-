const { describe, it, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const EMULATOR = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085";
const [host, rawPort] = EMULATOR.split(":");
const port = Number(rawPort || "8085");

const nowDate = () => new Date(Date.now() - 1000);
const futureDate = () => new Date(Date.now() + 60000);

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host,
      port,
      rules: fs.readFileSync(path.resolve(__dirname, "firestore.rules"), "utf8"),
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv.cleanup();
});

describe("Mousomi AI Firestore Security Rules", () => {
  it("denies unauthenticated access to profiles, conversations, and messages", async () => {
    const anonDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(anonDb.collection("users").doc("userA").get());
    await assertFails(
      anonDb.collection("users").doc("userA").set({
        userId: "userA",
        displayName: "Anon",
        themeModeName: "DARK",
        selectedModelId: "GEMINI_4_HYDROGEN",
        composerGlowIntensity: 0.85,
        createdAt: nowDate(),
        updatedAt: nowDate(),
      })
    );
    await assertFails(anonDb.collection("users").doc("userA").collection("conversations").get());
  });

  it("allows owner to create/update profile, blocks immutable field edits, blocks future timestamps, and denies cross-user access", async () => {
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    const profileRef = aliceDb.collection("users").doc("alice");

    await assertSucceeds(
      profileRef.set({
        userId: "alice",
        displayName: "Alice",
        email: "alice@example.com",
        themeModeName: "DARK",
        selectedModelId: "GEMINI_4_HYDROGEN",
        composerGlowIntensity: 0.85,
        createdAt: nowDate(),
        updatedAt: nowDate(),
      })
    );

    // Partial update matching Kotlin .update(...)
    await assertSucceeds(
      profileRef.update({
        displayName: "Alice Updated",
        updatedAt: nowDate(),
      })
    );

    // Tampered userId update fails
    await assertFails(
      profileRef.update({
        userId: "bob",
        updatedAt: nowDate(),
      })
    );

    // Future timestamp fails
    await assertFails(
      profileRef.update({
        updatedAt: futureDate(),
      })
    );

    // Cross-user list / get / write fails
    await assertFails(bobDb.collection("users").get());
    await assertFails(bobDb.collection("users").doc("alice").get());
    await assertFails(
      bobDb.collection("users").doc("alice").set({
        userId: "alice",
        displayName: "Hijacked",
        themeModeName: "DARK",
        selectedModelId: "GEMINI_4_HYDROGEN",
        composerGlowIntensity: 0.5,
        createdAt: nowDate(),
        updatedAt: nowDate(),
      })
    );
  });

  it("supports scoped owner CRUD for conversations and messages and rejects mismatched ownership", async () => {
    const aliceDb = testEnv.authenticatedContext("alice").firestore();
    const bobDb = testEnv.authenticatedContext("bob").firestore();
    const convRef = aliceDb.collection("users").doc("alice").collection("conversations").doc("conv_1");

    await assertSucceeds(
      convRef.set({
        userId: "alice",
        title: "Deep Research Session",
        clientUpdatedAtMs: 1750000000000,
        createdAt: nowDate(),
        updatedAt: nowDate(),
      })
    );

    const msgRef = convRef.collection("messages").doc("msg_1");
    await assertSucceeds(
      msgRef.set({
        userId: "alice",
        conversationId: "conv_1",
        fromUser: true,
        text: "Generate an image of a glowing observatory",
        clientTimestampMs: 1750000001000,
        errorFlag: false,
        attachmentsCount: 0,
        createdAt: nowDate(),
        updatedAt: nowDate(),
      })
    );

    // Scoped list queries matching Kotlin FirestoreCloudSyncRepository
    await assertSucceeds(
      aliceDb.collection("users").doc("alice").collection("conversations")
        .where("userId", "==", "alice")
        .orderBy("clientUpdatedAtMs", "desc")
        .get()
    );

    await assertSucceeds(
      convRef.collection("messages")
        .where("userId", "==", "alice")
        .orderBy("clientTimestampMs", "asc")
        .get()
    );

    // Mismatched conversationId rejected
    await assertFails(
      convRef.collection("messages").doc("msg_bad").set({
        userId: "alice",
        conversationId: "other_conv",
        fromUser: true,
        text: "Spoofed",
        clientTimestampMs: 1750000002000,
        createdAt: nowDate(),
        updatedAt: nowDate(),
      })
    );

    // Bob cannot read or delete Alice's conversation or messages
    await assertFails(bobDb.collection("users").doc("alice").collection("conversations").doc("conv_1").get());
    await assertFails(bobDb.collection("users").doc("alice").collection("conversations").doc("conv_1").delete());

    // Owner delete succeeds
    await assertSucceeds(convRef.delete());
  });
});
