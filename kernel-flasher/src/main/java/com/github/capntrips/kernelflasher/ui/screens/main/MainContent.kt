@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.capntrips.kernelflasher.ui.screens.main

import android.os.Build
import android.widget.Toast // [BARU] Import Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.github.capntrips.kernelflasher.ui.components.GlassCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.github.capntrips.kernelflasher.ui.localization.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModel
import kotlinx.serialization.ExperimentalSerializationApi

@ExperimentalMaterial3Api
@ExperimentalSerializationApi
@Composable
fun ColumnScope.MainContent(
    viewModel: MainViewModel,
    navController: NavController
) {
    val context = LocalContext.current

    // --- 1. DEVICE INFO SECTION ---
    Text(
        text = stringResource(R.string.kf_device_information),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            ModernListItem(
                icon = Icons.Default.PhoneAndroid,
                title = stringResource(R.string.model),
                value = "${Build.MODEL} (${Build.DEVICE})"
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ModernListItem(
                icon = Icons.Default.Tag, 
                title = stringResource(R.string.build_number),
                value = Build.ID
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ModernListItem(
                icon = Icons.Default.Memory,
                title = stringResource(R.string.kernel_version),
                value = viewModel.kernelVersion
            )
            if (viewModel.isAb) {
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ModernListItem(
                    icon = Icons.Default.Layers,
                    title = stringResource(R.string.slot_suffix),
                    value = viewModel.slotSuffix
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    // --- 2. SLOT STATUS SECTION ---
    Text(
        text = stringResource(R.string.kf_boot_slots),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Slot A
        viewModel.slotA?.let { slotA ->
            Box(Modifier.weight(1f)) {
                ModernSlotCard(
                    title = stringResource(R.string.slot_a),
                    slotVM = slotA,
                    isActive = viewModel.slotSuffix == "_a",
                    onClick = { navController.navigate("slot_a") }
                )
            }
        }

        // Slot B
        if (viewModel.isAb) {
            viewModel.slotB?.let { slotB ->
                Box(Modifier.weight(1f)) {
                    ModernSlotCard(
                        title = stringResource(R.string.slot_b),
                        slotVM = slotB,
                        isActive = viewModel.slotSuffix == "_b",
                        onClick = { navController.navigate("slot_b") }
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    // --- 3. TOOLS & ACTIONS SECTION ---
    Text(
        text = stringResource(R.string.kf_tools_actions),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // Backups
            ActionItem(
                icon = Icons.Default.Backup,
                title = stringResource(R.string.backups),
                subtitle = stringResource(R.string.kf_manage_kernel_backups),
                onClick = { navController.navigate("backups") }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
            
            // Updates (FROZEN / TOAST ONLY)
            ActionItem(
                icon = Icons.Default.Update,
                title = stringResource(R.string.updates),
                subtitle = stringResource(R.string.kf_check_for_updates),
                onClick = { 
                    // [MODIFIKASI] Tampilkan Toast alih-alih navigasi
                    Toast.makeText(context, context.getString(R.string.lp_toast_latest_version), Toast.LENGTH_SHORT).show()
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            // Ramoops (Conditional)
            if (viewModel.hasRamoops) {
                ActionItem(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.save_ramoops),
                    subtitle = stringResource(R.string.kf_save_ramoops_log),
                    onClick = { viewModel.saveRamoops(context) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
            }

            // Dmesg & Logcat
            ActionItem(
                icon = Icons.Default.Description,
                title = stringResource(R.string.save_dmesg),
                subtitle = stringResource(R.string.kf_save_kernel_buffer_log),
                onClick = { viewModel.saveDmesg(context) }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            ActionItem(
                icon = Icons.Default.LogoDev,
                title = stringResource(R.string.save_logcat),
                subtitle = stringResource(R.string.kf_save_android_system_log),
                onClick = { viewModel.saveLogcat(context) }
            )
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
            
            // Reboot
            ActionItem(
                icon = Icons.Default.PowerSettingsNew,
                title = stringResource(R.string.reboot),
                subtitle = stringResource(R.string.kf_reboot_device_system),
                onClick = { navController.navigate("reboot") },
                iconColor = MaterialTheme.colorScheme.error
            )
        }
    }
}

// --- CUSTOM MODERN COMPONENTS ---

@Composable
fun ModernListItem(icon: ImageVector, title: String, value: String) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun ModernSlotCard(
    title: String,
    slotVM: SlotViewModel,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        containerColor = containerColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Icon & Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Outlined.SdStorage,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                if (isActive) {
                    Text(
                        text = stringResource(R.string.kf_active),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Info (SHA1)
            Text(
                text = stringResource(R.string.kf_sha1_prefix, slotVM.sha1.take(8)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = contentColor.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // View Button (Small & Integrated)
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.surface.copy(alpha=0.2f) else MaterialTheme.colorScheme.primary.copy(alpha=0.1f),
                    contentColor = contentColor
                )
            ) {
                Text(stringResource(R.string.lp_view_details), fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                // [FIX] Menggunakan Ikon Default agar tidak error
                Icon(Icons.Default.KeyboardArrowRight, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.padding(8.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
