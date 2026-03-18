package com.example.youtubepro

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var isHome = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ইঞ্জিন অটো-আপডেট
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Engine Updated & Ready!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Engine Update Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val toolbarBtn = findViewById<TextView>(R.id.btnDownloads)
        val homeSection = findViewById<LinearLayout>(R.id.homeSection)
        val downloadsSection = findViewById<ScrollView>(R.id.downloadsSection)
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)
        val btnFind = findViewById<Button>(R.id.btnFind)
        val btnDownloadStart = findViewById<Button>(R.id.btnDownloadStart)
        val etLink = findViewById<EditText>(R.id.etLink)
        
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        toolbarBtn.setOnClickListener {
            if (isHome) {
                homeSection.visibility = View.GONE
                downloadsSection.visibility = View.VISIBLE
                toolbarBtn.text = "Home"
            } else {
                homeSection.visibility = View.VISIBLE
                downloadsSection.visibility = View.GONE
                toolbarBtn.text = "Downloads"
            }
            isHome = !isHome
        }

        btnFind.setOnClickListener {
            val link = etLink.text.toString().trim()
            if (link.isEmpty()) {
                Toast.makeText(this, "Please enter a link!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            btnFind.text = "Extracting Real Data..."
            btnFind.isEnabled = false
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val request = YoutubeDLRequest(link)
                    val info = YoutubeDL.getInstance().getInfo(request)
                    
                    val videoQualities = LinkedHashSet<String>()
                    val audioTracks = LinkedHashSet<String>()

                    info.formats?.forEach { format ->
                        if (!format.vcodec.isNullOrEmpty() && format.vcodec != "none") {
                            videoQualities.add("${format.height}p")
                        }
                        if (!format.acodec.isNullOrEmpty() && format.acodec != "none") {
                            if (!format.language.isNullOrEmpty()) {
                                audioTracks.add("Audio (${format.language})")
                            } else {
                                audioTracks.add("Original Audio")
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        btnFind.text = "Find Qualities"
                        btnFind.isEnabled = true
                        generateDynamicOptions(videoQualities.toList(), audioTracks.toList())
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnFind.text = "Find Qualities"
                        btnFind.isEnabled = true
                        Toast.makeText(this@MainActivity, "Failed to extract! Bad link or restricted.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnDownloadStart.setOnClickListener {
            val rgVideo = findViewById<RadioGroup>(R.id.rgVideo)
            val rgAudio = findViewById<RadioGroup>(R.id.rgAudio)

            val selectedVideoId = rgVideo.checkedRadioButtonId
            val selectedAudioId = rgAudio.checkedRadioButtonId

            if (selectedVideoId == -1 || selectedAudioId == -1) {
                Toast.makeText(this, "Please select options!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedVideo = findViewById<RadioButton>(selectedVideoId).text.toString()
            val selectedAudio = findViewById<RadioButton>(selectedAudioId).text.toString()

            if (selectedVideo.contains("Skip") && selectedAudio.contains("Skip")) {
                Toast.makeText(this, "You cannot skip both!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val details = "Video: $selectedVideo | Audio: $selectedAudio"
            addDownloadItem(details)
            
            Toast.makeText(this, "Download Added!", Toast.LENGTH_SHORT).show()
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            etLink.setText("")
            
            if (isHome) {
                toolbarBtn.performClick()
            }
        }
    }

    private fun generateDynamicOptions(videos: List<String>, audios: List<String>) {
        val rgVideo = findViewById<RadioGroup>(R.id.rgVideo)
        val rgAudio = findViewById<RadioGroup>(R.id.rgAudio)

        rgVideo.removeAllViews()
        rgAudio.removeAllViews()

        for ((index, q) in videos.withIndex()) {
            val rb = RadioButton(this).apply { text = q; setTextColor(Color.BLACK); id = View.generateViewId() }
            if (index == 0) rb.isChecked = true
            rgVideo.addView(rb)
        }
        val skipVid = RadioButton(this).apply { text = "Skip Video"; setTextColor(Color.BLACK); id = View.generateViewId() }
        rgVideo.addView(skipVid)

        if (audios.isEmpty()) {
            val orig = RadioButton(this).apply { text = "Original Audio"; setTextColor(Color.BLACK); id = View.generateViewId(); isChecked = true }
            rgAudio.addView(orig)
        } else {
            for ((index, a) in audios.withIndex()) {
                val rb = RadioButton(this).apply { text = a; setTextColor(Color.BLACK); id = View.generateViewId() }
                if (index == 0) rb.isChecked = true
                rgAudio.addView(rb)
            }
        }
        val skipAud = RadioButton(this).apply { text = "Skip Audio"; setTextColor(Color.BLACK); id = View.generateViewId() }
        rgAudio.addView(skipAud)
    }

    private fun addDownloadItem(info: String) {
        val container = findViewById<LinearLayout>(R.id.downloadsContainer)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#BBDEFB"))
            setPadding(30, 30, 30, 30)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, 20)
            layoutParams = params
            gravity = Gravity.CENTER_VERTICAL
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(this).apply { text = "Extracted Video"; textSize = 16f; setTextColor(Color.BLACK); setTypeface(null, android.graphics.Typeface.BOLD) }
        val desc = TextView(this).apply { text = info; textSize = 12f; setTextColor(Color.DKGRAY) }
        val status = TextView(this).apply { text = "Downloading..."; setTextColor(Color.parseColor("#1976D2")); setTypeface(null, android.graphics.Typeface.BOLD) }

        textLayout.addView(title)
        textLayout.addView(desc)
        textLayout.addView(status)

        val menuBtn = TextView(this).apply { text = "⋮"; textSize = 28f; setTextColor(Color.BLACK); setPadding(30, 0, 20, 0) }
        
        menuBtn.setOnClickListener {
            val popup = PopupMenu(this, menuBtn)
            popup.menu.add("Save to Device")
            popup.menu.add("Pause/Resume")
            popup.menu.add("Delete")
            
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Delete" -> { container.removeView(card); Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show() }
                    else -> Toast.makeText(this, "${item.title} selected!", Toast.LENGTH_SHORT).show()
                }
                true
            }
            popup.show()
        }

        card.addView(textLayout)
        card.addView(menuBtn)
        container.addView(card, 0)
    }
}
