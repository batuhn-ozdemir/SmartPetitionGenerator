package com.gpproject.smartpetitiongenerator.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.gpproject.smartpetitiongenerator.ui.viewmodel.GeminiOcrPreviewResult
import com.gpproject.smartpetitiongenerator.ui.viewmodel.PetitionViewModel
import com.gpproject.smartpetitiongenerator.ui.screens.demo_scan.DemoImageStore
import com.gpproject.smartpetitiongenerator.ui.screens.demo_scan.DemoGalleryContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

// Android and CameraX imports are used for image picking, camera capture,
// bitmap processing, EXIF orientation handling, and CameraX preview.

private enum class ScanStep {
    START,  // Initial screen where the user selects or captures an image.
    CAMERA, // Camera preview screen.
    CROP,    // Corner selection and perspective correction screen.
    DEMO_GALLERY  // Just for demo presentation
}

private enum class CornerDragTarget {
    NONE,         // No draggable target selected.
    TOP_LEFT,     // Top-left corner handle.
    TOP_RIGHT,    // Top-right corner handle.
    BOTTOM_RIGHT, // Bottom-right corner handle.
    BOTTOM_LEFT,  // Bottom-left corner handle.
    MOVE          // Whole selected quadrilateral is moved.
}

// Holds normalized document corner coordinates.
// Each Offset uses values between 0f and 1f relative to the image size.
private data class DocumentCorners(
    val topLeft: Offset,
    val topRight: Offset,
    val bottomRight: Offset,
    val bottomLeft: Offset
)

