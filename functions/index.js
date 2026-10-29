const admin = require("firebase-admin");
const {
  onCall,
  onRequest,
  HttpsError,
} = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

admin.initializeApp();

const db = admin.firestore();

const CASHFREE_APP_ID = defineSecret("CASHFREE_APP_ID");
const CASHFREE_SECRET_KEY = defineSecret("CASHFREE_SECRET_KEY");

const CASHFREE_BASE_URL = "https://sandbox.cashfree.com";
const CASHFREE_API_VERSION = "2025-01-01";

/*
 * Coin packages
 *
 * ₹15 = temporary TEST package.
 *
 * Normal packages:
 * ₹100  = 100 Coins
 * ₹200  = 220 Coins
 * ₹500  = 600 Coins
 * ₹1000 = 1300 Coins
 */

const COIN_PACKS = {
  "test_15": {
    amount: 15,
    coins: 300,
    test: true,
  },

  "pack_100": {
    amount: 100,
    coins: 100,
    test: false,
  },

  "pack_200": {
    amount: 200,
    coins: 220,
    test: false,
  },

  "pack_500": {
    amount: 500,
    coins: 600,
    test: false,
  },

  "pack_1000": {
    amount: 1000,
    coins: 1300,
    test: false,
  },
};


/*
 * Cashfree API headers
 */
function cashfreeHeaders() {
  return {
    "Content-Type": "application/json",
    Accept: "application/json",
    "x-api-version": CASHFREE_API_VERSION,
    "x-client-id": CASHFREE_APP_ID.value(),
    "x-client-secret": CASHFREE_SECRET_KEY.value(),
  };
}


/*
 * Get selected package
 */
function getPack(data) {
  const packId = String(data?.packId ?? "").trim();

  if (packId && COIN_PACKS[packId]) {
    return COIN_PACKS[packId];
  }

  const amount = Number(data?.amount);

  if (Number.isFinite(amount)) {
    const pack = Object.values(COIN_PACKS).find(
      (item) => item.amount === amount
    );

    return pack || null;
  }

  return null;
}


/*
 * Get customer phone
 */
function getCustomerPhone(request) {
  const fromData = String(
    request.data?.phone || ""
  ).replace(/\D/g, "");

  if (fromData.length >= 10) {
    return fromData.slice(-10);
  }

  const fromAuth = String(
    request.auth?.token?.phone_number || ""
  ).replace(/\D/g, "");

  if (fromAuth.length >= 10) {
    return fromAuth.slice(-10);
  }

  return "";
}


/*
 * Health check
 */
exports.healthCheck = onRequest(
  {
    region: "asia-south1",
  },
  (request, response) => {
    response
      .status(200)
      .send("VideoCallLive Firebase Functions working");
  }
);


/*
 * CREATE CASHFREE ORDER
 */
exports.createCashfreeOrder = onCall(
  {
    region: "asia-south1",
    secrets: [
      CASHFREE_APP_ID,
      CASHFREE_SECRET_KEY,
    ],
  },

  async (request) => {

    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please login first."
      );
    }

    const pack = getPack(request.data);

    if (!pack) {
      throw new HttpsError(
        "invalid-argument",
        "Invalid coin pack."
      );
    }

    const uid = request.auth.uid;

    const phone = getCustomerPhone(request);

    if (!phone) {
      throw new HttpsError(
        "invalid-argument",
        "Valid 10 digit phone number required."
      );
    }

    const orderId =
      "VCL_" +
      uid.substring(0, 8) +
      "_" +
      Date.now() +
      "_" +
      Math.random()
        .toString(36)
        .substring(2, 8);

    const orderRef = db
      .collection("paymentOrders")
      .doc(orderId);

    await orderRef.set({
      uid: uid,

      packId: String(
        request.data?.packId || pack.amount
      ),

      amount: pack.amount,

      coins: pack.coins,

      isTestPack: pack.test,

      status: "CREATED",

      environment: "sandbox",

      createdAt:
        admin.firestore.FieldValue.serverTimestamp(),
    });

    try {

      const response = await fetch(
        CASHFREE_BASE_URL + "/pg/orders",
        {
          method: "POST",

          headers: cashfreeHeaders(),

          body: JSON.stringify({

            order_id: orderId,

            order_amount: pack.amount,

            order_currency: "INR",

            customer_details: {
              customer_id: uid,
              customer_phone: phone,
            },

            order_note:
              pack.coins +
              " VideoCallLive Coins",

            order_tags: {
              uid: uid,

              pack_id: String(
                request.data?.packId ||
                pack.amount
              ),

              coins: String(pack.coins),

              test_pack: String(pack.test),
            },
          }),
        }
      );

      const result = await response.json();

      if (
        !response.ok ||
        !result.payment_session_id
      ) {

        console.error(
          "Cashfree create order error:",
          response.status,
          JSON.stringify(result)
        );

        await orderRef.update({
          status: "CREATE_FAILED",

          cashfreeResponse: result,

          updatedAt:
            admin.firestore.FieldValue.serverTimestamp(),
        });

        throw new HttpsError(
          "internal",
          "Unable to create Cashfree payment order."
        );
      }

      await orderRef.update({

        status: "CHECKOUT_READY",

        paymentSessionId:
          result.payment_session_id,

        updatedAt:
          admin.firestore.FieldValue.serverTimestamp(),
      });

      return {

        success: true,

        orderId: orderId,

        paymentSessionId:
          result.payment_session_id,

        amount: pack.amount,

        coins: pack.coins,

        isTestPack: pack.test,
      };

    } catch (error) {

      if (error instanceof HttpsError) {
        throw error;
      }

      console.error(
        "createCashfreeOrder failed:",
        error
      );

      await orderRef.update({

        status: "CREATE_FAILED",

        updatedAt:
          admin.firestore.FieldValue.serverTimestamp(),
      });

      throw new HttpsError(
        "internal",
        "Unable to create Cashfree payment order."
      );
    }
  }
);


