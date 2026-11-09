package app.mawaqeet.ui.location

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.mawaqeet.R
import app.mawaqeet.location.DeviceLocation

/** Starts the location permission flow returned by [rememberLocationPermissionRequest]. */
fun interface LocationPermissionRequest {
    fun start()
}

/**
 * The approximate-location permission flow: if already granted, [onResult] (true) at once; otherwise a short
 * explanation (only used to calculate Prayer Times, never leaves the phone), then the system request for
 * `ACCESS_COARSE_LOCATION` only. "Not now" or a denial gives `false`: the caller falls back to city search.
 * Call it where the dialog may appear (it is part of the composition); onboarding (#29) reuses it.
 */
@Composable
fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): LocationPermissionRequest {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var explaining by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult(granted)
    }
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false; currentOnResult(false) },
            title = { Text(stringResource(R.string.location_permission_title)) },
            text = { Text(stringResource(R.string.location_permission_explanation)) },
            confirmButton = {
                TextButton(onClick = { explaining = false; launcher.launch(DeviceLocation.PERMISSION) }) {
                    Text(stringResource(R.string.location_permission_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { explaining = false; currentOnResult(false) }) {
                    Text(stringResource(R.string.not_now))
                }
            },
        )
    }
    return remember(context) {
        LocationPermissionRequest {
            if (DeviceLocation.isGranted(context)) currentOnResult(true) else explaining = true
        }
    }
}
