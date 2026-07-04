package ame.project.n2live

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ame.project.n2live.data.ConfigManager
import ame.project.n2live.gl.SpriteGLSurfaceView

class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: SpriteGLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        glSurfaceView = findViewById(R.id.glSurfaceLive)
        val config = ConfigManager.load(this)
        glSurfaceView.init(config, fps = 60) // Live Mode = 60 FPS

        findViewById<android.widget.Button>(R.id.btnIdle).setOnClickListener {
            glSurfaceView.setState("idle")
        }
        findViewById<android.widget.Button>(R.id.btnTalk).setOnClickListener {
            glSurfaceView.setState("talk")
        }
        findViewById<android.widget.Button>(R.id.btnAngry).setOnClickListener {
            glSurfaceView.setState("angry")
        }
        findViewById<android.widget.Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // === Contoh integrasi State Listener dari AI ===
        // Di implementasi nyata, panggilan ini datang dari hasil stream AI,
        // misalnya saat mendeteksi audio/text -> "mulut terbuka":
        //   glSurfaceView.setState("talk")
        // dan saat AI mendeteksi ekspresi marah:
        //   glSurfaceView.setState("angry")
        glSurfaceView.spriteRenderer.listener = object : ame.project.n2live.gl.SpriteRenderer.StateListener {
            override fun onStateFinished(stateName: String) {
                // State non-loop (mis. "angry") sudah selesai -> balik ke idle
                runOnUiThread { glSurfaceView.setState("idle") }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload config kalau baru diedit dari SettingsActivity
        if (::glSurfaceView.isInitialized) {
            glSurfaceView.updateConfig(ConfigManager.load(this))
        }
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }
}
