package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.data.model.JudoGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.ui.theme.JudoGold
import com.example.ui.theme.JudoGoldLight
import com.example.ui.theme.JudoRed
import com.example.ui.theme.JudoSuccess
import com.example.util.ExtractedMemberInfo
import com.example.util.SportCardScanner
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

enum class ScanDialogStep {
    CHOOSE_INPUT,
    LIVE_CAMERA,
    PROCESSING,
    RESULT_PREVIEW
}

@Composable
fun SportCardScanDialog(
    onExtracted: (ExtractedMemberInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(ScanDialogStep.CHOOSE_INPUT) }
    var extractedResult by remember { mutableStateOf<ExtractedMemberInfo?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Traitement de la carte en cours...") }

    fun processBitmap(bitmap: Bitmap) {
        currentStep = ScanDialogStep.PROCESSING
        statusText = "Extraction des informations & photo du judoka..."
        scope.launch {
            val result = SportCardScanner.processCardBitmap(context, bitmap)
            result.onSuccess { info ->
                extractedResult = info
                currentStep = ScanDialogStep.RESULT_PREVIEW
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Erreur lors de la lecture de la carte"
                currentStep = ScanDialogStep.CHOOSE_INPUT
            }
        }
    }

    // Camera take picture preview launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            processBitmap(bitmap)
        }
    }

    // Gallery visual media photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = loadBitmapFromUri(context, uri)
            if (bmp != null) {
                processBitmap(bmp)
            } else {
                errorMessage = "Impossible de charger l'image sélectionnée"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("sport_card_scan_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(JudoRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Scanner Carte Sportive",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                            )
                            Text(
                                text = "Extraction automatique OCR & Photo",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                if (errorMessage.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                when (currentStep) {
                    ScanDialogStep.CHOOSE_INPUT -> {
                        // Card Framing Instruction Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = JudoGold.copy(alpha = 0.12f),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = JudoGoldLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Carte Judo Club Seddouk",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = JudoGoldLight
                                        )
                                    )
                                }
                                Text(
                                    text = "Positionnez la carte bien à plat pour extraire le Nom, Prénom, Date de naissance, Tél tuteur et la Photo du judoka.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        // Scanning options buttons
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Option 1: Take Photo with Camera
                            Button(
                                onClick = {
                                    cameraLauncher.launch(null)
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = JudoRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("scan_dialog_camera_button")
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Prendre une photo de la carte",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            // Option 2: Choose from Gallery / Photo library
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("scan_dialog_gallery_button")
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = JudoRed)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Choisir une photo de la carte (Galerie)",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            // Option 3: Live Camera Viewfinder
                            OutlinedButton(
                                onClick = { currentStep = ScanDialogStep.LIVE_CAMERA },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("scan_dialog_live_button")
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanner en direct (Cadre carte)")
                            }
                        }
                    }

                    ScanDialogStep.LIVE_CAMERA -> {
                        LiveCameraCardViewfinder(
                            onCardCaptured = { bitmap ->
                                processBitmap(bitmap)
                            },
                            onCancel = { currentStep = ScanDialogStep.CHOOSE_INPUT }
                        )
                    }

                    ScanDialogStep.PROCESSING -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = JudoRed,
                                    strokeWidth = 3.5.dp,
                                    modifier = Modifier.size(54.dp)
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Lecture OCR du texte & détection du visage...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    ScanDialogStep.RESULT_PREVIEW -> {
                        val info = extractedResult
                        if (info != null) {
                            CardExtractionPreviewAndEdit(
                                initialInfo = info,
                                onApply = { confirmed ->
                                    onExtracted(confirmed)
                                    onDismiss()
                                },
                                onRestart = { currentStep = ScanDialogStep.CHOOSE_INPUT }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardExtractionPreviewAndEdit(
    initialInfo: ExtractedMemberInfo,
    onApply: (ExtractedMemberInfo) -> Unit,
    onRestart: () -> Unit
) {
    var lastName by remember(initialInfo) { mutableStateOf(initialInfo.lastName) }
    var firstName by remember(initialInfo) { mutableStateOf(initialInfo.firstName) }
    var birthDate by remember(initialInfo) { mutableStateOf(initialInfo.birthDate) }
    var phone by remember(initialInfo) { mutableStateOf(initialInfo.phone) }
    var cardNumber by remember(initialInfo) { mutableStateOf(initialInfo.cardNumber) }
    var isEditMode by remember { mutableStateOf(initialInfo.lastName.isBlank() || initialInfo.firstName.isBlank()) }

    val calculatedCategory = remember(birthDate) {
        if (birthDate.isNotBlank()) JudoGroup.fromBirthDate(birthDate) else initialInfo.group
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Judoka Photo & Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Extracted Photo Avatar
                if (initialInfo.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = initialInfo.photoUrl,
                        contentDescription = "Photo du judoka",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, JudoGold, RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$firstName $lastName".trim().ifBlank { "Judoka détecté" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black
                        )
                    )
                    Text(
                        text = "Catégorie : ${calculatedCategory.label}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = JudoRed,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (cardNumber.isNotBlank()) {
                        Text(
                            text = "Carte N° $cardNumber" + if (initialInfo.season.isNotBlank()) " • Saison ${initialInfo.season}" else "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                IconButton(
                    onClick = { isEditMode = !isEditMode },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (isEditMode) "Valider" else "Ajuster",
                        tint = if (isEditMode) JudoSuccess else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isEditMode) {
                // Inline Edit Fields
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Vérifier & Ajuster les données extraites :",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            label = { Text("Nom") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            label = { Text("Prénom") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { birthDate = it },
                        label = { Text("Date de naissance (AAAA-MM-JJ)") },
                        placeholder = { Text("ex: 2014-06-12") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Tél. Tuteur") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cardNumber,
                            onValueChange = { cardNumber = it },
                            label = { Text("N° Carte") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Details summary list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetailItem(
                        label = "Date de naissance",
                        value = if (birthDate.isNotBlank()) birthDate else "Non détectée sur la carte"
                    )
                    DetailItem(
                        label = "Tél. Tuteur",
                        value = if (phone.isNotBlank()) phone else "Non détecté"
                    )
                    DetailItem(
                        label = "N° de Carte",
                        value = if (cardNumber.isNotBlank()) cardNumber else "Non détecté"
                    )
                    DetailItem(
                        label = "QR Code lié",
                        value = if (initialInfo.qrCode.isNotBlank()) initialInfo.qrCode else "Généré automatiquement"
                    )
                    DetailItem(
                        label = "Photo détectée",
                        value = if (initialInfo.photoUrl.isNotBlank()) "Oui (extraite de la carte)" else "Non"
                    )
                }
            }
        }
    }

    // Actions
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onRestart,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Recommencer")
        }

        Button(
            onClick = {
                val finalInfo = initialInfo.copy(
                    lastName = lastName.trim(),
                    firstName = firstName.trim(),
                    birthDate = birthDate.trim(),
                    phone = phone.trim(),
                    cardNumber = cardNumber.trim(),
                    group = if (birthDate.isNotBlank()) JudoGroup.fromBirthDate(birthDate.trim()) else initialInfo.group,
                    qrCode = if (initialInfo.qrCode.isNotBlank()) initialInfo.qrCode
                        else if (cardNumber.isNotBlank()) "JCS-$cardNumber"
                        else "JCS-${System.currentTimeMillis().toString().takeLast(5)}"
                )
                onApply(finalInfo)
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JudoSuccess),
            modifier = Modifier
                .weight(1.4f)
                .testTag("apply_card_scan_result_button")
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Remplir la fiche",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun LiveCameraCardViewfinder(
    onCardCaptured: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { pView ->
                    previewView = pView
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(pView.surfaceProvider)
                        }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                (ctx as androidx.lifecycle.LifecycleOwner),
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Card Framing Guide Overlay
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.85f)
                .height(180.dp)
                .border(2.dp, JudoGold, RoundedCornerShape(12.dp))
        )

        // Capture button overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Retour")
            }

            Button(
                onClick = {
                    val pView = previewView
                    val bmp = pView?.bitmap
                    if (bmp != null) {
                        onCardCaptured(bmp)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = JudoRed)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Capturer")
            }
        }
    }
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
