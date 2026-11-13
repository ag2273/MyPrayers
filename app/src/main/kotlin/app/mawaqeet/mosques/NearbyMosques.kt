package app.mawaqeet.mosques

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.mawaqeet.R

/**
 * Nearby mosques (#89): hands off to the user's maps app with a search for mosques. The `geo:0,0` search carries no
 * coordinates from the app, so the maps app uses its own location; the app sends nothing and needs no permission.
 */
object NearbyMosques {
    /** The maps search for [query] («مسجد», "mosque") around wherever the maps app finds the user. */
    fun intent(query: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))

    /** Opens the maps app with [context]'s language's query; with no app to handle it, a short message instead. */
    fun open(context: Context) {
        try {
            context.startActivity(intent(context.getString(R.string.mosques_query)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.mosques_no_maps_app, Toast.LENGTH_SHORT).show()
        }
    }
}
