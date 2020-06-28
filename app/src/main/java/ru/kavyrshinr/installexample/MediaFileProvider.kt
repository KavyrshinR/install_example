package ru.kavyrshinr.installexample

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class MediaFileProvider : FileProvider() {
    companion object {
        const val AUTHORITIES = "ru.kavyrshinr.installexample.fileprovider"

        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            return FileProvider.getUriForFile(context, authority, file)
        }
    }
}