@Composable
fun ScanToPreviewScreen(
    navController: NavController,
    viewModel: PetitionViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Current scan flow step.
    var step by remember { mutableStateOf(ScanStep.START) }

    // Original selected or captured image.
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Current document corner selection.
    var corners by remember { mutableStateOf(defaultDocumentCorners()) }

    // Processing and status UI states.
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember {
        mutableStateOf("Fotoğraf çekin veya galeriden seçin. OCR'a gitmeden önce 4 köşe seçilecek.")
    }
    var warningText by remember { mutableStateOf<String?>(null) }

    var demoImages by remember {
        mutableStateOf(DemoImageStore.getDemoImages(context))
    }

    // Opens Android photo picker and receives the selected image URI.
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.clearCurrentPreview()
        warningText = null

        if (uri == null) {
            statusText = "Görsel seçimi iptal edildi."
            return@rememberLauncherForActivityResult
        }

        // Decode the selected image and respect EXIF orientation.
        val bitmap = decodeBitmapFullRespectingOrientation(
            context = context,
            imageUri = uri
        )

        if (bitmap == null) {
            statusText = "Görsel açılamadı."
            warningText = "Seçilen fotoğraf okunamadı. Lütfen farklı bir görsel deneyin."
            return@rememberLauncherForActivityResult
        }

        sourceBitmap = bitmap
        corners = defaultDocumentCorners()
        step = ScanStep.CROP
        statusText = "Görsel seçildi. Belgenin 4 köşesini sürükleyerek seçin."
    }

    val addDemoImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            statusText = "Demo fotoğraf ekleme iptal edildi."
            return@rememberLauncherForActivityResult
        }

        val savedFile = DemoImageStore.saveImageFromUri(context, uri)

        if (savedFile == null) {
            warningText = "Fotoğraf demo galeriye eklenemedi."
        } else {
            demoImages = DemoImageStore.getDemoImages(context)
            statusText = "Fotoğraf demo galeriye eklendi."
            warningText = null
        }
    }

    // Requests camera permission before opening the camera screen.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            warningText = null
            step = ScanStep.CAMERA
        } else {
            warningText = "Kamera izni verilmedi."
            statusText = "Fotoğraf çekimi için kamera izni gerekiyor."
        }
    }

    // Keeps the screen awake while OCR/image processing is running.
    KeepScreenAwake(isProcessing)

    when (step) {
        ScanStep.START -> {
            StartStepContent(
                statusText = statusText,
                warningText = warningText,
                onPickImage = {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpenCamera = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        warningText = null
                        step = ScanStep.CAMERA
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onOpenDemoGallery = {
                    demoImages = DemoImageStore.getDemoImages(context)
                    step = ScanStep.DEMO_GALLERY
                }
            )
        }

        ScanStep.CAMERA -> {
            CameraCaptureStep(
                onCaptured = { file ->
                    viewModel.clearCurrentPreview()
                    warningText = null

                    // Decode captured camera image and apply EXIF rotation.
                    val bitmap = decodeBitmapFullRespectingOrientation(file)

                    if (bitmap == null) {
                        statusText = "Çekilen fotoğraf okunamadı."
                        warningText = "Fotoğraf dosyası çözümlenemedi. Lütfen tekrar deneyin."
                        step = ScanStep.START
                        return@CameraCaptureStep
                    }

                    sourceBitmap = bitmap
                    corners = defaultDocumentCorners()
                    step = ScanStep.CROP
                    statusText = "Fotoğraf çekildi. Belgenin 4 köşesini sürükleyerek seçin."
                },
                onCancel = {
                    step = ScanStep.START
                    statusText = "Fotoğraf çekimi iptal edildi."
                },
                onError = { message ->
                    warningText = message
                    statusText = "Fotoğraf çekimi başarısız oldu."
                    step = ScanStep.START
                }
            )
        }

        ScanStep.CROP -> {
            val bitmap = sourceBitmap

            if (bitmap == null) {
                step = ScanStep.START
                statusText = "Görsel bulunamadı. Lütfen tekrar seçin."
                return
            }

            CropStepContent(
                bitmap = bitmap,
                corners = corners,
                onCornersChange = { corners = it },
                isProcessing = isProcessing,
                statusText = statusText,
                warningText = warningText,
                onBack = {
                    sourceBitmap = null
                    corners = defaultDocumentCorners()
                    step = ScanStep.START
                    statusText = "Fotoğraf çekin veya galeriden seçin. OCR'a gitmeden önce 4 köşe seçilecek."
                    warningText = null
                },
                onResetCorners = {
                    corners = defaultDocumentCorners()
                },
                onStartOcr = {
                    scope.launch {
                        isProcessing = true
                        warningText = null
                        statusText = "Seçilen dörtgen düzeltilip PNG olarak AI'ye gönderiliyor..."

                        // Perspective correction and PNG conversion are CPU-heavy,
                        // so they run on Dispatchers.Default.
                        val result = runCatching {
                            withContext(Dispatchers.Default) {
                                val correctedBitmap = perspectiveCorrectBitmap(
                                    source = bitmap,
                                    corners = corners
                                )

                                val pngBytes = bitmapToLosslessPngBytes(correctedBitmap)

                                if (correctedBitmap != bitmap && !correctedBitmap.isRecycled) {
                                    correctedBitmap.recycle()
                                }

                                viewModel.createPreviewFromGeminiDocumentLayout(
                                    imageBytes = pngBytes,
                                    mimeType = "image/png"
                                )
                            }
                        }.getOrElse { error ->
                            GeminiOcrPreviewResult.Error(
                                error.localizedMessage ?: "Bilinmeyen hata"
                            )
                        }

                        when (result) {
                            is GeminiOcrPreviewResult.Success -> {
                                statusText = "Tamamlandı: Belge alanı çözümlendi (güven: ${
                                    "%.2f".format(Locale.US, result.confidence)
                                })."
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
            )
        }

        ScanStep.DEMO_GALLERY -> {
            DemoGalleryContent(
                demoImages = demoImages,
                onAddImage = {
                    addDemoImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onImageSelected = { file ->
                    val bitmap = DemoImageStore.decodeFileToBitmap(file)

                    if (bitmap == null) {
                        warningText = "Demo fotoğraf okunamadı."
                        return@DemoGalleryContent
                    }

                    sourceBitmap = bitmap
                    corners = defaultDocumentCorners()
                    step = ScanStep.CROP
                    statusText = "Demo fotoğraf seçildi. Belgenin 4 köşesini ayarlayın."
                    warningText = null
                },
                onDeleteImage = { file ->
                    val deleted = DemoImageStore.deleteDemoImage(file)

                    demoImages = DemoImageStore.getDemoImages(context)

                    statusText = if (deleted) {
                        "Demo fotoğraf listeden kaldırıldı."
                    } else {
                        "Demo fotoğraf silinemedi."
                    }
                }
            )
        }
    }
}

// Initial screen that lets the user either pick an image or open the camera.
@Composable
private fun StartStepContent(
    statusText: String,
    warningText: String?,
    onPickImage: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenDemoGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Fotoğraftan Birebir Önizleme",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Fotoğraf seçildikten veya çekildikten sonra belgenin 4 köşesini seçeceksiniz. " +
                    "Yamuk çekilmiş belge perspective transform ile düzleştirilir ve AI'ye sadece seçilen alan gönderilir.",
            style = MaterialTheme.typography.bodyLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(onClick = onPickImage) {
                Text("Fotoğraf Seç", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(onClick = onOpenCamera) {
                Text("Fotoğraf Çek", style = MaterialTheme.typography.titleMedium)
            }
        }

        Text(statusText, style = MaterialTheme.typography.bodyLarge)

        Button(
            onClick = onOpenDemoGallery,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Demo Galeri")
        }


        warningText?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

// Screen where the user adjusts the four document corners before OCR.
@Composable
private fun CropStepContent(
    bitmap: Bitmap,
    corners: DocumentCorners,
    onCornersChange: (DocumentCorners) -> Unit,
    isProcessing: Boolean,
    statusText: String,
    warningText: String?,
    onBack: () -> Unit,
    onResetCorners: () -> Unit,
    onStartOcr: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Belgenin 4 Köşesini Seç",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Her beyaz noktayı belgenin gerçek köşesine sürükle. Köşeyi yakalayınca parmağını kaldırana kadar bırakmaz.",
            style = MaterialTheme.typography.bodyMedium
        )

        DocumentCornerPicker(
            bitmap = bitmap,
            corners = corners,
            onCornersChange = onCornersChange,
            interactionEnabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                enabled = !isProcessing
            ) {
                Text("Geri")
            }

            OutlinedButton(
                onClick = onResetCorners,
                modifier = Modifier.weight(1f),
                enabled = !isProcessing
            ) {
                Text("Sıfırla")
            }
        }

        Button(
            onClick = onStartOcr,
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Düzelt ve OCR Başlat")
            }
        }

        Text(statusText, style = MaterialTheme.typography.bodyMedium)

        warningText?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// Displays the selected image and lets the user drag document corner handles.
@Composable
private fun DocumentCornerPicker(
    bitmap: Bitmap,
    corners: DocumentCorners,
    onCornersChange: (DocumentCorners) -> Unit,
    interactionEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var containerWidth by remember { mutableIntStateOf(0) }
    var containerHeight by remember { mutableIntStateOf(0) }

    val imageDrawRect = remember(containerWidth, containerHeight, bitmap.width, bitmap.height) {
        calculateFittedImageRect(
            containerWidth = containerWidth.toFloat(),
            containerHeight = containerHeight.toFloat(),
            bitmapWidth = bitmap.width.toFloat(),
            bitmapHeight = bitmap.height.toFloat()
        )
    }

    val currentCorners by rememberUpdatedState(corners)
    val currentImageDrawRect by rememberUpdatedState(imageDrawRect)
    val currentOnCornersChange by rememberUpdatedState(onCornersChange)

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged {
                containerWidth = it.width
                containerHeight = it.height
            }
            .pointerInput(interactionEnabled) {
                if (!interactionEnabled) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    val rectAtDown = currentImageDrawRect
                    val cornersAtDown = currentCorners

                    val dragTarget = detectCornerDragTarget(
                        touch = down.position,
                        imageRect = rectAtDown,
                        corners = cornersAtDown
                    )

                    if (dragTarget == CornerDragTarget.NONE) {
                        return@awaitEachGesture
                    }

                    down.consume()

                    val activePointerId = down.id
                    var gestureCorners = cornersAtDown

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == activePointerId }

                        if (change == null || !change.pressed) {
                            break
                        }

                        val dragAmount = change.position - change.previousPosition
                        val activeImageRect = currentImageDrawRect

                        if (
                            activeImageRect.width > 0f &&
                            activeImageRect.height > 0f &&
                            (dragAmount.x != 0f || dragAmount.y != 0f)
                        ) {
                            val dx = dragAmount.x / activeImageRect.width
                            val dy = dragAmount.y / activeImageRect.height

                            val nextCorners = updateDocumentCornersByDrag(
                                corners = gestureCorners,
                                target = dragTarget,
                                dx = dx,
                                dy = dy
                            )

                            gestureCorners = nextCorners
                            currentOnCornersChange(nextCorners)
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Belge köşe seçimi",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (imageDrawRect.width <= 0f || imageDrawRect.height <= 0f) return@Canvas

            val p1 = corners.topLeft.toScreenOffset(imageDrawRect)
            val p2 = corners.topRight.toScreenOffset(imageDrawRect)
            val p3 = corners.bottomRight.toScreenOffset(imageDrawRect)
            val p4 = corners.bottomLeft.toScreenOffset(imageDrawRect)

            drawLine(Color.White, p1, p2, strokeWidth = 5f)
            drawLine(Color.White, p2, p3, strokeWidth = 5f)
            drawLine(Color.White, p3, p4, strokeWidth = 5f)
            drawLine(Color.White, p4, p1, strokeWidth = 5f)

            drawCornerHandle(p1)
            drawCornerHandle(p2)
            drawCornerHandle(p3)
            drawCornerHandle(p4)
        }
    }
}

// CameraX screen used to capture a document photo.
@Composable
private fun CameraCaptureStep(
    onCaptured: (File) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }

                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder()
                            .build()
                            .also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()

                        imageCapture = capture

                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                        }.onFailure {
                            onError("Kamera başlatılamadı: ${it.localizedMessage ?: "Bilinmeyen hata"}")
                        }
                    },
                    ContextCompat.getMainExecutor(ctx)
                )

                previewView
            }
        )

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            Text(
                text = "Kağıdı köşe rehberlerinin içine yerleştir",
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("Vazgeç")
            }

            Button(
                onClick = {
                    val capture = imageCapture
                    if (capture == null) {
                        onError("Kamera henüz hazır değil. Lütfen tekrar deneyin.")
                        return@Button
                    }

                    val file = File.createTempFile(
                        "ocr_capture_",
                        ".jpg",
                        context.cacheDir
                    )

                    val outputOptions = ImageCapture.OutputFileOptions
                        .Builder(file)
                        .build()

                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(
                                outputFileResults: ImageCapture.OutputFileResults
                            ) {
                                onCaptured(file)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                onError(
                                    exception.localizedMessage
                                        ?: "Fotoğraf kaydedilemedi."
                                )
                            }
                        }
                    )
                }
            ) {
                Text("Çek")
            }
        }
    }
}

