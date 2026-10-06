const { onRequest } = require("firebase-functions/v2/https");

exports.healthCheck = onRequest((request, response) => {
  response.status(200).send("VideoCallLive Firebase Functions working");
});
