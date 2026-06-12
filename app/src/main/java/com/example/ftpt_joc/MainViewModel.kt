/*
 * FTP Transfer - Une application pour transférer des photos vers un serveur FTP.
 * Copyright (C) 2025  JOC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.ftpt_joc

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient

class MainViewModel : ViewModel() {
    var photos by mutableStateOf<List<PhotoItem>>(emptyList())
    var selectedIndices by mutableStateOf(setOf<Int>())
    var isUploading by mutableStateOf(false)
    var lastSelectedIndex by mutableStateOf<Int?>(null)
    var lastActionWasUpload by mutableStateOf(false)
    var statusMessage by mutableStateOf("")

    fun loadPhotos(context: Context) {
        viewModelScope.launch {
            photos = withContext(Dispatchers.IO) {
                com.example.ftpt_joc.loadPhotos(context)
            }
        }
    }

    fun toggleSelection(index: Int, isCtrl: Boolean, isShift: Boolean) {
        val newSelection = selectedIndices.toMutableSet()
        when {
            isCtrl -> {
                if (newSelection.contains(index)) newSelection.remove(index)
                else newSelection.add(index)
                lastSelectedIndex = index
            }
            isShift && lastSelectedIndex != null -> {
                val start = minOf(lastSelectedIndex!!, index)
                val end = maxOf(lastSelectedIndex!!, index)
                for (i in start..end) newSelection.add(i)
            }
            else -> {
                newSelection.clear()
                newSelection.add(index)
                lastSelectedIndex = index
            }
        }
        selectedIndices = newSelection
        lastActionWasUpload = false
    }

    fun uploadPhotos(context: Context, settings: Map<String, String>) {
        val selectedPhotos = selectedIndices.mapNotNull { photos.getOrNull(it) }
        if (selectedPhotos.isEmpty()) return

        isUploading = true
        statusMessage = "Connexion à ${settings["host"]}..."
        
        viewModelScope.launch(Dispatchers.IO) {
            val client = FTPClient()
            try {
                client.connect(settings["host"], 21)
                client.login(settings["user"], settings["pass"])
                client.enterLocalPassiveMode()
                client.setFileType(FTP.BINARY_FILE_TYPE)
                
                val remoteDir = settings["dir"] ?: "/"
                if (remoteDir.isNotEmpty() && remoteDir != "/") {
                    if (!client.changeWorkingDirectory(remoteDir)) {
                        client.makeDirectory(remoteDir)
                        client.changeWorkingDirectory(remoteDir)
                    }
                }

                selectedPhotos.forEachIndexed { i, photo ->
                    withContext(Dispatchers.Main) {
                        statusMessage = "Envoi ${i + 1}/${selectedPhotos.size}: ${photo.name}"
                    }
                    context.contentResolver.openInputStream(photo.uri)?.use { inputStream ->
                        val success = client.storeFile(photo.name, inputStream)
                        if (!success) throw Exception("Échec de l'envoi de ${photo.name}")
                    }
                }

                withContext(Dispatchers.Main) {
                    statusMessage = "Transfert réussi !"
                    isUploading = false
                    lastActionWasUpload = true
                }
            } catch (e: Exception) {
                Log.e("FTP", "Erreur", e)
                withContext(Dispatchers.Main) {
                    statusMessage = "Erreur: ${e.message}"
                    isUploading = false
                }
            } finally {
                if (client.isConnected) {
                    try { client.logout() } catch (e: Exception) {}
                    try { client.disconnect() } catch (e: Exception) {}
                }
            }
        }
    }

    fun createDeleteRequest(context: Context): PendingIntent? {
        val uris = selectedIndices.mapNotNull { photos.getOrNull(it)?.uri }
        if (uris.isEmpty()) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createDeleteRequest(context.contentResolver, uris)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 : On tente de supprimer la première pour obtenir l'autorisation
            try {
                context.contentResolver.delete(uris[0], null, null)
            } catch (e: SecurityException) {
                if (e is RecoverableSecurityException) {
                    return e.userAction.actionIntent
                }
            }
        }
        return null
    }

    fun deleteDirectly(context: Context) {
        val uris = selectedIndices.mapNotNull { photos.getOrNull(it)?.uri }
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    Log.e("Delete", "Erreur sur $uri", e)
                }
            }
            withContext(Dispatchers.Main) {
                onPhotosDeleted(context)
            }
        }
    }

    fun onPhotosDeleted(context: Context) {
        selectedIndices = emptySet()
        lastActionWasUpload = false
        statusMessage = "Photos supprimées"
        loadPhotos(context)
    }
}
