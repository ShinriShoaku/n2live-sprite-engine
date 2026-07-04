package ame.project.n2live.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ame.project.n2live.R
import ame.project.n2live.data.ConfigManager
import java.io.File

/**
 * List checkbox nama-nama PNG hasil scan folder, dipakai di Animation Builder
 * untuk memilih file mana saja yang masuk ke sebuah state (mis. frame "talk").
 * Urutan centang menentukan urutan frame animasi.
 */
class FrameCheckAdapter(
    private val allFiles: List<String>,
    private val selectedOrdered: MutableList<String>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<FrameCheckAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val imgPreview: ImageView = view.findViewById(R.id.imgFramePreview)
        val checkBox: CheckBox = view.findViewById(R.id.checkboxFrame)
        val order: TextView = view.findViewById(R.id.tvFrameOrder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_frame_checkbox, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fileName = allFiles[position]
        holder.checkBox.text = fileName
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selectedOrdered.contains(fileName)
        updateOrderLabel(holder, fileName)

        // Load preview image
        val spriteFile = File(ConfigManager.spriteDir(holder.itemView.context), fileName)
        if (spriteFile.exists()) {
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bitmap = BitmapFactory.decodeFile(spriteFile.absolutePath, options)
            holder.imgPreview.setImageBitmap(bitmap)
        } else {
            holder.imgPreview.setImageBitmap(null)
        }

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!selectedOrdered.contains(fileName)) selectedOrdered.add(fileName)
            } else {
                selectedOrdered.remove(fileName)
            }
            onSelectionChanged()
            notifyDataSetChanged()
        }
    }

    private fun updateOrderLabel(holder: ViewHolder, fileName: String) {
        val idx = selectedOrdered.indexOf(fileName)
        if (idx >= 0) {
            holder.order.text = "${idx + 1}"
            holder.order.visibility = android.view.View.VISIBLE
        } else {
            holder.order.visibility = android.view.View.GONE
        }
    }

    override fun getItemCount(): Int = allFiles.size
}
