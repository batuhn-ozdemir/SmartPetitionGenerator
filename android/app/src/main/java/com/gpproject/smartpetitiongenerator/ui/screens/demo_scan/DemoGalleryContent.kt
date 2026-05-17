package com.gpproject.smartpetitiongenerator.ui.screens.demo_scan

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DemoGalleryContent(
    demoImages: List<File>,
    onAddImage: () -> Unit,
    onImageSelected: (File) -> Unit,
    onDeleteImage: (File) -> Unit
) {
    var imageToDelete by remember {
        mutableStateOf<File?>(null)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Button(
            onClick = onAddImage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Demo Galeriye Fotoğraf Ekle")
        }

        if (demoImages.isEmpty()) {
            Text("Henüz demo fotoğraf eklenmedi.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = demoImages,
                    key = { file -> file.absolutePath }
                ) { file ->

                    val bitmap = remember(file.absolutePath, file.lastModified()) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }

                    if (bitmap != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .combinedClickable(
                                    onClick = {
                                        onImageSelected(file)
                                    },
                                    onLongClick = {
                                        imageToDelete = file
                                    }
                                )
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Demo fotoğraf",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    if (imageToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                imageToDelete = null
            },
            title = {
                Text("Demo fotoğraf silinsin mi?")
            },
            text = {
                Text("Bu işlem sadece uygulama içindeki demo galeriden kaldırır. Telefon galerindeki asıl fotoğraf silinmez.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        imageToDelete?.let {
                            onDeleteImage(it)
                        }
                        imageToDelete = null
                    }
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        imageToDelete = null
                    }
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }
}