package com.gpproject.smartpetitiongenerator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.RadioButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gpproject.smartpetitiongenerator.ui.viewmodel.AiState
import com.gpproject.smartpetitiongenerator.ui.viewmodel.PetitionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePetitionScreen(
    navController: NavController,
    viewModel: PetitionViewModel,
    readyTemplateId: String?,
    showPromptInput: Boolean = true
) {
    // User prompt text for AI-based petition generation.
    var promptText by remember { mutableStateOf("") }

    // Observed state values from the ViewModel.
    val aiState by viewModel.aiState
    val userProfile by viewModel.userProfile

    // Controls whether the dynamic input form bottom sheet is visible.
    var showFormSheet by remember { mutableStateOf(false) }

    // Stores dynamic form values by input field key.
    val formValues = remember { mutableStateMapOf<String, String>() }

    // Prevents applying the same ready template multiple times after recomposition.
    var appliedTemplateId by remember { mutableStateOf<String?>(null) }

    // Leaves the ready template form and resets temporary ViewModel state.
    val leaveTemplateForm: () -> Unit = {
        viewModel.resetState()
        navController.popBackStack()
    }

    // Handles Android back button behavior for ready template flow.
    BackHandler(enabled = !showPromptInput && (showFormSheet || aiState is AiState.NeedsInput)) {
        leaveTemplateForm()
    }

    // Starts the ready template flow if this screen was opened with a template ID.
    LaunchedEffect(readyTemplateId) {
        if (!readyTemplateId.isNullOrBlank() && readyTemplateId != appliedTemplateId) {
            viewModel.getReadyTemplateById(readyTemplateId)?.let { template ->
                viewModel.startReadyTemplateFlow(template)
                appliedTemplateId = readyTemplateId
            }
        }
    }

    // Reacts to AI state changes and navigates when the final petition is ready.
    LaunchedEffect(aiState) {
        when (val state = aiState) {
            is AiState.NeedsInput -> showFormSheet = true

            is AiState.Success -> {
                showFormSheet = false
                navController.navigate("preview_screen/new")
                viewModel.resetState()
            }

            else -> {}
        }
    }

    // Initializes dynamic form values using AI-given values or saved user profile data.
    LaunchedEffect(aiState, userProfile) {
        val state = aiState

        if (state is AiState.NeedsInput) {
            formValues.clear()

            val given = state.petitionData.givenParams ?: emptyMap()
            val fields = state.petitionData.requiredParams.orEmpty()

            fields.forEach { field ->
                val fromGiven = given[field.key]

                // Auto-fill common personal fields from the saved profile if AI did not provide them.
                val fromProfile = when (field.key) {
                    "AD_SOYAD" -> userProfile?.fullName
                    "TCKN" -> userProfile?.identityNumber
                    "TELEFON" -> userProfile?.phoneNumber
                    "ADRES" -> userProfile?.address
                    else -> null
                }

                val initial = when {
                    !fromGiven.isNullOrBlank() -> fromGiven
                    !fromProfile.isNullOrBlank() -> fromProfile
                    else -> ""
                }

                formValues[field.key] = initial
            }
        }
    }

    // Keeps the device screen awake while AI generation is loading.
    KeepScreenAwake(aiState is AiState.Loading)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                if (showPromptInput) "Yapay Zeka Asistanı" else "Hazır Şablon Formu",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (showPromptInput) {
                Text(
                    "Nasıl bir dilekçe yazdırmak istediğinizi detaylıca anlatın.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Text input where the user describes the petition request.
                OutlinedTextField(
                    value = promptText,
                    onValueChange = {
                        if (it.length <= 500) promptText = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = {
                        Text("Örn: 20 Şubat'ta yediğim hatalı park cezasına itiraz etmek istiyorum. O gün hastanedeydim...")
                    },
                    supportingText = {
                        Text(
                            text = "${promptText.length} / 500",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    }
                )

                // Starts AI petition draft generation.
                Button(
                    onClick = { viewModel.generatePetition(promptText) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    enabled = aiState !is AiState.Loading && promptText.isNotBlank()
                ) {
                    if (aiState is AiState.Loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("TASLAĞI OLUŞTUR")
                    }
                }
            } else {
                // Info text shown when the screen is opened from a ready template.
                Text(
                    "Hazır şablona tıkladığınız için form doğrudan açıldı. Alanları boş bırakabilirsiniz; metinde {parametre_adi} olarak işaretlenir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            // Shows AI or backend error messages.
            if (aiState is AiState.Error) {
                Text(
                    (aiState as AiState.Error).message,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Dynamic input form shown as a bottom sheet.
        if (showFormSheet && aiState is AiState.NeedsInput) {
            val data = (aiState as AiState.NeedsInput).petitionData
            val fields = data.requiredParams.orEmpty()

            // Checks whether this template supports attachments.
            val supportsAttachments =
                fields.any { it.key == "EKLER_LISTESI" } ||
                        data.templateHtml.orEmpty().contains("{{EKLER_BOLUMU}}")

            val hasAttachmentField = fields.any { it.key == "EKLER_LISTESI" }

            // Stores whether the user wants to include attachments.
            var includeAttachments by remember(data) {
                mutableStateOf(
                    formValues["EKLER_LISTESI"].orEmpty().isNotBlank() ||
                            formValues["EK_VAR_MI"].orEmpty().equals("evet", ignoreCase = true)
                )
            }

            ModalBottomSheet(
                onDismissRequest = {
                    if (showPromptInput) {
                        showFormSheet = false
                    } else {
                        leaveTemplateForm()
                    }
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Eksik Bilgiler",
                                style = MaterialTheme.typography.headlineSmall
                            )

                            // Saves the current generated petition structure as a reusable template.
                            IconButton(
                                onClick = {
                                    val templateName = if (promptText.isNotBlank()) {
                                        if (promptText.length > 24) {
                                            promptText.take(24) + "..."
                                        } else {
                                            promptText
                                        }
                                    } else {
                                        "Özel Şablon"
                                    }

                                    viewModel.saveAsTemplate(templateName, data)
                                }
                            ) {
                                Text(
                                    text = "📄",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }

                        Text(
                            "Aşağıdaki alanları doldurabilir veya yapay zekanın tamamlaması için boş bırakabilirsiniz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (fields.isEmpty() && !supportsAttachments) {
                            Text(
                                "Şablonda eksik alan bulunamadı. Direkt oluşturabilirsiniz.",
                                color = Color.Gray
                            )
                        } else {
                            // Shows attachment selection if this template supports attachments.
                            if (supportsAttachments) {
                                Text(
                                    "Ekler eklenecek mi?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = includeAttachments,
                                        onClick = {
                                            includeAttachments = true
                                            formValues["EK_VAR_MI"] = "Evet"
                                        }
                                    )
                                    Text("Evet", modifier = Modifier.padding(end = 16.dp))

                                    RadioButton(
                                        selected = !includeAttachments,
                                        onClick = {
                                            includeAttachments = false
                                            formValues["EK_VAR_MI"] = "Hayır"
                                            formValues["EKLER_LISTESI"] = ""
                                        }
                                    )
                                    Text("Hayır")
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Creates text fields dynamically from required input fields.
                            fields.forEach { field ->
                                if (field.key == "EK_VAR_MI") return@forEach
                                if (field.key == "EKLER_LISTESI" && !includeAttachments) return@forEach

                                val inputVal = formValues[field.key].orEmpty()

                                OutlinedTextField(
                                    value = inputVal,
                                    onValueChange = { newValue ->
                                        if (newValue.length <= 200) {
                                            formValues[field.key] = newValue
                                        }
                                    },
                                    label = { Text(field.label) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    keyboardOptions = when (field.type) {
                                        "number" -> KeyboardOptions(keyboardType = KeyboardType.Number)
                                        "phone" -> KeyboardOptions(keyboardType = KeyboardType.Phone)
                                        else -> KeyboardOptions.Default
                                    },
                                    placeholder = { Text("Bu alanı doldurun") },
                                    singleLine = field.type != "text" || field.key.contains("TARIH")
                                )
                            }
                        }

                        // If the template supports attachments but does not define an attachment field,
                        // create an extra attachment input manually.
                        if (supportsAttachments && includeAttachments && !hasAttachmentField) {
                            OutlinedTextField(
                                value = formValues["EKLER_LISTESI"].orEmpty(),
                                onValueChange = { newValue ->
                                    if (newValue.length <= 200) {
                                        formValues["EKLER_LISTESI"] = newValue
                                    }
                                },
                                label = { Text("Ekler (her satıra bir ek)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                placeholder = { Text("Bu alanı doldurun") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Submits the dynamic form and generates the final petition.
                    Button(
                        onClick = {
                            val payload = if (hasAttachmentField && !includeAttachments) {
                                formValues
                                    .toMap()
                                    .filterKeys {
                                        it != "EKLER_LISTESI" && it != "EKLER_BOLUMU"
                                    }
                                    .plus("EK_VAR_MI" to "Hayır")
                            } else {
                                val ekValue = if (supportsAttachments && includeAttachments) {
                                    "Evet"
                                } else {
                                    "Hayır"
                                }

                                formValues.toMap().plus("EK_VAR_MI" to ekValue)
                            }

                            viewModel.submitDynamicForm(data, payload)
                        },
                        enabled = aiState !is AiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (aiState is AiState.Loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text("FİNAL DİLEKÇEYİ ÜRET")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun KeepScreenAwake(enabled: Boolean) {
    val view = LocalView.current

    // Temporarily keeps the screen on while the given state is enabled.
    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn

        if (enabled) {
            view.keepScreenOn = true
        }

        // Restore the previous keepScreenOn value when this effect leaves composition.
        onDispose {
            view.keepScreenOn = previous
        }
    }
}