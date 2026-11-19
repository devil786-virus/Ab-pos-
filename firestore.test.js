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

test("Unauthenticated user: cannot read or write any store collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("p1")
      .set({
        userId: ALICE_UID,
        code: "101",
        nameBn: "শাড়ি",
        nameEn: "Saree",
        category: "Saree",
        price: 650,
        stock: 10,
        createdAt: past,
        updatedAt: past,
      })
  );
});

test("UserProfile: owner create & partial update succeed; cross-user & ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set({
        userId: ALICE_UID,
        storeName: "আবরণী বস্ত্রালয় (Avarani Bastralaya)",
        displayName: "Shop Owner",
        email: "owner@example.com",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .update({
        displayName: "Updated Owner",
        email: "owner@example.com",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject ghost field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .update({
        isAdmin: true,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject cross-user read/write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .update({ storeName: "Hijacked", updatedAt: new Date(Date.now() - 1000) })
  );
});

test("Products: Kotlin-mirrored create, partial update, list, and delete succeed; invalid bounds fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("p1")
      .set({
        userId: ALICE_UID,
        code: "101",
        nameBn: "ধনেখালি তাঁতের শাড়ি",
        nameEn: "Dhaniakhali Tant Saree",
        category: "Saree",
        price: 650,
        stock: 30,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Empty & non-empty subcollection list
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").get()
  );

  // Partial update matching PosSyncRepository
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("p1")
      .update({
        code: "101",
        nameBn: "ধনেখালি তাঁতের শাড়ি",
        nameEn: "Dhaniakhali Tant Saree",
        category: "Saree",
        price: 700,
        stock: 28,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject @DocumentId 'id' inside payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("p2")
      .set({
        id: "p2",
        userId: ALICE_UID,
        code: "102",
        nameBn: "Jamdani",
        nameEn: "Jamdani",
        category: "Saree",
        price: 1200,
        stock: 10,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("p3")
      .set({
        userId: ALICE_UID,
        code: "103",
        nameBn: "Future",
        nameEn: "Future",
        category: "Saree",
        price: 500,
        stock: 5,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: created,
      })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("p1").delete()
  );
});

test("Customers, Bills, Expenses, and DuePayments: Kotlin payloads & cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Customer
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("c1")
      .set({
        userId: ALICE_UID,
        code: "503",
        name: "Arun Maity",
        phone: "9732100503",
        address: "Nimtala Bazar, Khejuri",
        due: 450,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Bill (with optional customerId & customerCode omitted for walk-in)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bills")
      .doc("b_1001")
      .set({
        userId: ALICE_UID,
        billNo: 1001,
        dateIso: "2026-11-18",
        customerName: "Walk-in",
        itemCount: 2,
        subtotal: 1300,
        discount: 50,
        netBill: 1250,
        prevDue: 0,
        grandPayable: 1250,
        cashPaid: 1250,
        remainingDue: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Expense
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("expenses")
      .doc("ex_1")
      .set({
        userId: ALICE_UID,
        dateIso: "2026-11-18",
        desc: "Tea-Snacks",
        amount: 60,
        createdAt: created,
        updatedAt: created,
      })
  );

  // DuePayment (optional note omitted)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("duePayments")
      .doc("dp_1")
      .set({
        userId: ALICE_UID,
        dateIso: "2026-11-18",
        customerId: "c1",
        customerCode: "503",
        customerName: "Arun Maity",
        oldDue: 450,
        amount: 200,
        newDue: 250,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Cross-user list checks fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bills").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("expenses").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("duePayments").get());
});