// Keeps the device screen on while long image processing or OCR operations are running.
@Composable
private fun KeepScreenAwake(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn
        if (enabled) view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previous
        }
    }
}

// Returns the default corner positions as normalized coordinates.
private fun defaultDocumentCorners(): DocumentCorners {
    return DocumentCorners(
        topLeft = Offset(0.08f, 0.08f),
        topRight = Offset(0.92f, 0.08f),
        bottomRight = Offset(0.92f, 0.92f),
        bottomLeft = Offset(0.08f, 0.92f)
    )
}

// Decodes an image URI into a Bitmap and applies EXIF orientation correction.
private fun decodeBitmapFullRespectingOrientation(
    context: Context,
    imageUri: Uri
): Bitmap? {
    return runCatching {
        val sourceBytes = context.contentResolver.openInputStream(imageUri)?.use {
            it.readBytes()
        } ?: return null

        val bitmap = BitmapFactory.decodeByteArray(
            sourceBytes,
            0,
            sourceBytes.size,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null

        val orientation = resolveExifOrientation(
            context = context,
            imageUri = imageUri,
            sourceBytes = sourceBytes
        )

        bitmap.rotateByExifOrientation(orientation)
    }.getOrNull()
}

// Decodes an image URI into a Bitmap and applies EXIF orientation correction.
private fun decodeBitmapFullRespectingOrientation(file: File): Bitmap? {
    return runCatching {
        val sourceBytes = file.readBytes()

        val bitmap = BitmapFactory.decodeByteArray(
            sourceBytes,
            0,
            sourceBytes.size,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null

        val orientation = ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        bitmap.rotateByExifOrientation(orientation)
    }.getOrNull()
}

// Reads EXIF orientation from an image URI or byte array.
private fun resolveExifOrientation(
    context: Context,
    imageUri: Uri?,
    sourceBytes: ByteArray
): Int {
    return runCatching {
        when {
            imageUri != null -> {
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            }

            else -> {
                ByteArrayInputStream(sourceBytes).use { input ->
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            }
        }
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
}

// Rotates or flips the bitmap according to its EXIF orientation.
private fun Bitmap.rotateByExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()

    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.preScale(-1f, 1f)
            matrix.postRotate(270f)
        }

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.preScale(-1f, 1f)
            matrix.postRotate(90f)
        }

        else -> return this
    }

    return Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        matrix,
        true
    ).also {
        if (it != this && !this.isRecycled) {
            this.recycle()
        }
    }
}

