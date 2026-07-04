package ame.project.n2live.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ame.project.n2live.R
import ame.project.n2live.data.LayerConfig

class LayerAdapter(
    private val layers: MutableList<LayerConfig>,
    private val onLayerSelected: (LayerConfig) -> Unit,
    private val onDelete: (LayerConfig) -> Unit
) : RecyclerView.Adapter<LayerAdapter.ViewHolder>() {

    private var selectedIndex = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvLayerName)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteLayer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_layer_tab, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val layer = layers[position]
        holder.name.text = layer.name
        
        val isSelected = position == selectedIndex
        holder.itemView.setBackgroundResource(
            if (isSelected) R.drawable.bg_frame_item_selected else R.drawable.bg_frame_item
        )

        holder.itemView.setOnClickListener {
            selectedIndex = holder.adapterPosition
            onLayerSelected(layer)
            notifyDataSetChanged()
        }

        holder.btnDelete.setOnClickListener { onDelete(layer) }
    }

    override fun getItemCount(): Int = layers.size
    
    fun getSelectedLayer(): LayerConfig? = layers.getOrNull(selectedIndex)

    fun selectLayer(layer: LayerConfig) {
        val index = layers.indexOf(layer)
        if (index != -1 && index != selectedIndex) {
            selectedIndex = index
            notifyDataSetChanged()
        }
    }
}
