const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();
const F = admin.firestore.FieldValue;

const CREATOR_PRIVATE_FIELDS = [
  "creatorAge",
  "creatorBio",
  "creatorSelfiePath",
  "creatorFaceDetected",
  "creatorApplicationSubmittedAt",
  "creatorApplicationStatus",
  "creatorApproved",
  "creatorApplicationReviewedAt",
  "creatorStatus",
];

async function migrateOne(userDoc) {
  const data = userDoc.data();
  const updates = {};
  const application = {};
  let hadApplication = false;

  for (const field of CREATOR_PRIVATE_FIELDS) {
    if (Object.prototype.hasOwnProperty.call(data, field)) {
      application[field] = data[field];
      updates[field] = F.delete();
      hadApplication = true;
    }
  }

  if (hadApplication) {
    // Preserve legacy application fields in the private application record. Country
    // may also be used by the public profile, so copy it but do not remove it there.
    if (data.creatorName !== undefined) {
      application.creatorName = data.creatorName;
      // The public displayName remains available after removing the application-only name.
      updates.creatorName = F.delete();
    }
    if (data.country !== undefined && application.country === undefined) {
      application.country = data.country;
    }

    const appRef = db.collection("creatorApplications").doc(userDoc.id);
    const existingApp = await appRef.get();
    const merged = { ...application };
    if (existingApp.exists) {
      const existing = existingApp.data();
      for (const [key, value] of Object.entries(existing)) {
        if (merged[key] === undefined) merged[key] = value;
      }
    }
    await appRef.set(merged, { merge: true });
  }

  const publicCoins = Number(data.coins || 0);
  const publicCoinBalance = Number(data.coinBalance || 0);
  const hadLegacyCoins = Object.prototype.hasOwnProperty.call(data, "coins") ||
    Object.prototype.hasOwnProperty.call(data, "coinBalance");

  if (hadLegacyCoins) {
    const privateRef = db.collection("privateUsers").doc(userDoc.id);
    const privateDoc = await privateRef.get();
    if (!privateDoc.exists || typeof privateDoc.data().coins !== "number") {
      await privateRef.set({
        coins: Math.max(publicCoins, publicCoinBalance),
        migratedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
    }
    updates.coins = F.delete();
    updates.coinBalance = F.delete();
  }

  if (Object.keys(updates).length > 0) {
    await userDoc.ref.update(updates);
  }
}

async function main() {
  let last = null;
  let processed = 0;
  while (true) {
    let query = db.collection("users")
      .orderBy(admin.firestore.FieldPath.documentId())
      .limit(200);
    if (last) query = query.startAfter(last);
    const snap = await query.get();
    if (snap.empty) break;
    for (const userDoc of snap.docs) {
      await migrateOne(userDoc);
      processed += 1;
    }
    last = snap.docs[snap.docs.length - 1];
  }
  console.log("Private user-data migration complete. User documents scanned: " + processed);
}

main().then(() => process.exit(0)).catch((error) => {
  console.error("Private user-data migration failed:", error);
  process.exit(1);
});