// Applies perspective correction using the selected four document corners.
private fun perspectiveCorrectBitmap(
    source: Bitmap,
    corners: DocumentCorners
): Bitmap {
    val srcTopLeft = corners.topLeft.toBitmapPoint(source)
    val srcTopRight = corners.topRight.toBitmapPoint(source)
    val srcBottomRight = corners.bottomRight.toBitmapPoint(source)
    val srcBottomLeft = corners.bottomLeft.toBitmapPoint(source)

    val topWidth = distance(srcTopLeft, srcTopRight)
    val bottomWidth = distance(srcBottomLeft, srcBottomRight)
    val leftHeight = distance(srcTopLeft, srcBottomLeft)
    val rightHeight = distance(srcTopRight, srcBottomRight)

    val outputWidth = max(topWidth, bottomWidth).roundToInt().coerceAtLeast(1)
    val outputHeight = max(leftHeight, rightHeight).roundToInt().coerceAtLeast(1)

    val outputBitmap = Bitmap.createBitmap(
        outputWidth,
        outputHeight,
        Bitmap.Config.ARGB_8888
    )

    val src = floatArrayOf(
        srcTopLeft.x, srcTopLeft.y,
        srcTopRight.x, srcTopRight.y,
        srcBottomRight.x, srcBottomRight.y,
        srcBottomLeft.x, srcBottomLeft.y
    )

    val dst = floatArrayOf(
        0f, 0f,
        outputWidth.toFloat(), 0f,
        outputWidth.toFloat(), outputHeight.toFloat(),
        0f, outputHeight.toFloat()
    )

    val matrix = Matrix()
    val success = matrix.setPolyToPoly(src, 0, dst, 0, 4)

    if (!success) {
        error("Perspektif düzeltme yapılamadı. Köşeleri tekrar seçin.")
    }

    val canvas = AndroidCanvas(outputBitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, matrix, paint)

    return outputBitmap
}

