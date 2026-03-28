package com.gpproject.smartpetitiongenerator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
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
    // Veritabanındaki listeyi anlık takip et
    val historyList by viewModel.historyList.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Başlık
        Text(
            text = "Geçmiş Dilekçelerim",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1565C0),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (historyList.isEmpty()) {
            // Liste boşsa kullanıcıya bilgi ver
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Henüz kayıtlı bir dilekçeniz yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Liste Doluysa Göster
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyList) { petition ->
                    HistoryItemCard(
                        petition = petition,
                        onDeleteClick = { viewModel.deletePetition(petition) },
                        onEditClick = {
                            // Tıklandığında Preview ekranına HTML içeriğini gönder
                            // Not: Gerçek uygulamada ID gönderip veritabanından çekmek daha sağlıklıdır.
                            // Şimdilik string encode ile gönderiyoruz.
                            navController.navigate("preview_screen/${petition.id}?mode=edit")
                        },
                        onShareClick = {
                            // PDF indirme fonksiyonu buraya bağlanacak
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
    // Tarih formatlama (Long -> String)
    val dateString = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("tr", "TR"))
        .format(Date(petition.createdDate))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Üst Satır: Başlık ve Tarih
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Alt Satır: Aksiyon Butonları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // PDF İndir Butonu
                IconButton(onClick = onShareClick) {
                    Icon(Icons.Default.Share, contentDescription = "PDF", tint = Color(0xFF4CAF50))
                }

                // Düzenle/Görüntüle Butonu
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = Color(0xFF1565C0))
                }

                // Sil Butonu
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red)
                }
            }
        }
    }
}