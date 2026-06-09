package com.example.mobileprogramming

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mobileprogramming.databinding.ItemRecentCertificateBinding

class RecentAdapter(
    private val items: List<StorageItem>,
    private val onItemClick: (StorageItem) -> Unit
) : RecyclerView.Adapter<RecentAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemRecentCertificateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StorageItem) {
            binding.ivRecentThumbnail.load(item.uri) {
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
        val binding = ItemRecentCertificateBinding.inflate(
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
