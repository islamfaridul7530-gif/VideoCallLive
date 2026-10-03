const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

const CASHFREE_APP_ID = defineSecret("CASHFREE_APP_ID");
const CASHFREE_SECRET_KEY = defineSecret("CASHFREE_SECRET_KEY");

exports.createCashfreeOrder = onCall(
  {
    region: "asia-south1",
    secrets: [CASHFREE_APP_ID, CASHFREE_SECRET_KEY]
  },
  async (request) => {

    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Please login first."
      );
    }

    const packId = String(request.data?.packId || "");

    // Price and coins are decided ONLY by backend.
    const packs = {
      test_15: {
        amount: 15,
        coins: 300
      },
      pack_100: {
        amount: 100,
        coins: 100
      },
      pack_200: {
        amount: 200,
        coins: 220
      },
      pack_500: {
        amount: 500,
        coins: 600
      },
      pack_1000: {
        amount: 1000,
        coins: 1300
      }
    };

    const pack = packs[packId];

    if (!pack) {
      throw new HttpsError(
        "invalid-argument",
        "Invalid coin pack."
      );
    }

    const uid = request.auth.uid;

    const orderId =
      "VCL_" +
      uid.substring(0, 8) +
      "_" +
      Date.now();

    const phone =
      String(request.data?.phone || "")
        .replace(/\D/g, "")
        .slice(-10);

    if (phone.length !== 10) {
      throw new HttpsError(
        "invalid-argument",
        "Valid 10 digit phone number required."
      );
    }

    const response = await fetch(
      "https://sandbox.cashfree.com/pg/orders",
      {
        method: "POST",

        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
          "x-api-version": "2025-01-01",
          "x-client-id": CASHFREE_APP_ID.value(),
          "x-client-secret": CASHFREE_SECRET_KEY.value()
        },

        body: JSON.stringify({
          order_id: orderId,
          order_amount: pack.amount,
          order_currency: "INR",

          customer_details: {
            customer_id: uid,
            customer_phone: phone
          },

          order_note:
            pack.coins + " Video Call Live Coins",

          order_tags: {
            uid: uid,
            pack_id: packId,
            coins: String(pack.coins)
          }
        })
      }
    );

    const result = await response.json();

    if (!response.ok) {
      console.error(
        "Cashfree create order error",
        response.status,
        result
      );

      throw new HttpsError(
        "internal",
        "Unable to create payment order."
      );
    }

    return {
      orderId: result.order_id,
      paymentSessionId: result.payment_session_id,
      amount: pack.amount,
      coins: pack.coins
    };
  }
);
