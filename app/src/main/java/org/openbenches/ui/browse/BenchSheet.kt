package org.openbenches.ui.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import org.openbenches.data.Bench
import org.openbenches.data.BenchMedia
import org.openbenches.data.cleanInscription
import org.openbenches.data.mediaUrl
import org.openbenches.ui.theme.BorderBlack
import org.openbenches.ui.theme.GroundBrown
import org.openbenches.ui.theme.InscriptionStyle
import org.openbenches.ui.theme.PanelBorder
import org.openbenches.ui.theme.PlaqueGold
import org.openbenches.ui.theme.SkyBlue
import org.openbenches.ui.theme.SoftBlack
import org.openbenches.ui.theme.SoftWhite
import org.openbenches.ui.theme.Texture
import org.openbenches.ui.theme.panel
import org.openbenches.ui.theme.textured
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the sheet can do; all callbacks for the bench being shown. */
class BenchSheetActions(
    val onClose: () -> Unit,
    val onDirections: () -> Unit,
    val onShare: () -> Unit,
    val onOpenWebsite: () -> Unit,
    val onPhoto: (index: Int) -> Unit,
    val onRetry: () -> Unit,
    val onPickFromStack: (Bench) -> Unit,
)

/**
 * The bench sheet, styled like a bench page on openbenches.org: the inscription on a gold plaque,
 * gold buttons, and photos with a brown caption bar, all on the wood background.
 */
@Composable
fun BenchSheet(
    bench: Bench,
    stack: List<Bench>,
    details: DetailsState?,
    distance: String?,
    actions: BenchSheetActions,
    modifier: Modifier = Modifier,
) {
    val feature = (details as? DetailsState.Loaded)?.feature
    val inscription = feature?.properties?.popupContent?.let(::cleanInscription) ?: bench.popupContent
    val photos = feature?.properties?.media.orEmpty().filter { it.url.startsWith("/image/") }
    val address = feature?.properties?.address

    Column(
        modifier
            .fillMaxWidth()
            .textured(SoftWhite, Texture.Wood, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .testTag("benchSheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Drag handle and close button.
        Box(Modifier.fillMaxWidth().height(40.dp)) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp).width(40.dp).height(5.dp)
                    .clip(RoundedCornerShape(50)).panel(SoftWhite, Texture.Paper, RoundedCornerShape(50), 1.dp)
            )
            Text(
                "Bench ${bench.id}" + (distance?.let { " · $it away" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = SoftWhite,
                modifier = Modifier.align(Alignment.BottomStart).panel(GroundBrown, Texture.Paper, RoundedCornerShape(50), 1.dp)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            IconButton(
                onClick = actions.onClose,
                modifier = Modifier.align(Alignment.CenterEnd).padding(top = 6.dp).size(36.dp)
                    .panel(SoftWhite, Texture.Paper, RoundedCornerShape(50), 2.dp),
            ) { Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(20.dp)) }
        }

        if (stack.size > 1) StackPicker(bench, stack, actions.onPickFromStack)

        // #benchInscription: the plaque.
        Box(
            Modifier.fillMaxWidth().panel(PlaqueGold, Texture.Brushed).padding(horizontal = 16.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(inscription, style = InscriptionStyle, textAlign = TextAlign.Center, modifier = Modifier.testTag("inscription"))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldButton("Directions", Icons.Filled.Directions, actions.onDirections, Modifier.weight(1f))
            GoldButton("Share", Icons.Filled.Share, actions.onShare, Modifier.weight(1f))
            GoldButton("Website", Icons.Filled.Public, actions.onOpenWebsite, Modifier.weight(1f))
        }

        when {
            details is DetailsState.Failed -> Row(
                Modifier.fillMaxWidth().panel(SoftWhite, Texture.Paper).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Couldn't load photos: ${details.message}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = actions.onRetry) { Text("Retry") }
            }
            details == null || details is DetailsState.Loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SoftBlack, strokeWidth = 3.dp)
            }
            photos.isEmpty() -> Text(
                "No photos of this bench yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().panel(SoftWhite, Texture.Paper).padding(16.dp),
                textAlign = TextAlign.Center,
            )
            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 4.dp),
                modifier = Modifier.testTag("photos"),
            ) {
                itemsIndexed(photos, key = { _, m -> m.mediaId }) { index, media ->
                    PhotoCard(media, onClick = { actions.onPhoto(index) })
                }
            }
        }

        // hgroup: the sky-blue paper panel with where and when.
        Column(
            Modifier.fillMaxWidth().panel(SkyBlue, Texture.Paper).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (address != null) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(address, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("address"))
                }
            }
            feature?.properties?.createdAt?.let(::formatAdded)?.let {
                Text("Added $it", style = MaterialTheme.typography.bodyMedium)
            }
            SelectionContainer {
                Text(
                    String.format(Locale.UK, "%.6f, %.6f", bench.lat, bench.lng),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                )
            }
            if (photos.isNotEmpty()) {
                val credits = photos.mapNotNull { it.username }.distinct().joinToString(", ")
                val licences = photos.mapNotNull { it.licence }.distinct().joinToString(", ")
                Text(
                    "Photos" + (if (credits.isNotEmpty()) " by $credits" else "") + (if (licences.isNotEmpty()) " · $licences" else ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StackPicker(current: Bench, stack: List<Bench>, onPick: (Bench) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${stack.size} benches at this spot",
            style = MaterialTheme.typography.labelLarge,
            color = SoftWhite,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(stack.sortedBy { it.id }, key = { _, b -> b.id }) { _, b ->
                FilterChip(
                    selected = b.id == current.id,
                    onClick = { onPick(b) },
                    label = { Text(b.popupContent.lineSequence().firstOrNull().orEmpty().take(24), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = SoftWhite, selectedContainerColor = PlaqueGold,
                    ),
                    border = BorderStroke(2.dp, BorderBlack),
                )
            }
        }
    }
}

/** The website's .button: plaque gold, bold black text, a small radius. */
@Composable
private fun GoldButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = PlaqueGold, contentColor = SoftBlack),
        border = BorderStroke(2.dp, BorderBlack),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** A bench photo as on the website: image with rounded top and a brown caption bar, inside the thick border. */
@Composable
private fun PhotoCard(media: BenchMedia, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(232.dp).border(PanelBorder, BorderBlack, shape).clip(shape).clickable(onClick = onClick)
            .semantics { contentDescription = "Bench photo: ${mediaTypeLabel(media.mediaType)}" },
    ) {
        SubcomposeAsyncImage(
            model = mediaUrl(media.url),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(170.dp),
            loading = { Box(Modifier.textured(SkyBlue, Texture.Paper, RoundedCornerShape(0.dp))) },
            error = {
                Box(Modifier.textured(SkyBlue, Texture.Paper, RoundedCornerShape(0.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = SoftBlack.copy(alpha = 0.5f))
                }
            },
        )
        Row(
            Modifier.fillMaxWidth().textured(GroundBrown, Texture.Paper, RoundedCornerShape(0.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                mediaTypeLabel(media.mediaType),
                style = MaterialTheme.typography.labelLarge,
                color = SoftWhite,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            media.licence?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SoftBlack,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).textured(SoftWhite, Texture.Paper, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

fun mediaTypeLabel(mediaType: String?): String = when (mediaType?.lowercase()) {
    "inscription" -> "Inscription"
    "plaque" -> "Plaque"
    "bench" -> "The bench"
    "view" -> "View from the bench"
    "pano" -> "Panorama"
    "video" -> "Video"
    else -> "Photo"
}

private fun formatAdded(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK))
}.getOrNull()
