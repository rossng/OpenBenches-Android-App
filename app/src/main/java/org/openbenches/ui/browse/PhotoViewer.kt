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

/** A photo for [PhotoViewer]: anything Coil can load (a URL or a local file), and its captions. */
data class ViewerPhoto(val model: Any, val title: String, val credit: String? = null)

/** Server photos of a bench, captioned with their type, author and licence. */
fun List<BenchMedia>.asViewerPhotos(): List<ViewerPhoto> = map { m ->
    ViewerPhoto(
        model = mediaUrl(m.url),
        title = mediaTypeLabel(m.mediaType),
        credit = listOfNotNull(m.username?.let { "by $it" }, m.licence).joinToString(" · ").ifEmpty { null },
    )
}

/** Full-screen photos: swipe between them, pinch or double-tap to zoom, back or ✕ to close. */
@Composable
fun PhotoViewer(photos: List<ViewerPhoto>, startIndex: Int, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(initialPage = startIndex) { photos.size }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("photoViewer")) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                SubcomposeAsyncImage(
                    model = photos[page].model,
                    contentDescription = photos[page].title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().zoomable(),
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PlaqueGold)
                        }
                    },
                    error = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("This photo isn't available.", style = MaterialTheme.typography.bodyLarge, color = SoftWhite)
                        }
                    },
                )
            }
            val photo = photos[pager.currentPage]
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
                Text(photo.title, style = MaterialTheme.typography.titleMedium, color = SoftWhite)
                photo.credit?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SoftWhite.copy(alpha = 0.8f)) }
            }
        }
    }
}
