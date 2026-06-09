package com.example.mobileprogramming

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mobileprogramming.databinding.ItemStorageBinding

data class StorageItem(
    val uri: Uri,
    val name: String
)

class StorageAdapter(
    private val items: List<StorageItem>,
    private val onItemClick: (StorageItem) -> Unit
) : RecyclerView.Adapter<StorageAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemStorageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StorageItem) {
            // Load local certificate thumbnail image using Coil
            binding.ivStorageThumbnail.load(item.uri) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStorageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
