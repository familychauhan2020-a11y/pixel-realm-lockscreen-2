package com.realm.lockscreen

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            text = "Realm Lockscreen Active\nUpdates nightly after 12:00 AM"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        val btn = Button(this).apply {
            text = "Generate & Apply Lock Screen Now"
            setOnClickListener {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val oneTime = OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(this@MainActivity).enqueue(oneTime)
                status.text = "Queued! Lock screen will update in ~10 seconds."
            }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(status)
            addView(btn)
        })

        scheduleMidnightJob()
    }

    private fun scheduleMidnightJob() {
        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atTime(0, 2)
        val initialDelay = Duration.between(now, nextMidnight).toMinutes()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val dailyWork = PeriodicWorkRequestBuilder<WallpaperWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyRealmWallpaper",
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWork
        )
    }
}

class WallpaperWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val themes = listOf(
                "cozy fantasy tavern interior glowing fireplace retro pixel art 16bit",
                "lone knight overlooking ancient castle valley sunset retro pixel art",
                "mystical glowing forest shrine ruins night stars pixel art fantasy",
                "dragon perched on misty mountain peak retro rpg pixel art",
                "medieval harbor town ships lanterns night pixel art fantasy"
            )
            val prompt = URLEncoder.encode(themes.random(), "UTF-8")
            val seed = Random.nextInt(1, 999999)
            val imageUrl = "https://pollinations.ai"

            val conn = (URL(imageUrl).openConnection() as HttpsURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "RealmLockscreen/1.0")
            }

            if (conn.responseCode != HttpsURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext Result.retry()
            }

            val bitmap = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            conn.disconnect()

            if (bitmap == null) return@withContext Result.retry()

            val wm = WallpaperManager.getInstance(applicationContext)
            wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
