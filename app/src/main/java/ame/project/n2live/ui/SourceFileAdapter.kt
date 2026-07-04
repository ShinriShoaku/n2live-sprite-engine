package ame.project.n2live.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import ame.project.n2live.R
import ame.project.n2live.data.ConfigManager
import java.io.File

class SourceFileAdapter(
    private val files: List<String>,
    private val onFileClicked: (String) -> Unit
) : RecyclerView.Adapter<SourceFileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgFramePreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_frame_checkbox, parent, false)
        // Hide checkbox for source picker
        view.findViewById<View>(R.id.checkboxFrame).visibility = View.GONE
        view.findViewById<View>(R.id.tvFrameOrder).visibility = View.GONE
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fileName = files[position]
        val spriteFile = File(ConfigManager.spriteDir(holder.itemView.context), fileName)
        if (spriteFile.exists()) {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = BitmapFactory.decodeFile(spriteFile.absolutePath, options)
            holder.img.setImageBitmap(bitmap)
        }
        holder.itemView.setOnClickListener { onFileClicked(fileName) }
    }

    override fun getItemCount(): Int = files.size
}
