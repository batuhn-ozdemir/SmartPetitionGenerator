package com.gpproject.smartpetitiongenerator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.gpproject.smartpetitiongenerator.data.seed.ReadyPetitionTemplate
import com.gpproject.smartpetitiongenerator.ui.viewmodel.PetitionViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    templates: List<ReadyPetitionTemplate>,
    viewModel: PetitionViewModel
) {
    // Stores the current search text entered by the user.
    var searchQuery by remember { mutableStateOf("") }

    // Holds the AI-generated template selected for deletion.
    var pendingDeleteTemplate by remember { mutableStateOf<ReadyPetitionTemplate?>(null) }

    // Filters templates by title or category according to the search query.
    val filtered = remember(searchQuery, templates) {
        if (searchQuery.isBlank()) {
            templates
        } else {
            templates.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // App title.
        Text(
            text = "Smart Petition",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1565C0),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Search field for filtering ready petition templates.
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Dilekçe şablonu ara...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Hızlı Başlangıç",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Opens a blank A4 page without selecting a ready template.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = {
                        viewModel.clearCurrentPreview()
                        viewModel.resetState()
                        navController.navigate("preview_screen/new")
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFF1565C0)
                )

                Column {
                    Text(
                        "Kendin Dilekçe Oluştur",
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        "Forma girmeden direkt boş A4 açılır",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Shows the number of templates after filtering.
        Text(
            "Hazır Şablonlar (${filtered.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Displays ready templates in a two-column grid.
        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                count = filtered.size,
                key = { index -> filtered[index].id }
            ) { index ->
                val template = filtered[index]

                ReadyTemplateCard(
                    template = template,
                    onClick = {
                        navController.navigate("template_form/${template.id}")
                    },
                    onLongPress = {
                        // Only AI-generated templates can be deleted from the home screen.
                        if (template.isAiGenerated) {
                            pendingDeleteTemplate = template
                        }
                    }
                )
            }
        }

        // Confirmation dialog for deleting AI-generated templates.
        pendingDeleteTemplate?.let { target ->
            AlertDialog(
                onDismissRequest = {
                    pendingDeleteTemplate = null
                },
                title = {
                    Text("Şablon silinsin mi?")
                },
                text = {
                    Text("\"${target.title}\" AI tarafından kaydedilen şablonlardan silinecek.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAiTemplate(target.id)
                            pendingDeleteTemplate = null
                        }
                    ) {
                        Text("Sil")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteTemplate = null
                        }
                    ) {
                        Text("Vazgeç")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadyTemplateCard(
    template: ReadyPetitionTemplate,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    // Shows a short preview of the first required input fields.
    val parameterPreview = remember(template.requiredFields) {
        template.requiredFields
            .map { it.label }
            .take(3)
            .joinToString(", ")
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    // Main icon based on the template category.
                    Icon(
                        imageVector = iconForCategory(template.category),
                        contentDescription = null,
                        tint = Color(0xFF1565C0)
                    )

                    // Small AI badge shown only for AI-generated templates.
                    if (template.isAiGenerated) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "AI şablonu",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 0.dp, top = 0.dp)
                                .background(
                                    Color(0xFFE8F5E9),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Template title.
                Text(
                    text = template.title,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Template category.
                Text(
                    text = template.category,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Short preview of required fields.
                Text(
                    text = "Parametreler: $parameterPreview",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Selects an icon according to the template category text.
private fun iconForCategory(category: String): ImageVector {
    val normalized = category.lowercase()

    return when {
        "hukuk" in normalized || "idari" in normalized ->
            Icons.Default.Gavel

        "eğitim" in normalized || "egitim" in normalized ->
            Icons.Default.School

        "finans" in normalized || "banka" in normalized || "kredi" in normalized ->
            Icons.Default.AccountBalance

        "sağlık" in normalized || "saglik" in normalized || "aile hekimi" in normalized ->
            Icons.Default.MedicalServices

        "belediye" in normalized || "altyapı" in normalized ||
                "altyapi" in normalized || "imar" in normalized ->
            Icons.Default.Apartment

        "çalışma" in normalized || "is hayati" in normalized ||
                "iş" in normalized || "is" in normalized ->
            Icons.Default.Work

        "edit" in normalized || "yazım" in normalized ->
            Icons.Default.Edit

        "genel" in normalized ->
            Icons.Default.Description

        else ->
            Icons.Default.Description
    }
}