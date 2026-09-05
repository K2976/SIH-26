package com.fitforge.app.domain.model

/**
 * Availability state of Android Health Connect on the current device.
 */
sealed interface HealthConnectAvailability {
    /** Health Connect is installed and ready to be used. */
    data object Available : HealthConnectAvailability

    /** Health Connect is supported by the OS (Android 9+) but needs to be installed from Play Store. */
    data object NotInstalled : HealthConnectAvailability

    /** Health Connect is not supported on this device/ROM. */
    data object NotSupported : HealthConnectAvailability
}
