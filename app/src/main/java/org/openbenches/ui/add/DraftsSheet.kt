package org.openbenches.ui.add

import android.text.format.DateUtils
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.openbenches.data.BenchDraft
import org.openbenches.data.DraftStore
import org.openbenches.map.distanceMeters
import org.openbenches.map.formatDistance
import org.openbenches.ui.theme.BorderBlack
import org.openbenches.ui.theme.RoundIconButton
import org.openbenches.ui.theme.SoftBlack
import org.openbenches.ui.theme.SoftWhite
import org.openbenches.ui.theme.Texture
import org.openbenches.ui.theme.textured
import java.io.File

/** Benches photographed but not yet uploaded, newest first. Tap one to finish it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsSheet(
    drafts: List<BenchDraft>,
    userLocation: Pair<Double, Double>?,
    onOpen: (BenchDraft) -> Unit,
    onDelete: (BenchDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { DraftStore.get(context) }
    var deleting by remember { mutableStateOf<BenchDraft?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SoftWhite,
    ) {
        Column(Modifier.textured(SoftWhite, Texture.Paper, RoundedCornerShape(0.dp)).navigationBarsPadding().testTag("draftsSheet")) {
            Text("Benches to finish", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "Add the inscription and check each one, then upload it.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (drafts.isEmpty()) {
                Text("Nothing left to finish.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp))
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(drafts, key = { it.id }) { draft ->
                    DraftRow(draft, store.dirFor(draft.id), userLocation, onClick = { onOpen(draft) }, onDelete = { deleting = draft })
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    deleting?.let { draft ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this bench?") },
            text = { Text("Its photos will be deleted from this phone.") },
            confirmButton = { TextButton(onClick = { onDelete(draft); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

/** "Just now", "20 minutes ago", "Yesterday, 4:05 PM" and so on. */
private fun whenTaken(context: android.content.Context, time: Long): String =
    if (System.currentTimeMillis() - time < DateUtils.MINUTE_IN_MILLIS) "Just now"
    else DateUtils.getRelativeDateTimeString(context, time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()

@Composable
private fun DraftRow(
    draft: BenchDraft,
    dir: File,
    userLocation: Pair<Double, Double>?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("draftRow"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = draft.photos.firstOrNull()?.let { File(dir, it.fileName) },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, BorderBlack, RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                draft.inscription.lineSequence().firstOrNull { it.isNotBlank() } ?: "Inscription not added yet",
                style = MaterialTheme.typography.bodyLarge,
                color = if (draft.inscription.isBlank()) SoftBlack.copy(alpha = 0.6f) else SoftBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val parts = listOfNotNull(
                whenTaken(context, draft.createdAt),
                if (draft.photos.size == 1) "1 photo" else "${draft.photos.size} photos",
                draft.location?.let { loc -> userLocation?.let { formatDistance(distanceMeters(it.first, it.second, loc.first, loc.second)) + " away" } },
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = SoftBlack.copy(alpha = 0.7f))
        }
        RoundIconButton(Icons.Filled.Delete, "Delete draft", onClick = onDelete, size = 36.dp)
    }
}
