@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.capntrips.kernelflasher.ui.screens.slot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.github.capntrips.kernelflasher.ui.components.GlassCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.github.capntrips.kernelflasher.ui.localization.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.components.SlotCard

@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@ExperimentalUnitApi
@Composable
fun ColumnScope.SlotContent(
    viewModel: SlotViewModel,
    slotSuffix: String,
    navController: NavController
) {
    val context = LocalContext.current

    // --- SLOT CARD (HEADER) ---
    // SlotCard bawaan sudah kita bagusin sebelumnya, jadi biarkan saja
    SlotCard(
        title = stringResource(if (slotSuffix == "_a") R.string.slot_a else if (slotSuffix == "_b") R.string.slot_b else R.string.slot),
        viewModel = viewModel,
        navController = navController,
        isSlotScreen = true
    )

    AnimatedVisibility(!viewModel.isRefreshing) {
        Column {
            Spacer(Modifier.height(24.dp))

            // --- GROUP 1: CORE OPERATIONS ---
            Text(
                text = stringResource(R.string.kf_core_operations),
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
                    // FLASH
                    if (viewModel.isActive) {
                        SlotActionItem(
                            icon = Icons.Default.SystemUpdate, // Ikon Flash
                            title = stringResource(R.string.flash),
                            subtitle = stringResource(R.string.kf_flash_ak3_or_partition_images),
                            onClick = { navController.navigate("slot$slotSuffix/flash") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                    }

                    // BACKUP
                    SlotActionItem(
                        icon = Icons.Default.Save, // Ikon Backup
                        title = stringResource(R.string.backup),
                        subtitle = stringResource(R.string.kf_backup_boot_dtbo_and_others),
                        onClick = {
                            viewModel.clearFlash(context)
                            navController.navigate("slot$slotSuffix/backup")
                        }
                    )

                    // RESTORE
                    if (viewModel.isActive) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                        SlotActionItem(
                            icon = Icons.Default.Restore, // Ikon Restore
                            title = stringResource(R.string.restore),
                            subtitle = stringResource(R.string.kf_restore_partitions_from_backups),
                            onClick = { navController.navigate("slot$slotSuffix/backups") }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- GROUP 2: UTILITIES ---
            Text(
                text = stringResource(R.string.kf_utilities),
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
                    // CHECK KERNEL VERSION
                    SlotActionItem(
                        icon = Icons.Default.Memory,
                        title = stringResource(R.string.check_kernel_version),
                        subtitle = stringResource(R.string.kf_read_version_from_kernel),
                        onClick = { if (!viewModel.isRefreshing) viewModel.getKernel(context) }
                    )

                    // VENDOR DLKM LOGIC
                    if (viewModel.hasVendorDlkm) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                        
                        AnimatedVisibility(!viewModel.isRefreshing) {
                            Column {
                                // MOUNTED
                                AnimatedVisibility(viewModel.isVendorDlkmMounted) {
                                    SlotActionItem(
                                        icon = Icons.Default.Eject,
                                        title = stringResource(R.string.unmount_vendor_dlkm),
                                        subtitle = stringResource(R.string.kf_unmount_vendor_dlkm_partition),
                                        onClick = { viewModel.unmountVendorDlkm(context) }
                                    )
                                }

                                // NOT MOUNTED BUT MAPPED
                                AnimatedVisibility(!viewModel.isVendorDlkmMounted && viewModel.isVendorDlkmMapped) {
                                    Column {
                                        SlotActionItem(
                                            icon = Icons.Default.SdStorage,
                                            title = stringResource(R.string.mount_vendor_dlkm),
                                            subtitle = stringResource(R.string.kf_mount_vendor_dlkm_system),
                                            onClick = { viewModel.mountVendorDlkm(context) }
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                                        SlotActionItem(
                                            icon = Icons.Default.LinkOff,
                                            title = stringResource(R.string.unmap_vendor_dlkm),
                                            subtitle = stringResource(R.string.kf_unmap_vendor_dlkm_device),
                                            onClick = { viewModel.unmapVendorDlkm(context) }
                                        )
                                    }
                                }

                                // NOT MAPPED
                                AnimatedVisibility(!viewModel.isVendorDlkmMounted && !viewModel.isVendorDlkmMapped) {
                                    SlotActionItem(
                                        icon = Icons.Default.Link,
                                        title = stringResource(R.string.map_vendor_dlkm),
                                        subtitle = stringResource(R.string.kf_map_vendor_dlkm_partition),
                                        onClick = { viewModel.mapVendorDlkm(context) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Spacer bawah agar tidak kepotong navbar/scroll
            Spacer(Modifier.height(100.dp))
        }
    }
}

// --- HELPER COMPONENT ---

@Composable
fun SlotActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
