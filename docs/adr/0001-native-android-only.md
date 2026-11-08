# Native Android only for v1

v1 is a native Android app (Kotlin, Jetpack Compose, Jetpack Glance widgets), with no iOS and no cross-platform framework. Native gives full access to the platform features the app depends on most: exact alarms, foreground services for the adhan, and home-screen widgets. It also keeps the app relatively small.

## Considered Options

- **Cross-platform (Flutter, React Native, Kotlin Multiplatform with Compose Multiplatform)**: rejected for v1. Widgets and reliable alarms would still need native code per platform, and the runtime adds app size, while the only gain would be iOS, which v1 doesn't target.

## Consequences

Adding iOS later means a separate native app (or adopting Kotlin Multiplatform for shared calculation and Prayer Log logic). Keeping the domain logic free of Android framework types keeps that door open.
