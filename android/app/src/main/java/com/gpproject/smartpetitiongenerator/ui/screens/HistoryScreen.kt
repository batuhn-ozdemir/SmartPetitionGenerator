package com.gpproject.smartpetitiongenerator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gpproject.smartpetitiongenerator.data.local.PetitionEntity
import com.gpproject.smartpetitiongenerator.ui.viewmodel.PetitionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: PetitionViewModel
) {
    // Observes the saved petition history from the ViewModel.
    val historyList by viewModel.historyList.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Screen title.
        Text(
            text = "Geçmiş Dilekçelerim",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1565C0),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (historyList.isEmpty()) {
            // Empty state shown when there are no saved petitions.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Henüz kayıtlı bir dilekçeniz yok.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Displays saved petitions in a scrollable list.
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyList) { petition ->
                    HistoryItemCard(
                        petition = petition,

                        // Deletes the selected petition from local history.
                        onDeleteClick = {
                            viewModel.deletePetition(petition)
                        },

                        // Opens the selected petition in preview/edit mode.
                        onEditClick = {
                            navController.navigate("preview_screen/${petition.id}?mode=edit")
                        },

                        // Opens the selected petition in preview/share mode.
                        onShareClick = {
                            navController.navigate("preview_screen/${petition.id}?mode=share")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    petition: PetitionEntity,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit,
    onShareClick: () -> Unit
) {
    // Converts the saved timestamp into a readable Turkish date format.
    val dateString = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("tr", "TR"))
        .format(Date(petition.createdDate))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Top section: petition title and creation date.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = petition.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom section: action buttons.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // Opens PDF/share flow.
                IconButton(onClick = onShareClick) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "PDF",
                        tint = Color(0xFF4CAF50)
                    )
                }

                // Opens edit/preview flow.
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Düzenle",
                        tint = Color(0xFF1565C0)
                    )
                }

                // Deletes the petition.
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}