# Certo for Android

Kotlin + Jetpack Compose version of Certo, the IT certification study app by Qortn.

- Sign-in: Clerk (production instance `clerk.qortyn.com`), email/username + password and Google.
- Progress: synced through the `certo-api` Supabase Edge Function (same data shape as iOS, so progress moves between devices).
- Subscriptions: Google Play Billing, product `certo_premium` with base plans `monthly` and `yearly`.
- Question banks: `app/src/main/assets/certs/cert_*.json` (shared with iOS).

GitHub Actions builds the release bundle on every push to `main` and publishes it to the `builds` branch.
The bundle is signed with the upload key before it goes to Google Play.
