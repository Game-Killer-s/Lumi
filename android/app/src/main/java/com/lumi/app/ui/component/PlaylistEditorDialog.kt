package com.lumi.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumi.app.R
import com.lumi.app.data.local.LibraryMutations

/**
 * Діалог створення/перейменування плейлиста. Спільний для бібліотеки
 * та екрана плейлиста, щоб валідація назви й опису не розходилась.
 */
@Composable
fun PlaylistEditorDialog(
    initialTitle: String,
    initialDescription: String,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }

    val normalizedTitle = title.trim()
    val titleError = when {
        title.isNotEmpty() && normalizedTitle.isEmpty() ->
            stringResource(R.string.library_title_required)
        normalizedTitle.length > LibraryMutations.PLAYLIST_TITLE_MAX_LENGTH ->
            stringResource(
                R.string.library_title_too_long,
                LibraryMutations.PLAYLIST_TITLE_MAX_LENGTH
            )
        else -> null
    }
    val descriptionError = if (description.length > LibraryMutations.PLAYLIST_DESCRIPTION_MAX_LENGTH) {
        stringResource(
            R.string.library_description_too_long,
            LibraryMutations.PLAYLIST_DESCRIPTION_MAX_LENGTH
        )
    } else {
        null
    }
    val canSave = normalizedTitle.isNotEmpty() && titleError == null && descriptionError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isEdit) R.string.library_rename_playlist else R.string.library_new_playlist
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.library_name)) },
                    singleLine = true,
                    isError = titleError != null,
                    supportingText = titleError?.let { { Text(it) } }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.library_description)) },
                    minLines = 2,
                    isError = descriptionError != null,
                    supportingText = descriptionError?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onConfirm(normalizedTitle, description.trim().ifBlank { null }) }
            ) {
                Text(stringResource(if (isEdit) R.string.library_save else R.string.library_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
