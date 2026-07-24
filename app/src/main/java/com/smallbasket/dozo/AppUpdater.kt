package com.smallbasket.dozo

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.smallbasket.dozo.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object AppUpdater {
    private const val JSON_URL = "https://github.com/kartheek0107/dozo/blob/master/update.json"

    fun checkForUpdates(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonString = URL(JSON_URL).readText()
                val json = JSONObject(jsonString)
                val latestCode = json.getInt("latestVersionCode")
                val downloadUrl = json.getString("url")

                // Compare with current version from build.gradle.kts
                val currentCode = BuildConfig.VERSION_CODE

                if (latestCode > currentCode) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(context, downloadUrl)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpdateDialog(context: Context, url: String) {
        AlertDialog.Builder(context)
            .setTitle("New Update Available")
            .setMessage("A new version of Dozo is available. Would you like to update?")
            .setPositiveButton("Update Now") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }
}