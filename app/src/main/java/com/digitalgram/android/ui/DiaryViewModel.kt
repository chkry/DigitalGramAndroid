package com.digitalgram.android.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalgram.android.data.DiaryDao
import com.digitalgram.android.data.DiaryEntry
import kotlinx.coroutines.launch

class DiaryViewModel(private val diaryDao: DiaryDao) : ViewModel() {
    
    val allEntries: LiveData<List<DiaryEntry>> = diaryDao.getAllEntries()
    
    fun insert(entry: DiaryEntry) = viewModelScope.launch {
        diaryDao.insert(entry)
    }
    
    fun update(entry: DiaryEntry) = viewModelScope.launch {
        diaryDao.update(entry.copy(updatedAt = System.currentTimeMillis()))
    }
    
    fun delete(entry: DiaryEntry) = viewModelScope.launch {
        diaryDao.delete(entry)
    }
    
    fun deleteById(id: Long) = viewModelScope.launch {
        diaryDao.deleteById(id)
    }
    
    suspend fun getEntryById(id: Long): DiaryEntry? {
        return diaryDao.getEntryById(id)
    }
}
