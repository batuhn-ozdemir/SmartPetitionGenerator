package com.gpproject.smartpetitiongenerator.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gpproject.smartpetitiongenerator.ui.viewmodel.GeminiOcrPreviewResult
import com.gpproject.smartpetitiongenerator.ui.viewmodel.PetitionViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

@Composable
fun ScanToPreviewScreen(
    navController: NavController,
    viewModel: PetitionViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Fotoğraf çekin veya galeriden seçip OCR başlatın.") }
    var warningText by remember { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.clearCurrentPreview()
        selectedImageUri = uri
        selectedImageBytes = null
        statusText = if (uri != null) {
            "Görsel seçildi. Gemini ile dilekçe tespiti ve konumsal OCR için hazır."
        } else {
            "Görsel seçimi iptal edildi."
        }
        warningText = null
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { isSuccess ->
        viewModel.clearCurrentPreview()
        val capturedUri = pendingCameraUri
        selectedImageBytes = null
        selectedImageUri = if (isSuccess) capturedUri else null
        statusText = if (isSuccess && capturedUri != null) {
            "Fotoğraf çekildi (yüksek çözünürlük). Gemini ile dilekçe tespiti ve konumsal OCR için hazır."
        } else {
            "Fotoğraf çekimi iptal edildi."
        }
        pendingCameraUri = null
        warningText = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Fotoğraftan Birebir Önizleme",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Bu akışta AI size elinizde bulunan dilekçe için hızlı bir yazdırma yapmaya çalışır. Hata yapabilir, beğenmediğiniz yerleştirmeleri elle düzenlemeniz gerekir. " +
                    "Uzun dilekçelerde hata gösterilir. Bu işlem biraz uzun sürebilir.",
            style = MaterialTheme.typography.bodyLarge
        )

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(
                onClick = {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Text("Fotoğraf Seç", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = {
                val uri = createTempImageUri(context)
                if (uri == null) {
                    warningText = "Kamera için geçici dosya hazırlanamadı. Lütfen tekrar deneyin."
                    statusText = "Fotoğraf çekimi başlatılamadı."
                    return@OutlinedButton
                }
                pendingCameraUri = uri
                takePhotoLauncher.launch(uri)
            }) {
                Text("Fotoğraf Çek", style = MaterialTheme.typography.titleMedium)
            }
        }

        SelectedImagePreview(
            selectedImageUri = selectedImageUri,
            selectedImageBytes = selectedImageBytes
        )

        Button(
            enabled = !isProcessing && (selectedImageUri != null || selectedImageBytes != null),
            onClick = {
                val imageUri = selectedImageUri
                val inMemoryBytes = selectedImageBytes

                scope.launch {
                    isProcessing = true
                    warningText = null
                    statusText = "Okuma yapılıyor..."


                    val result = runCatching {
                        val bytes = when {
                            inMemoryBytes != null -> inMemoryBytes
                            imageUri != null -> context.contentResolver.openInputStream(imageUri).use { input ->
                                input?.readBytes()
                            } ?: error("Görsel açılamadı")
                            else -> error("Görsel bulunamadı")
                        }

                        val preparedImage = prepareImageForOcr(bytes)

                        var latestResult: GeminiOcrPreviewResult = GeminiOcrPreviewResult.Error("Bilinmeyen hata")
                        for (attempt in 1..3) {
                            latestResult = viewModel.createPreviewFromGeminiDocumentLayout(
                                imageBytes = preparedImage.bytes,
                                mimeType = preparedImage.mimeType
                            )
                            if (latestResult is GeminiOcrPreviewResult.Success) break
                            if (attempt < 3) {
                                //statusText = "${attempt}. okuma yapıldı, başarısız. \n${attempt + 1}. okuma yapılıyor..."
                                statusText = "Bu işlem biraz uzun sürebilir."
                            }
                        }
                        latestResult
                    }.getOrElse { error ->
                        GeminiOcrPreviewResult.Error(error.localizedMessage ?: "Bilinmeyen hata")
                    }

                    when (result) {
                        is GeminiOcrPreviewResult.Success -> {
                            statusText = "Tamamlandı: Dilekçe düzeni Gemini ile çözümlendi (güven: ${"%.2f".format(Locale.US, result.confidence)})."
                            navController.navigate("preview_screen/new")
                        }
                        is GeminiOcrPreviewResult.Error -> {
                            statusText = "Okuma başarısız oldu."
                            warningText = result.message
                        }

                    }
                    isProcessing = false
                }
            }
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Oku ve Önizle", style = MaterialTheme.typography.titleMedium)
            }
        }

        Text(statusText, style = MaterialTheme.typography.bodyLarge)

        warningText?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SelectedImagePreview(
    selectedImageUri: Uri?,
    selectedImageBytes: ByteArray?
) {
    val context = LocalContext.current
    var previewBitmap by remember(selectedImageUri, selectedImageBytes) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(selectedImageUri, selectedImageBytes) {
        previewBitmap = when {
            selectedImageBytes != null -> BitmapFactory.decodeByteArray(selectedImageBytes, 0, selectedImageBytes.size)
            selectedImageUri != null -> context.contentResolver.openInputStream(selectedImageUri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
            else -> null
        }
    }

    if (previewBitmap != null) {
        Image(
            bitmap = previewBitmap!!.asImageBitmap(),
            contentDescription = "Seçilen fotoğraf önizleme",
            modifier = Modifier
                .size(170.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(4.dp)
        )
    } else {
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private data class PreparedImage(
    val bytes: ByteArray,
    val mimeType: String
)

private fun prepareImageForOcr(
    originalBytes: ByteArray,
    maxDimensionPx: Int = 2048,
    maxPayloadBytes: Int = 2_400_000
): PreparedImage {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, bounds)

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return PreparedImage(originalBytes, "image/jpeg")
    }

    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    val sampleSize = if (largest <= maxDimensionPx) 1 else {
        var sample = 1
        while (largest / sample > maxDimensionPx) sample *= 2
        sample
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)
        ?: return PreparedImage(originalBytes, "image/jpeg")

    val output = ByteArrayOutputStream()
    var quality = 88
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)

    while (output.size() > maxPayloadBytes && quality > 55) {
        output.reset()
        quality -= 8
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
    }

    bitmap.recycle()
    val compressed = output.toByteArray()
    output.close()

    if (compressed.size > maxPayloadBytes) {
        Log.w("ScanToPreviewScreen", "OCR payload still large after compression: ${compressed.size} bytes")
    }

    return PreparedImage(
        bytes = compressed,
        mimeType = "image/jpeg"
    )
}

private fun Bitmap.toJpegBytes(
    quality: Int = 92
): ByteArray {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, output)
    return output.toByteArray().also {
        output.close()
    }
}

private fun createTempImageUri(context: android.content.Context): Uri? {
    return runCatching {
        val tempFile = File.createTempFile(
            "ocr_capture_",
            ".jpg",
            context.cacheDir
        )
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }.getOrNull()
}