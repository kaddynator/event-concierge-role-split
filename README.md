# Event Concierge

Native Android MVP built with Kotlin, Jetpack Compose, and Material 3.

## Local development

1. Open this directory in Android Studio.
2. Use the bundled Gradle wrapper when prompted.
3. Select an Android 24+ emulator or device.
4. Run the `app` configuration.

The app is connected to Firebase project `gen-lang-client-0128227025` through
`app/google-services.json`. Debug builds use the Firebase App Check debug
provider; each new development device must have its generated token registered
under Firebase Console → App Check → Apps → Manage debug tokens.

## App roles

- **Attendee:** concierge, schedule, and privacy-safe self check-in.
- **Organizer:** operational overview, full guest roster, and issue management.

The current role gate separates the two interfaces for a demo session. It is not
yet a production security boundary.

## Active cloud features

- Firebase Anonymous Authentication creates the current app session.
- Cloud Firestore stores role activity, check-ins, guest messages, and issues.
- Firebase AI Logic uses Gemini 3.5 Flash for typed concierge answers.
- Gemini Live uses the native-audio model for two-way voice conversations.
- The organizer can hand off an event to the phone's calendar, draft an email in
  Gmail, or share a briefing file to installed apps such as Drive and Google Chat
  through standard Android intents. These handoffs use the accounts already
  signed into the phone and do not require a second OAuth flow.

## Before production

1. Replace Anonymous Authentication with Google sign-in or another identity
   provider.
2. Store the attendee/organizer role in protected user data or a custom claim,
   then enforce it in Firestore Security Rules.
3. Register Play Integrity as the production App Check provider; never ship a
   debug build or debug token.
4. Replace the demo event facts and in-memory roster with organizer-managed
   Firestore data.
5. Add direct Google Workspace APIs only if background sync is required; the
   first version intentionally uses user-confirmed Android handoffs.