// Converts a Bitmap into PNG bytes without quality loss.
private fun bitmapToLosslessPngBytes(bitmap: Bitmap): ByteArray {
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    return output.toByteArray().also {
        output.close()
    }
}

// Calculates where the image is actually drawn inside the container when using ContentScale.Fit.
private fun calculateFittedImageRect(
    containerWidth: Float,
    containerHeight: Float,
    bitmapWidth: Float,
    bitmapHeight: Float
): Rect {
    if (
        containerWidth <= 0f ||
        containerHeight <= 0f ||
        bitmapWidth <= 0f ||
        bitmapHeight <= 0f
    ) {
        return Rect(0f, 0f, 0f, 0f)
    }

    val containerAspect = containerWidth / containerHeight
    val imageAspect = bitmapWidth / bitmapHeight

    return if (imageAspect > containerAspect) {
        val drawWidth = containerWidth
        val drawHeight = drawWidth / imageAspect
        val top = (containerHeight - drawHeight) / 2f

        Rect(
            left = 0f,
            top = top,
            right = drawWidth,
            bottom = top + drawHeight
        )
    } else {
        val drawHeight = containerHeight
        val drawWidth = drawHeight * imageAspect
        val left = (containerWidth - drawWidth) / 2f

        Rect(
            left = left,
            top = 0f,
            right = left + drawWidth,
            bottom = drawHeight
        )
    }
}

// Converts normalized image coordinates into screen coordinates.
private fun Offset.toScreenOffset(imageRect: Rect): Offset {
    return Offset(
        x = imageRect.left + x.coerceIn(0f, 1f) * imageRect.width,
        y = imageRect.top + y.coerceIn(0f, 1f) * imageRect.height
    )
}

// Converts normalized image coordinates into real bitmap pixel coordinates.
private fun Offset.toBitmapPoint(bitmap: Bitmap): Offset {
    return Offset(
        x = x.coerceIn(0f, 1f) * bitmap.width,
        y = y.coerceIn(0f, 1f) * bitmap.height
    )
}

