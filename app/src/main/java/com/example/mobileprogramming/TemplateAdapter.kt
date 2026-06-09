package com.example.mobileprogramming

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mobileprogramming.databinding.ItemTemplateBinding

data class TemplateItem(
    val id: Int,
    val drawableResId: Int,
    val name: String
)

class TemplateAdapter(
    private val items: List<TemplateItem>,
    private val onItemClick: (TemplateItem) -> Unit
) : RecyclerView.Adapter<TemplateAdapter.ViewHolder>() {

    private var selectedPosition = 0

    inner class ViewHolder(private val binding: ItemTemplateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateItem, position: Int) {
            binding.ivTemplateThumbnail.setImageResource(item.drawableResId)
            
            // Highlight selected template
            if (position == selectedPosition) {
                binding.cardTemplate.strokeWidth = 6 // pixels
                binding.cardTemplate.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#4F46E5")))
            } else {
                binding.cardTemplate.strokeWidth = 0
            }

            binding.root.setOnClickListener {
                val previousSelected = selectedPosition
                selectedPosition = bindingAdapterPosition
                notifyItemChanged(previousSelected)
                notifyItemChanged(selectedPosition)
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTemplateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size
}
