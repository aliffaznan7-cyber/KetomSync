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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 1000);
}

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

function validClipPayload(uid) {
  return {
    userId: uid,
    content: "https://developer.android.com/jetpack/compose",
    category: "link",
    isPinned: false,
    deviceName: "Google Pixel 9 Pro",
    deviceType: "phone",
    retentionPolicy: "24h",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validSnippetPayload(uid) {
  return {
    userId: uid,
    title: "Standup Update",
    shortcut: "/standup",
    body: "Hi {team}, working on {task} from {device}.",
    category: "macro",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

function validDevicePayload(uid) {
  return {
    userId: uid,
    deviceName: "Xiaomi Pad 7",
    deviceType: "tablet",
    modelName: "Xiaomi 2410CRP4CG",
    syncEnabled: true,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
}

test("Unauthenticated user cannot read or write clipboard entries", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/clipboard_entries`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/clipboard_entries`).doc("clip_1").set(validClipPayload(ALICE_UID))
  );
});

test("Owner can create, list, update pin, and delete clipboard entry", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/clipboard_entries`).doc("clip_1");
  await assertSucceeds(ref.set(validClipPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/clipboard_entries`).get());
  await assertSucceeds(ref.update({ isPinned: true, updatedAt: pastTimestamp() }));
  await assertSucceeds(ref.delete());
});

test("Cross-user access to clipboard entries, snippets, and devices is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/clipboard_entries`).doc("clip_1").set(validClipPayload(ALICE_UID))
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/clipboard_entries`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/clipboard_entries`).doc("clip_1").get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/clipboard_entries`).doc("clip_1").delete());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/snippets`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/devices`).get());
});

test("Shadow update with ghost field or invalid category is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection(`users/${ALICE_UID}/clipboard_entries`).doc("clip_ghost");
  await assertFails(
    ref.set({
      ...validClipPayload(ALICE_UID),
      isAdmin: true,
    })
  );
  await assertFails(
    ref.set({
      ...validClipPayload(ALICE_UID),
      category: "invalid_cat",
    })
  );
});

test("Spoofing another userId in payload is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/clipboard_entries`).doc("clip_spoof").set(validClipPayload(BOB_UID))
  );
});

test("Owner can CRUD snippets and devices with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const snippetRef = aliceDb.collection(`users/${ALICE_UID}/snippets`).doc("snip_1");
  await assertSucceeds(snippetRef.set(validSnippetPayload(ALICE_UID)));
  await assertSucceeds(snippetRef.update({ title: "Updated Standup", updatedAt: pastTimestamp() }));

  const deviceRef = aliceDb.collection(`users/${ALICE_UID}/devices`).doc("dev_pad7");
  await assertSucceeds(deviceRef.set(validDevicePayload(ALICE_UID)));
  await assertSucceeds(deviceRef.update({ syncEnabled: false, updatedAt: pastTimestamp() }));
});
