package ame.project.n2live.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ame.project.n2live.R
import ame.project.n2live.data.ConfigManager
import java.io.File

class TimelineAdapter(
    private val frames: MutableList<String>,
    private val onFrameRemoved: (Int) -> Unit
) : RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgTimelinePreview)
        val index: TextView = view.findViewById(R.id.tvTimelineIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timeline_frame, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fileName = frames[position]
        holder.index.text = (position + 1).toString()
        
        val spriteFile = File(ConfigManager.spriteDir(holder.itemView.context), fileName)
        if (spriteFile.exists()) {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = BitmapFactory.decodeFile(spriteFile.absolutePath, options)
            holder.img.setImageBitmap(bitmap)
        }
        
        holder.itemView.setOnClickListener {
            onFrameRemoved(holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = frames.size
}
