# Privacy Policy — VideoCallLive

**Draft updated: 21 October 2026.** This policy is prepared for review. Do not submit it to Google Play until the app changes described here have been deployed and tested and the external account-deletion page is publicly accessible.

VideoCallLive is an adults-only social communication app for profiles, messaging and live audio/video calls. This policy describes information handled by the current app source and its Firebase/Cashfree backend.

## Information we handle

- **Account and profile information:** Firebase account identifier; authentication type; phone number when you choose phone-based sign-in or provide a number for checkout; display name, gender, age, country, biography, profile photographs, online/last-seen status and creator profile details that you choose to publish.
- **Messages and calls:** chat text, sender/recipient account IDs and timestamps; call participant IDs, call status and timing, and technical signalling information needed to establish a call. When you start or accept a call, your camera and microphone provide a live stream to the other participant. The reviewed app source does not contain a feature that intentionally records and uploads call audio/video as a media file. The other participant may still capture or record content using their device or other tools.
- **Creator applications:** if you apply to be a creator, your submitted name, age, country, introduction and selfie are stored for application review. The on-device face check only checks that one face is visible; it does **not** verify identity, age or liveness.
- **Support requests:** category, written complaint, account ID and timestamps. The current app allows choosing a screenshot, but the reviewed code stores only its local URI and does not upload the screenshot.
- **Payments and virtual coins:** order ID, selected coin package, amount, coin quantity, status and payment identifiers. When checkout is used, your phone number and order details are sent to Cashfree to process or verify the payment. The current source uses Cashfree's **sandbox/test environment**; the payment integration must be reviewed before accepting live payments.
- **Local diagnostics:** the app keeps the most recent crash stack trace on your device for troubleshooting; the reviewed code does not automatically upload this stored trace.

## Why we use information

We use information to create and restore accounts, show profiles, deliver messages, connect and maintain calls, handle creator applications, display coin balances, process and verify payments, provide support, protect the service against misuse, and diagnose app failures.

## Who may receive information

- **Other VideoCallLive users:** the profile information and photos you publish, messages you send, and live audio/video you share during a call are available to the relevant recipients or call participants.
- **Google Firebase:** authentication, database, file storage and callable backend functions use Firebase services. Information is stored and processed by those services to run the app.
- **Cashfree:** when a payment flow is used, relevant checkout details are shared with Cashfree. Cashfree handles its own processing and retention under its terms and policies.
- **Connectivity providers and call peers:** technical network information is processed to establish live calls. Depending on the connection method, connection details such as IP addresses may be exposed to the other call participant.

We do not sell personal information. The reviewed source does not configure a third-party advertising SDK. Service providers may process technical information under their own terms.

## Camera, microphone and photos

Camera and microphone access are used for live calls. A photo or selfie is accessed only when you use the associated profile or creator-application feature. Creator-application selfies are stored for authorised review. Do not upload images you do not have permission to share.

## Retention and deletion

We retain account and service information while needed to operate the service. The app is being updated to provide an in-app **Profile → Delete Account** action. Once the deletion update is deployed, using it will remove the account from Firebase Authentication and delete the account's profile, creator-application data, stored profile/selfie files, app-side payment-order records, support tickets, chats and call records from our app backend. Deletion affects shared chat/call history stored by VideoCallLive for the involved participants. Deletion is not reversible.

Cashfree and other service providers may retain records they are required to keep under their own policies or applicable law. Information already seen, copied or recorded by another user outside our service cannot be retrieved by us. Temporary provider backups may take time to expire.

## Security

We use Firebase authentication and access rules to limit access to account data, and use service providers to operate the app. No online service can guarantee absolute security. Do not share passwords or one-time codes, and report suspicious activity through Help & Support.

## Age restriction

VideoCallLive is intended for adults aged 18 and over. Do not use the app if you are under 18.

## Your choices

You may edit profile information in the app, stop granting camera/microphone access in Android settings, and request account deletion using **Profile → Delete Account** after the deletion update is installed. For a question about this policy or a deletion request when you cannot access the app, email the address below. This mailbox should be monitored regularly:

**Privacy/support email: [videocalllive7530support@gmail.com](mailto:videocalllive7530support@gmail.com)**

For privacy-related enquiries, use the subject “VideoCallLive Privacy Request”. Do not send passwords, OTPs or payment credentials in a support request.

## Changes to this policy

We may update this policy when the app or its data practices change. The current version will be published at this URL, with the revision date shown above.