/*
 * VERIFY CASHFREE PAYMENT
 */
exports.verifyCashfreePayment = onCall(
  {
    region: "asia-south1",

    secrets: [
      CASHFREE_APP_ID,
      CASHFREE_SECRET_KEY,
    ],
  },

  async (request) => {

    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please login first."
      );
    }

    const orderId = String(
      request.data?.orderId || ""
    ).trim();

    if (!orderId) {
      throw new HttpsError(
        "invalid-argument",
        "orderId is required."
      );
    }

    const orderRef = db
      .collection("paymentOrders")
      .doc(orderId);

    const orderSnap =
      await orderRef.get();

    if (!orderSnap.exists) {
      throw new HttpsError(
        "not-found",
        "Payment order not found."
      );
    }

    const order = orderSnap.data();

    if (order.uid !== request.auth.uid) {
      throw new HttpsError(
        "permission-denied",
        "This payment order belongs to another user."
      );
    }

    if (order.status === "PAID") {

      return {

        success: true,

        alreadyCredited: true,

        orderId: orderId,

        coins: Number(
          order.coins || 0
        ),
      };
    }

    try {

      const response = await fetch(

        CASHFREE_BASE_URL +
          "/pg/orders/" +
          encodeURIComponent(orderId) +
          "/payments",

        {
          method: "GET",

          headers: cashfreeHeaders(),
        }
      );

      const payments =
        await response.json();

      if (
        !response.ok ||
        !Array.isArray(payments)
      ) {

        console.error(
          "Cashfree verification error:",
          response.status,
          JSON.stringify(payments)
        );

        throw new HttpsError(
          "internal",
          "Unable to verify Cashfree payment."
        );
      }

      const successfulPayment =
        payments.find(
          (payment) =>
            payment.payment_status ===
            "SUCCESS"
        );

      if (!successfulPayment) {

        const pendingPayment =
          payments.find(
            (payment) =>
              payment.payment_status ===
              "PENDING"
          );

        return {

          success: false,

          status: pendingPayment
            ? "PENDING"
            : "FAILED",

          orderId: orderId,

          coins: 0,
        };
      }

      const userRef = db
        .collection("privateUsers")
        .doc(request.auth.uid);
      const legacyUserRef = db
        .collection("users")
        .doc(request.auth.uid);

      await db.runTransaction(
        async (transaction) => {

          const freshOrderSnap =
            await transaction.get(
              orderRef
            );

          if (!freshOrderSnap.exists) {
            throw new HttpsError(
              "not-found",
              "Payment order not found."
            );
          }

          const freshOrder =
            freshOrderSnap.data();

          if (
            freshOrder.uid !==
            request.auth.uid
          ) {

            throw new HttpsError(
              "permission-denied",
              "This payment order belongs to another user."
            );
          }

          if (
            freshOrder.status ===
            "PAID"
          ) {
            return;
          }

          // Read both balance locations before writing. If this account has not
          // yet been migrated, seed the private balance from its legacy balance
          // exactly once as part of the same transaction that credits the purchase.
          const userSnap = await transaction.get(userRef);
          const legacyUserSnap = await transaction.get(legacyUserRef);
          const privateData = userSnap.exists ? userSnap.data() : {};
          const legacyData = legacyUserSnap.exists ? legacyUserSnap.data() : {};
          const hasPrivateCoins = typeof privateData.coins === "number";
          const legacyCoins = Math.max(
            Number(legacyData.coins || 0),
            Number(legacyData.coinBalance || 0)
          );
          const currentCoins = hasPrivateCoins
            ? privateData.coins
            : legacyCoins;

          const coinsToAdd =
            Number(
              freshOrder.coins || 0
            );

          const newCoins =
            currentCoins +
            coinsToAdd;

          transaction.set(

            userRef,

            {
              coins: newCoins,
              ...(hasPrivateCoins ? {} : {
                migratedAt: admin.firestore.FieldValue.serverTimestamp(),
                legacyCoinsMigrated: true,
              }),
              updatedAt:
                admin.firestore.FieldValue
                  .serverTimestamp(),
            },

            {
              merge: true,
            }
          );

          transaction.update(

            orderRef,

            {
              status: "PAID",

              paymentId:
                successfulPayment
                  .cf_payment_id || null,

              paymentStatus:
                successfulPayment
                  .payment_status,

              paidAt:
                admin.firestore.FieldValue
                  .serverTimestamp(),

              updatedAt:
                admin.firestore.FieldValue
                  .serverTimestamp(),
            }
          );
        }
      );

      return {

        success: true,

        alreadyCredited: false,

        orderId: orderId,

        coins: Number(
          order.coins || 0
        ),
      };

    } catch (error) {

      if (error instanceof HttpsError) {
        throw error;
      }

      console.error(
        "verifyCashfreePayment failed:",
        error
      );

      throw new HttpsError(
        "internal",
        "Unable to verify Cashfree payment."
      );
    }
  }
);


