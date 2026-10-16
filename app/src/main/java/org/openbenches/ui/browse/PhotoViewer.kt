package org.openbenches.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import org.openbenches.data.BenchMedia
import org.openbenches.data.mediaUrl
import org.openbenches.ui.theme.PlaqueGold
import org.openbenches.ui.theme.SoftWhite
import org.openbenches.ui.zoomable

/** Full-screen photos: swipe between them, pinch to zoom, back or ✕ to close. */
@Composable
fun PhotoViewer(photos: List<BenchMedia>, startIndex: Int, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(initialPage = startIndex) { photos.size }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("photoViewer")) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                SubcomposeAsyncImage(
                    model = mediaUrl(photos[page].url),
                    contentDescription = mediaTypeLabel(photos[page].mediaType),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().zoomable(),
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PlaqueGold)
                        }
                    },
                )
            }
            val media = photos[pager.currentPage]
            Row(
                Modifier.fillMaxWidth().safeDrawingPadding().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${pager.currentPage + 1} / ${photos.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SoftWhite,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close photos", tint = SoftWhite) }
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                    .safeDrawingPadding().padding(16.dp),
            ) {
                Text(mediaTypeLabel(media.mediaType), style = MaterialTheme.typography.titleMedium, color = SoftWhite)
                val credit = listOfNotNull(media.username?.let { "by $it" }, media.licence).joinToString(" · ")
                if (credit.isNotEmpty()) Text(credit, style = MaterialTheme.typography.bodySmall, color = SoftWhite.copy(alpha = 0.8f))
            }
        }
    }
}
