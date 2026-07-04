package ame.project.n2live

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ame.project.n2live.data.*
import ame.project.n2live.gl.SpriteGLSurfaceView
import ame.project.n2live.ui.LayerAdapter
import ame.project.n2live.ui.SourceFileAdapter
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var config: AvatarConfig
    private lateinit var layerAdapter: LayerAdapter
    private lateinit var recyclerLayers: RecyclerView
    
    private lateinit var previewSurface: SpriteGLSurfaceView
    private lateinit var sliderPosX: SeekBar
    private lateinit var sliderPosY: SeekBar
    private lateinit var sliderScale: SeekBar
    private lateinit var sliderPosZ: SeekBar

    private lateinit var spinnerStateName: Spinner
    private lateinit var timelineScrubber: SeekBar
    private lateinit var tvTimelineTime: TextView
    private lateinit var sliderDuration: SeekBar
    private lateinit var tvDurationValue: TextView
    private lateinit var cbLoop: CheckBox
    private lateinit var timelineTracks: LinearLayout
    private lateinit var timelineRuler: LinearLayout
    
    private lateinit var recyclerSource: RecyclerView

    private var scannedFiles: List<String> = emptyList()
    private var selectedLayer: LayerConfig? = null
    private var selectedSlotIndex: Int = -1
    private var selectedTrackLayer: LayerConfig? = null

    private val FPS = 8
    private var totalSeconds = 5
    private var totalSlots = FPS * totalSeconds

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.data
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            AssetScanner.scanAndImport(this, uri)
            refreshSourcePalette()
        }
    }

    private var isPlaying = false
    private val playbackHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            
            val currentSec = previewSurface.getElapsedSec()
            val stateName = spinnerStateName.selectedItem as String
            
            val firstLayer = config.layers.firstOrNull()
            val state = firstLayer?.states?.get(stateName)
            val duration = if (state != null && state.frames.isNotEmpty()) state.frames.size.toFloat() / FPS else totalSeconds.toFloat()
            
            val displaySec = if (state?.loop == true) {
                if (duration > 0) currentSec % duration else 0f
            } else {
                currentSec.coerceAtMost(duration)
            }
            
            val frameIndex = (displaySec * FPS).toInt().coerceIn(0, totalSlots - 1)
            
            runOnUiThread {
                timelineScrubber.progress = frameIndex
                tvTimelineTime.text = "%.2fs".format(displaySec)
                
                if (selectedSlotIndex != frameIndex) {
                    selectedSlotIndex = frameIndex
                    updateTimelineHighlightOnly()
                    updateLayerControlUI()
                }
            }
            
            playbackHandler.postDelayed(this, (1000 / FPS).toLong())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        config = ConfigManager.load(this)
        // Ensure all states have initialized lists (fix for old JSON)
        config.layers.forEach { layer ->
            layer.states.values.forEach { state ->
                if (state.posX == null) state.posX = mutableListOf()
                if (state.posY == null) state.posY = mutableListOf()
                if (state.scale == null) state.scale = mutableListOf()
                if (state.zOrder == null) state.zOrder = mutableListOf()
            }
        }
        scannedFiles = AssetScanner.listImported(this)

        bindViews()
        previewSurface.init(config, fps = 30)
        setupLayers()
        setupPositionControls()
        setupTimelineCore()
        setupSourcePalette()
        
        refreshTimelineUI()
    }

    private fun bindViews() {
        recyclerLayers = findViewById(R.id.recyclerLayers)
        previewSurface = findViewById(R.id.glSurfacePreview)
        sliderPosX = findViewById(R.id.sliderPosX)
        sliderPosY = findViewById(R.id.sliderPosY)
        sliderScale = findViewById(R.id.sliderScale)
        sliderPosZ = findViewById(R.id.sliderPosZ)

        spinnerStateName = findViewById(R.id.spinnerStateName)
        timelineScrubber = findViewById(R.id.timelineScrubber)
        tvTimelineTime = findViewById(R.id.tvTimelineTime)
        sliderDuration = findViewById(R.id.sliderDuration)
        tvDurationValue = findViewById(R.id.tvDurationValue)
        cbLoop = findViewById(R.id.cbLoop)
        timelineTracks = findViewById(R.id.timelineTracks)
        timelineRuler = findViewById(R.id.timelineRuler)
        recyclerSource = findViewById(R.id.recyclerSourceFiles)

        findViewById<Button>(R.id.btnPickFolder).setOnClickListener {
            pickFolderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        }
        findViewById<Button>(R.id.btnAddLayer).setOnClickListener { showAddLayerDialog() }
        findViewById<Button>(R.id.btnSaveConfig).setOnClickListener {
            ConfigManager.save(this, config)
            Toast.makeText(this, "Saved to config.json", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnSaveState).setOnClickListener { updatePreviewFromTimeline() }
        
        val btnPlay = findViewById<Button>(R.id.btnPreviewPlay)
        btnPlay.setOnClickListener {
            isPlaying = !isPlaying
            if (isPlaying) {
                btnPlay.text = "Stop"
                val currentManualSec = timelineScrubber.progress.toFloat() / FPS
                // Pastikan renderer menggunakan state yang dipilih di spinner
                previewSurface.setState(spinnerStateName.selectedItem as String)
                previewSurface.setManualTime(-1f) // Aktifkan mode auto
                playbackHandler.post(playbackRunnable)
            } else {
                btnPlay.text = "Play"
                val currentSec = previewSurface.getElapsedSec()
                previewSurface.setManualTime(currentSec) // Pause
                playbackHandler.removeCallbacks(playbackRunnable)
                refreshTimelineUI() 
            }
        }
        findViewById<Button>(R.id.btnPreviewIdle).setOnClickListener { 
            previewSurface.setManualTime(0f)
            previewSurface.setState("idle") 
        }
        findViewById<Button>(R.id.btnPreviewTalk).setOnClickListener { 
            previewSurface.setManualTime(0f)
            previewSurface.setState("talk") 
        }
        findViewById<Button>(R.id.btnPreviewAngry).setOnClickListener { 
            previewSurface.setManualTime(0f)
            previewSurface.setState("angry") 
        }
    }

    private fun setupLayers() {
        recyclerLayers.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        layerAdapter = LayerAdapter(config.layers, 
            onLayerSelected = { layer ->
                selectedLayer = layer
                updateLayerControlUI()
            },
            onDelete = { layer ->
                config.layers.remove(layer)
                layerAdapter.notifyDataSetChanged()
                refreshTimelineUI()
            }
        )
        recyclerLayers.adapter = layerAdapter
    }

    private fun setupPositionControls() {
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser || selectedLayer == null) return
                val layer = selectedLayer!!
                val stateName = spinnerStateName.selectedItem as String
                val state = layer.states[stateName]
                
                if (selectedSlotIndex >= 0 && state != null) {
                    // Update per-frame values
                    when (s?.id) {
                        R.id.sliderPosX -> state.posX[selectedSlotIndex] = (p - 100) / 100f
                        R.id.sliderPosY -> state.posY[selectedSlotIndex] = (p - 100) / 100f
                        R.id.sliderScale -> state.scale[selectedSlotIndex] = p / 100f
                        R.id.sliderPosZ -> state.zOrder[selectedSlotIndex] = p
                    }
                } else {
                    // Update global layer values
                    when (s?.id) {
                        R.id.sliderPosX -> layer.posX = (p - 100) / 100f
                        R.id.sliderPosY -> layer.posY = (p - 100) / 100f
                        R.id.sliderScale -> layer.scale = p / 100f
                        R.id.sliderPosZ -> layer.zOrder = p
                    }
                }
                updatePreviewFromTimeline()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }
        sliderPosX.setOnSeekBarChangeListener(listener)
        sliderPosY.setOnSeekBarChangeListener(listener)
        sliderScale.setOnSeekBarChangeListener(listener)
        sliderPosZ.setOnSeekBarChangeListener(listener)
    }

    private fun updateLayerControlUI() {
        val layer = selectedLayer ?: return
        findViewById<TextView>(R.id.tvActiveLayerName).text = "Layer: ${layer.name}"
        
        val stateName = spinnerStateName.selectedItem as String
        val state = layer.states[stateName]
        
        // Safety check for null lists from Gson
        val curX = if (selectedSlotIndex >= 0 && state != null) {
            state.posX?.getOrNull(selectedSlotIndex) ?: layer.posX
        } else {
            layer.posX
        }
        
        val curY = if (selectedSlotIndex >= 0 && state != null) {
            state.posY?.getOrNull(selectedSlotIndex) ?: layer.posY
        } else {
            layer.posY
        }
        
        val curS = if (selectedSlotIndex >= 0 && state != null) {
            state.scale?.getOrNull(selectedSlotIndex) ?: layer.scale
        } else {
            layer.scale
        }
        
        val curZ = if (selectedSlotIndex >= 0 && state != null) {
            state.zOrder?.getOrNull(selectedSlotIndex) ?: layer.zOrder
        } else {
            layer.zOrder
        }
        
        // Update slider progress
        sliderPosX.progress = (curX * 100 + 100).toInt()
        sliderPosY.progress = (curY * 100 + 100).toInt()
        sliderScale.progress = (curS * 100).toInt()
        sliderPosZ.progress = curZ
    }

    private fun setupTimelineCore() {
        sliderDuration.progress = totalSeconds
        tvDurationValue.text = "${totalSeconds}s"
        sliderDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                totalSeconds = p.coerceAtLeast(1)
                totalSlots = FPS * totalSeconds
                tvDurationValue.text = "${totalSeconds}s"
                timelineScrubber.max = totalSlots - 1
                refreshTimelineUI()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val states = arrayOf("idle", "talk", "angry")
        spinnerStateName.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, states)
        spinnerStateName.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                refreshTimelineUI()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        timelineScrubber.max = totalSlots - 1
        timelineScrubber.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val sec = p.toFloat() / FPS
                tvTimelineTime.text = "%.2fs".format(sec)
                // HANYA set manual time jika digerakkan oleh USER (mencegah stuck saat play)
                if (fromUser) {
                    previewSurface.setManualTime(sec)
                    selectedSlotIndex = p
                    updateTimelineHighlightOnly()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        cbLoop.setOnCheckedChangeListener { _, isChecked ->
            val stateName = spinnerStateName.selectedItem as String
            config.layers.forEach { layer ->
                layer.states[stateName]?.loop = isChecked
            }
            // Update preview agar renderer tahu perubahan loop
            updatePreviewFromTimeline()
        }
    }

    private fun updateTimelineHighlightOnly() {
        // 1. Update Ruler Highlight (box waktu di paling atas)
        for (i in 0 until timelineRuler.childCount) {
            val tv = timelineRuler.getChildAt(i) as? TextView ?: continue
            // Cek apakah detik ini mengandung frame yang sedang aktif
            val startFrame = i * FPS
            val endFrame = (i + 1) * FPS
            if (selectedSlotIndex in startFrame until endFrame) {
                tv.setBackgroundColor(0x4400FF00.toInt())
            } else {
                tv.setBackgroundColor(0)
            }
        }

        // 2. Update Tracks Highlight (box gambar)
        for (trackIdx in 0 until timelineTracks.childCount) {
            val trackRow = timelineTracks.getChildAt(trackIdx) as? LinearLayout ?: continue
            for (slotIdx in 0 until trackRow.childCount) {
                val slotView = trackRow.getChildAt(slotIdx)
                val overlay = slotView.findViewById<View>(R.id.slotSelectionOverlay)
                // Sorot seluruh kolom jika slotIdx sama dengan frame aktif
                if (selectedSlotIndex == slotIdx) {
                    overlay.visibility = View.VISIBLE
                    overlay.setBackgroundColor(if (selectedTrackLayer == config.layers.getOrNull(trackIdx)) 0xAA00FF00.toInt() else 0x4400FF00.toInt())
                } else {
                    overlay.visibility = View.GONE
                }
            }
        }
    }

    private fun refreshTimelineUI() {
        timelineRuler.removeAllViews()
        timelineTracks.removeAllViews()

        // Slot width in DP is 32 + margins = roughly 34dp. 
        // We'll use px for precise alignment if needed, but let's try fixed width for now.
        val slotWidthPx = (34 * resources.displayMetrics.density).toInt()

        // Build Ruler
        for (i in 0 until totalSeconds) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(slotWidthPx * FPS, ViewGroup.LayoutParams.MATCH_PARENT)
                text = "${i}s"
                textSize = 10f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(10, 0, 0, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_ruler_box)
            }
            timelineRuler.addView(tv)
        }

        val stateName = spinnerStateName.selectedItem as String
        
        // Update loop checkbox based on current state (assuming all layers share loop property for same state)
        val firstLayerState = config.layers.firstOrNull()?.states?.get(stateName)
        cbLoop.isChecked = firstLayerState?.loop ?: false

        // Build Tracks
        for (layer in config.layers) {
            val trackRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val state = layer.states.getOrPut(stateName) { StateConfig(fps = FPS) }
            // Ensure state lists are initialized and match totalSlots
            if (state.frames == null) state.frames = mutableListOf()
            if (state.posX == null) state.posX = mutableListOf()
            if (state.posY == null) state.posY = mutableListOf()
            if (state.scale == null) state.scale = mutableListOf()
            if (state.zOrder == null) state.zOrder = mutableListOf()

            while (state.frames.size < totalSlots) state.frames.add("")
            while (state.posX.size < totalSlots) state.posX.add(null)
            while (state.posY.size < totalSlots) state.posY.add(null)
            while (state.scale.size < totalSlots) state.scale.add(null)
            while (state.zOrder.size < totalSlots) state.zOrder.add(null)
            
            for (i in 0 until totalSlots) {
                val slotView = LayoutInflater.from(this).inflate(R.layout.item_timeline_slot, trackRow, false)
                val thumb = slotView.findViewById<ImageView>(R.id.imgSlotThumb)
                val overlay = slotView.findViewById<View>(R.id.slotSelectionOverlay)
                
                val fileName = state.frames.getOrNull(i) ?: ""
                if (fileName.isNotEmpty()) {
                    val file = File(ConfigManager.spriteDir(this), fileName)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 8 })
                        thumb.setImageBitmap(bitmap)
                    }
                }

                slotView.setOnClickListener {
                    selectedTrackLayer = layer
                    selectedLayer = layer // Link to position controls
                    layerAdapter.selectLayer(layer) // Highlight in layer list (Top Bar)
                    
                    selectedSlotIndex = i // Update index frame
                    updateLayerControlUI() // Update slider sesuai frame yang diklik
                    
                    // Pindah waktu preview ke frame ini
                    val sec = i.toFloat() / FPS
                    previewSurface.setManualTime(sec)
                    timelineScrubber.progress = i
                    
                    // Refresh agar highlight track berpindah ke layer/frame yang baru diklik
                    refreshTimelineUI()
                }

                if (selectedTrackLayer == layer && selectedSlotIndex == i) {
                    overlay.visibility = View.VISIBLE
                    overlay.setBackgroundColor(0x8800FF00.toInt()) // Lebih terang agar terlihat
                } else {
                    overlay.visibility = View.GONE
                }

                trackRow.addView(slotView)
            }
            timelineTracks.addView(trackRow)
        }
        // updatePreviewFromTimeline() // DIHAPUS agar tidak menimpa manualTime saat playback
    }

    private fun setupSourcePalette() {
        recyclerSource.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        refreshSourcePalette()
    }

    private fun refreshSourcePalette() {
        scannedFiles = AssetScanner.listImported(this)
        recyclerSource.adapter = SourceFileAdapter(scannedFiles) { fileName ->
            val layer = selectedTrackLayer ?: selectedLayer ?: return@SourceFileAdapter
            val stateName = spinnerStateName.selectedItem as String
            val state = layer.states[stateName] ?: return@SourceFileAdapter
            
            if (selectedSlotIndex >= 0) {
                // Set manual time to the selected slot to stop auto-play and show change
                val sec = selectedSlotIndex.toFloat() / FPS
                previewSurface.setManualTime(sec)
                timelineScrubber.progress = selectedSlotIndex

                // If slot already has this file, clear it. Else set it.
                if (state.frames[selectedSlotIndex] == fileName) {
                    state.frames[selectedSlotIndex] = ""
                } else {
                    state.frames[selectedSlotIndex] = fileName
                }
                refreshTimelineUI()
            }
        }
    }

    private fun updatePreviewFromTimeline() {
        previewSurface.updateConfig(config)
        // Ensure manual time is set so we aren't stuck on a blank frame or auto-playing
        val sec = timelineScrubber.progress.toFloat() / FPS
        previewSurface.setManualTime(sec)
        previewSurface.setState(spinnerStateName.selectedItem as String, forceReset = false)
    }

    private fun showAddLayerDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this).setTitle("New Layer").setView(input)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().ifBlank { "layer_${config.layers.size}" }
                // Gunakan zOrder yang lebih tinggi agar layer baru berada di depan
                val maxZ = config.layers.maxOfOrNull { it.zOrder } ?: -1
                val newLayer = LayerConfig(name = name, zOrder = maxZ + 1)
                
                // Inisialisasi states untuk layer baru agar tidak kosong (menghindari bug resolveFrame)
                val states = arrayOf("idle", "talk", "angry")
                states.forEach { stateName ->
                    val state = StateConfig(fps = FPS)
                    // Inisialisasi list dengan null/kosong sesuai totalSlots
                    repeat(totalSlots) {
                        state.frames.add("")
                        state.posX.add(null)
                        state.posY.add(null)
                        state.scale.add(null)
                        state.zOrder.add(null)
                    }
                    newLayer.states[stateName] = state
                }
                
                config.layers.add(newLayer)
                
                // Select the new layer
                selectedLayer = newLayer
                selectedTrackLayer = newLayer
                selectedSlotIndex = 0 // Mulai dari frame 0 untuk layer baru
                
                // Reset timeline ke awal (0s)
                timelineScrubber.progress = 0
                previewSurface.setManualTime(0f)
                
                // Force update all UI components
                layerAdapter.notifyDataSetChanged()
                layerAdapter.selectLayer(newLayer)
                updateLayerControlUI()
                refreshTimelineUI()
                updatePreviewFromTimeline()
            }.show()
    }

    override fun onResume() { super.onResume(); previewSurface.onResume() }
    override fun onPause() { super.onPause(); previewSurface.onPause() }
}