/*
 * GET COIN BALANCE
 */
exports.getCoinBalance = onCall(
  {
    region: "asia-south1",
  },

  async (request) => {

    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please login first."
      );
    }

    const uid = request.auth.uid;
    const privateSnap = await db.collection("privateUsers").doc(uid).get();
    const privateData = privateSnap.exists ? privateSnap.data() : {};

    // During the staged rollout, some accounts may still have their balance
    // only in the legacy public document. Prefer a real private balance, but
    // fall back to legacy data when no numeric private balance exists.
    if (typeof privateData.coins === "number") {
      return { coins: privateData.coins };
    }

    const legacySnap = await db.collection("users").doc(uid).get();
    const legacyData = legacySnap.exists ? legacySnap.data() : {};
    return {
      coins: Math.max(
        Number(legacyData.coins || 0),
        Number(legacyData.coinBalance || 0)
      ),
    };
  }
);

/*
 * ADMIN ACCESS
 *
 * Only the owner's Firebase UID below can bootstrap the admin claim.
 * This UID is not a password or secret; it only identifies the owner account.
 */
const OWNER_ADMIN_UID = "cmnDofW7OcYP2FvTV2KA1uTARM22";

exports.bootstrapAdmin = onCall(
  {
    region: "asia-south1",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please login first."
      );
    }

    if (request.auth.uid !== OWNER_ADMIN_UID) {
      throw new HttpsError(
        "permission-denied",
        "Admin bootstrap is not available for this account."
      );
    }

    await admin.auth().setCustomUserClaims(
      OWNER_ADMIN_UID,
      { admin: true }
    );

    return {
      success: true,
      admin: true,
      uid: OWNER_ADMIN_UID,
    };
  }
);


/*
 * Delete the authenticated user's app account and associated app-side data.
 * This operation is intentionally irreversible and requires an explicit confirmation.
 */
async function deleteDocumentsForQuery(query) {
  const snapshot = await query.get();
  for (const document of snapshot.docs) {
    await db.recursiveDelete(document.ref);
  }
}

async function deleteStoragePrefix(prefix) {
  try {
    await admin.storage().bucket().deleteFiles({ prefix });
  } catch (error) {
    if (Number(error.code) === 404) return;
    console.error("Account deletion storage cleanup failed for prefix " + prefix, error);
    throw new HttpsError("internal", "Stored photos could not be deleted. Please retry.");
  }
}

