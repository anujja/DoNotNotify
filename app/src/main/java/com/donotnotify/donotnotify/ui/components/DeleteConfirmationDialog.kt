package com.donotnotify.donotnotify.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.donotnotify.donotnotify.R

@Composable
fun DeleteConfirmationDialog(
    itemName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ConfirmationDialog(
        title = stringResource(R.string.delete_item_title),
        text = stringResource(R.string.delete_item_confirm, itemName),
        confirmLabel = stringResource(R.string.delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
