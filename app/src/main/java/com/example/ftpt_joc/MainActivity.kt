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

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ftpt_joc.ui.theme.FtptjocTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FtptjocTheme {
                MainScreen()
            }
        }
    }
}

data class PhotoItem(
    val id: Long,
    val name: String,
    val date: String,
    val uri: Uri,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    
    var isCtrlActive by remember { mutableStateOf(false) }
    var isShiftActive by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Launcher pour la suppression (Permission système)
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onPhotosDeleted(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.loadPhotos(context)
        else Toast.makeText(context, "Permission refusée", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            if (viewModel.photos.isEmpty()) viewModel.loadPhotos(context)
        } else {
            permissionLauncher.launch(permission)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("FTP Transfer", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (viewModel.statusMessage.isNotEmpty()) {
                            Text(viewModel.statusMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadPhotos(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
                }
            )
        },
        bottomBar = {
            BottomControls(
                isCtrlActive = isCtrlActive,
                onCtrlToggle = { isCtrlActive = it; if (it) isShiftActive = false },
                isShiftActive = isShiftActive,
                onShiftToggle = { isShiftActive = it; if (it) isCtrlActive = false },
                isLastActionUpload = viewModel.lastActionWasUpload,
                isUploading = viewModel.isUploading,
                onGoClick = {
                    val settings = settingsManager.getSettings()
                    if (settings["host"].isNullOrBlank()) {
                        showSettings = true
                        return@BottomControls
                    }
                    viewModel.uploadPhotos(context, settings)
                },
                onSuppClick = {
                    val pendingIntent = viewModel.createDeleteRequest(context)
                    if (pendingIntent != null) {
                        val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        deleteLauncher.launch(request)
                    } else {
                        // Soit Android < 10, soit déjà supprimé, soit erreur
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            viewModel.deleteDirectly(context)
                        } else {
                            Toast.makeText(context, "Erreur suppression ou fin de liste", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                selectedCount = viewModel.selectedIndices.size
            )
        }
    ) { innerPadding ->
        if (showSettings) {
            SettingsDialog(
                settingsManager = settingsManager,
                onDismiss = { showSettings = false }
            )
        }

        Column(modifier = Modifier.padding(innerPadding)) {
            if (viewModel.photos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucune photo trouvée")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    itemsIndexed(viewModel.photos) { index, photo ->
                        PhotoTile(
                            photo = photo,
                            isSelected = viewModel.selectedIndices.contains(index),
                            onClick = {
                                viewModel.toggleSelection(index, isCtrlActive, isShiftActive)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(settingsManager: SettingsManager, onDismiss: () -> Unit) {
    val currentSettings = remember { settingsManager.getSettings() }
    var host by remember { mutableStateOf(currentSettings["host"] ?: "") }
    var user by remember { mutableStateOf(currentSettings["user"] ?: "") }
    var pass by remember { mutableStateOf(currentSettings["pass"] ?: "") }
    var dir by remember { mutableStateOf(currentSettings["dir"] ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paramètres FTP") },
        text = {
            Column {
                TextField(value = host, onValueChange = { host = it }, label = { Text("Hôte (ex: perso-ftp.free.fr)") })
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = user, onValueChange = { user = it }, label = { Text("Identifiant") })
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = pass, 
                    onValueChange = { pass = it }, 
                    label = { Text("Mot de passe") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = dir, onValueChange = { dir = it }, label = { Text("Répertoire distant") })
            }
        },
        confirmButton = {
            Button(onClick = {
                settingsManager.saveSettings(host, user, pass, dir)
                onDismiss()
            }) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun PhotoTile(photo: PhotoItem, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clickable { onClick() }
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = photo.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(4.dp)
            ) {
                Text(text = photo.name, fontSize = 10.sp, maxLines = 1, color = Color.White, fontWeight = FontWeight.Bold)
                Text(text = photo.date, fontSize = 8.sp, maxLines = 1, color = Color.LightGray)
            }

            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)))
            }
        }
    }
}

@Composable
fun BottomControls(
    isCtrlActive: Boolean, onCtrlToggle: (Boolean) -> Unit,
    isShiftActive: Boolean, onShiftToggle: (Boolean) -> Unit,
    isLastActionUpload: Boolean, isUploading: Boolean,
    onGoClick: () -> Unit,
    onSuppClick: () -> Unit,
    selectedCount: Int
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onShiftToggle(!isShiftActive) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isShiftActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isShiftActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) {
                Text("SHIFT", fontSize = 10.sp)
            }

            Button(
                onClick = { onCtrlToggle(!isCtrlActive) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCtrlActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isCtrlActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) {
                Text("CTRL", fontSize = 10.sp)
            }

            // Bouton SUPP (toujours là, mais rouge pétant si on vient de transférer)
            Button(
                onClick = onSuppClick,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLastActionUpload) Color(0xFFD32F2F) else Color(0xFFF44336).copy(alpha = 0.7f),
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1.2f).padding(horizontal = 2.dp)
            ) {
                Text("SUPP ($selectedCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            // Bouton GO
            Button(
                onClick = onGoClick,
                enabled = !isUploading && selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1.2f).padding(horizontal = 2.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("GO", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun loadPhotos(context: android.content.Context): List<PhotoItem> {
    val photoList = mutableListOf<PhotoItem>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED
    )
    
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol)
            var timestamp = cursor.getLong(dateCol)
            if (timestamp == 0L) timestamp = cursor.getLong(addedCol) * 1000

            val date = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(timestamp))
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            
            photoList.add(PhotoItem(id, name, date, uri, timestamp))
        }
    }
    return photoList
}
