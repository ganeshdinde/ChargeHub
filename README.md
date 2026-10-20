# ChargeHub — Android map increment

This build keeps the Google Maps integration and uses Open Charge Map for real
nearby charging-station locations through the existing provider-adapter flow.

## Included

- Google Maps Compose integration.
- Account gate and dashboard with Nearby, Offers and Wallet entry points.
- Current-location map centering when a device location is available.
- Current-location blue dot when location permission is granted.
- Fresh high-accuracy location on launch, plus lifecycle-bound location updates.
- Automatic station refresh after the user moves 1 km or more; the provider is
  not called for every location update.
- One Google Maps marker per normalized `ChargingStation`.
- Nearby-station discovery controls: 5 km, 10 km, or 25 km search radius.
- Geographic marker clustering to keep dense areas readable, plus tap-to-preview
  for individual stations.
- Station detail views from the map preview or list, including normalized
  connector type/power summaries, in-app directions, and payment entry.
- In-app directions preview; ChargeHub no longer opens an external navigation
  application from a station detail screen.
- Existing nearby charger list preserved below the map.
- Real Open Charge Map nearby-station integration, including request validation,
  timeout handling, API/error reporting, and response normalization.
- Secure API-key wiring through the Google Secrets Gradle Plugin.
- `com.chargehub.app` remains the application ID.

## Google Maps API key setup

Create a file named `secrets.properties` in this project root (same level as `settings.gradle.kts`) and add:

    MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
    OCM_API_KEY=YOUR_OPEN_CHARGE_MAP_API_KEY
    CHARGEHUB_BACKEND_URL=https://api.example.com/

Do not commit `secrets.properties`. It is already excluded by `.gitignore`.

## Payments

ChargeHub uses Razorpay Standard Checkout for UPI, cards, net banking and the
wallets enabled on the merchant account. The Android app never stores a
Razorpay key secret or computes a final amount. It requests a server-created
charge order, opens Checkout, and sends the signed result to the backend for
verification. ChargeHub wallet credit, coupons and gift cards are applied by
the backend before the Razorpay order is created.

The backend must expose these authenticated HTTPS endpoints:

- `POST /v1/charge-orders`: accepts the station/provider IDs and returns
  `razorpayOrderId`, `checkoutKeyId`, `amountPaise`, `currency` and `receipt`.
  It applies wallet, coupon and gift-card credit before creating the Razorpay
  order. If credit fully covers the session, it instead returns
  `paymentRequired: false` and the receipt, so the app does not open Razorpay.
- `POST /v1/charge-orders/{razorpayOrderId}/verify`: accepts the Razorpay
  payment ID and signature, verifies the signature with the server-only key
  secret, captures/records the payment, and returns the receipt.

The backend must also receive Razorpay webhooks and reconcile their status; do
not start a real charging session on a client-side callback alone.

## Account and directions services

The account gate currently preserves only a local signed-in session and never
stores a password. Before production, replace it with an authenticated backend
or identity provider using short-lived tokens. The directions screen stays in
ChargeHub and currently draws a direct live-location-to-station preview. Add a
server-side road-routing endpoint to return route geometry and turn guidance;
do not call a directions web service with an unrestricted key from the app.

The API key should be restricted in Google Cloud to:

- Application restriction: Android apps
- Package name: `com.chargehub.app`
- Debug SHA-1: `10:1A:E9:76:CE:7C:B8:43:D5:0C:DD:74:81:E6:7E:A3:B0:7E:B8:F1`
- API restriction: Maps SDK for Android

## Open Charge Map key setup

Create an Open Charge Map application key in **My Profile → My Apps**, then add
it as `OCM_API_KEY` in `secrets.properties`. It is a different key from the
Google Maps key. The app deliberately shows a specific setup, network, quota,
or provider error instead of treating a failed request as an empty result.

The search radius is 25 km. Open Charge Map is location data and does not
provide authoritative real-time connector availability, so the app labels
connector state as unknown until a live OCPI/CPO feed is added.

For a public production release, put the Open Charge Map key behind the
ChargeHub backend/API gateway. Keys placed in Android builds can be extracted;
the adapter boundary makes that future swap isolated to the data layer.

## Build

Open the project root in Android Studio and let Gradle sync. This project uses Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, and Java 17.

The original archive did not contain the Gradle wrapper JAR/scripts, so Android Studio may offer to regenerate the Gradle wrapper. Accept that option, or use Android Studio's Gradle tooling to sync/build the project.

## Current scope

The real provider integrations, account/payment/routing backend, station aggregation/deduplication, offline cache, vehicle compatibility, charging-session controls and production observability still require backend delivery. The app already contains the client boundaries and user flows for account access, in-app route preview and Razorpay Checkout.
