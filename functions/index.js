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

          const userSnap =
            await transaction.get(
              userRef
            );

          const currentCoins =
            Number(
              userSnap.exists
                ? userSnap.data().coins || 0
                : 0
            );

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

    const snap = await db
      .collection("users")
      .doc(request.auth.uid)
      .get();

    return {

      coins: Number(
        snap.exists
          ? snap.data().coins || 0
          : 0
      ),
    };
  }
);
