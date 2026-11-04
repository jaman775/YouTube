package com.drishyopot.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drishyopot.app.R

/** Paste a link, then play it, queue it or download it. */
@Composable
fun OpenLinkDialog(
    onPlay: (String) -> Unit,
    onQueue: (String) -> Unit,
    onDownload: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    val link = url.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_open_link)) },
        text = {
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text(stringResource(R.string.open_url_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = link.isNotEmpty(), onClick = { onPlay(link); onDismiss() }) { Text(stringResource(R.string.open_url_play)) }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(enabled = link.isNotEmpty(), onClick = { onQueue(link); onDismiss() }) { Text(stringResource(R.string.add_to_queue)) }
                TextButton(enabled = link.isNotEmpty(), onClick = { onDownload(link); onDismiss() }) { Text(stringResource(R.string.dl_download)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