exports.deleteMyAccount = onCall(
  { region: "asia-south1", timeoutSeconds: 540, memory: "1GiB" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Please sign in before deleting your account.");
    }
    if (request.data?.confirmation !== "DELETE") {
      throw new HttpsError("failed-precondition", "Explicit deletion confirmation is required.");
    }

    const uid = request.auth.uid;
    const provider = request.auth.token?.firebase?.sign_in_provider || "unknown";
    const authTime = Number(request.auth.token?.auth_time || 0);
    if (provider !== "anonymous" && (!authTime || Date.now() / 1000 - authTime > 15 * 60)) {
      throw new HttpsError(
        "failed-precondition",
        "For account safety, sign out and sign back in before deleting this account."
      );
    }

    // Delete stored user media first. If storage cleanup fails, keep the Auth account
    // so the owner can retry rather than reporting a false success.
    await deleteStoragePrefix("profilePhotos/" + uid + "/");
    await deleteStoragePrefix("creatorApplications/" + uid + "/");

    // Delete both current participant-indexed chats and legacy deterministic room
    // IDs from before the participants field was added. Account deletion is rare;
    // this bounded-purpose scan avoids leaving old messages under an orphaned room.
    const allChats = await db.collection("chats").get();
    const chatRefs = new Map();
    for (const chat of allChats.docs) {
      const participants = chat.get("participants");
      const idMatches = chat.id.startsWith(uid + "_") || chat.id.endsWith("_" + uid);
      if ((Array.isArray(participants) && participants.includes(uid)) || idMatches) {
        chatRefs.set(chat.ref.path, chat.ref);
      }
    }
    for (const ref of chatRefs.values()) await db.recursiveDelete(ref);

    const callQueries = await Promise.all([
      db.collection("calls").where("callerId", "==", uid).get(),
      db.collection("calls").where("receiverId", "==", uid).get(),
    ]);
    const callRefs = new Map();
    for (const snapshot of callQueries) {
      for (const document of snapshot.docs) callRefs.set(document.ref.path, document.ref);
    }
    for (const ref of callRefs.values()) await db.recursiveDelete(ref);

    await deleteDocumentsForQuery(
      db.collection("supportTickets").where("uid", "==", uid)
    );
    await deleteDocumentsForQuery(
      db.collection("paymentOrders").where("uid", "==", uid)
    );

    await db.recursiveDelete(db.collection("creatorApplications").doc(uid));
    await db.recursiveDelete(db.collection("privateUsers").doc(uid));
    await db.recursiveDelete(db.collection("users").doc(uid));

    try {
      await admin.auth().deleteUser(uid);
    } catch (error) {
      if (error.code !== "auth/user-not-found") {
        console.error("Account deletion Auth cleanup failed for UID " + uid, error);
        throw new HttpsError("internal", "App data was removed, but account closure did not finish. Please contact support.");
      }
    }

    return { success: true, deleted: true };
  }
);


// Push notifications for new one-to-one chat messages.
// The Android app stores FCM registration tokens in users/{uid}.fcmTokens.
const { onDocumentCreated } = require("firebase-functions/v2/firestore");

exports.chatMessageNotification = onDocumentCreated(
  {
    document: "chats/{chatId}/messages/{messageId}",
    region: "asia-south1",
  },
  async (event) => {
    const message = event.data?.data();
    if (!message) return;
    const receiverId = String(message.receiverId || "");
    const senderId = String(message.senderId || "");
    const body = String(message.text || "").trim();
    if (!receiverId || !senderId || receiverId === senderId || !body) return;

    const userRef = db.collection("users").doc(receiverId);
    const userSnap = await userRef.get();
    const rawTokens = userSnap.get("fcmTokens");
    const tokens = Array.isArray(rawTokens)
      ? [...new Set(rawTokens.filter((token) => typeof token === "string" && token.length > 20))]
      : [];
    if (!tokens.length) return;

    const senderSnap = await db.collection("users").doc(senderId).get();
    const senderName = String(senderSnap.get("displayName") || "New message").slice(0, 80);
    const result = await admin.messaging().sendEachForMulticast({
      tokens,
      notification: {
        title: senderName,
        body: body.length > 180 ? body.slice(0, 177) + "..." : body,
      },
      data: {
        type: "chat_message",
        chatId: String(event.params.chatId),
        senderId,
        receiverId,
      },
      android: {
        priority: "high",
        notification: { channelId: "chat_messages", sound: "default" },
      },
    });

    const invalid = [];
    result.responses.forEach((response, index) => {
      const code = response.error?.code || "";
      if (code.includes("registration-token-not-registered") ||
          code.includes("invalid-registration-token")) invalid.push(tokens[index]);
    });
    if (invalid.length) {
      await userRef.update({
        fcmTokens: admin.firestore.FieldValue.arrayRemove(...invalid),
      });
    }
  }
);
