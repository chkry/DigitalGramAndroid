package com.digitalgram.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.digitalgram.android.data.DiaryEntry
import com.digitalgram.android.databinding.ItemDiaryEntryBinding
import java.text.SimpleDateFormat
import java.util.*

class DiaryAdapter(
    private val onEntryClick: (DiaryEntry) -> Unit
) : ListAdapter<DiaryEntry, DiaryAdapter.DiaryViewHolder>(DiaryEntryDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaryViewHolder {
        val binding = ItemDiaryEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DiaryViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: DiaryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class DiaryViewHolder(
        private val binding: ItemDiaryEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEntryClick(getItem(position))
                }
            }
        }
        
        fun bind(entry: DiaryEntry) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = entry.date
            }
            
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val dateFormat = SimpleDateFormat("d", Locale.getDefault())
            
            binding.dayName.text = dayFormat.format(calendar.time).uppercase()
            binding.dayNumber.text = dayFormat.format(calendar.time)
            binding.entryContent.text = entry.content
        }
    }
    
    private class DiaryEntryDiffCallback : DiffUtil.ItemCallback<DiaryEntry>() {
        override fun areItemsTheSame(oldItem: DiaryEntry, newItem: DiaryEntry): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: DiaryEntry, newItem: DiaryEntry): Boolean {
            return oldItem == newItem
        }
    }
}
