package com.digitalgram.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.digitalgram.android.data.DiaryDao

class DiaryViewModelFactory(private val diaryDao: DiaryDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiaryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DiaryViewModel(diaryDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