// Detects whether the user touched a corner handle or the inside of the selected quadrilateral.
private fun detectCornerDragTarget(
    touch: Offset,
    imageRect: Rect,
    corners: DocumentCorners
): CornerDragTarget {
    if (imageRect.width <= 0f || imageRect.height <= 0f) {
        return CornerDragTarget.NONE
    }

    val topLeft = corners.topLeft.toScreenOffset(imageRect)
    val topRight = corners.topRight.toScreenOffset(imageRect)
    val bottomRight = corners.bottomRight.toScreenOffset(imageRect)
    val bottomLeft = corners.bottomLeft.toScreenOffset(imageRect)

    val threshold = 110f

    fun near(point: Offset): Boolean {
        return distance(touch, point) <= threshold
    }

    return when {
        near(topLeft) -> CornerDragTarget.TOP_LEFT
        near(topRight) -> CornerDragTarget.TOP_RIGHT
        near(bottomRight) -> CornerDragTarget.BOTTOM_RIGHT
        near(bottomLeft) -> CornerDragTarget.BOTTOM_LEFT
        isPointInsideQuad(touch, topLeft, topRight, bottomRight, bottomLeft) -> CornerDragTarget.MOVE
        else -> CornerDragTarget.NONE
    }
}

// Updates document corner positions according to the current drag gesture.
private fun updateDocumentCornersByDrag(
    corners: DocumentCorners,
    target: CornerDragTarget,
    dx: Float,
    dy: Float
): DocumentCorners {
    fun Offset.moved(): Offset {
        return Offset(
            x = (x + dx).coerceIn(0f, 1f),
            y = (y + dy).coerceIn(0f, 1f)
        )
    }

    fun Offset.movedBy(deltaX: Float, deltaY: Float): Offset {
        return Offset(
            x = (x + deltaX).coerceIn(0f, 1f),
            y = (y + deltaY).coerceIn(0f, 1f)
        )
    }

    return when (target) {
        CornerDragTarget.TOP_LEFT -> corners.copy(topLeft = corners.topLeft.moved())
        CornerDragTarget.TOP_RIGHT -> corners.copy(topRight = corners.topRight.moved())
        CornerDragTarget.BOTTOM_RIGHT -> corners.copy(bottomRight = corners.bottomRight.moved())
        CornerDragTarget.BOTTOM_LEFT -> corners.copy(bottomLeft = corners.bottomLeft.moved())

        CornerDragTarget.MOVE -> {
            val all = listOf(
                corners.topLeft,
                corners.topRight,
                corners.bottomRight,
                corners.bottomLeft
            )

            val minX = all.minOf { it.x }
            val maxX = all.maxOf { it.x }
            val minY = all.minOf { it.y }
            val maxY = all.maxOf { it.y }

            val safeDx = dx.coerceIn(-minX, 1f - maxX)
            val safeDy = dy.coerceIn(-minY, 1f - maxY)

            DocumentCorners(
                topLeft = corners.topLeft.movedBy(safeDx, safeDy),
                topRight = corners.topRight.movedBy(safeDx, safeDy),
                bottomRight = corners.bottomRight.movedBy(safeDx, safeDy),
                bottomLeft = corners.bottomLeft.movedBy(safeDx, safeDy)
            )
        }

        CornerDragTarget.NONE -> corners
    }
}

// Draws a visible draggable corner handle on the canvas.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerHandle(
    center: Offset
) {
    drawCircle(
        color = Color.Black.copy(alpha = 0.60f),
        radius = 46f,
        center = center
    )

    drawCircle(
        color = Color.White,
        radius = 38f,
        center = center
    )

    drawCircle(
        color = Color(0xFF1565C0),
        radius = 24f,
        center = center
    )

    drawCircle(
        color = Color.White,
        radius = 7f,
        center = center
    )
}

// Returns the Euclidean distance between two points.
private fun distance(a: Offset, b: Offset): Float {
    return hypot(a.x - b.x, a.y - b.y)
}

// Checks whether a point is inside a quadrilateral by splitting it into two triangles.
private fun isPointInsideQuad(
    p: Offset,
    a: Offset,
    b: Offset,
    c: Offset,
    d: Offset
): Boolean {
    return isPointInTriangle(p, a, b, c) || isPointInTriangle(p, a, c, d)
}

// Checks whether a point is inside a triangle using area comparison.
private fun isPointInTriangle(
    p: Offset,
    a: Offset,
    b: Offset,
    c: Offset
): Boolean {
    val area = triangleArea(a, b, c)
    val area1 = triangleArea(p, b, c)
    val area2 = triangleArea(a, p, c)
    val area3 = triangleArea(a, b, p)

    return abs(area - (area1 + area2 + area3)) < 1.5f
}

// Calculates the area of a triangle using its three points.
private fun triangleArea(
    a: Offset,
    b: Offset,
    c: Offset
): Float {
    return abs(
        (a.x * (b.y - c.y) +
                b.x * (c.y - a.y) +
                c.x * (a.y - b.y)) / 2f
    )
}