package org.openbenches.ui.theme

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openbenches.R

/** The website's panel border: --border-width .3rem in --black-border. */
val PanelBorder = 3.dp
val PanelShape = RoundedCornerShape(16.dp)

/** The website's tileable textures (from transparenttextures.com), drawn over a colour. */
enum class Texture(@DrawableRes val res: Int) {
    Paper(R.drawable.texture_paper),       // hgroup: sky-blue header panels
    Brushed(R.drawable.texture_brushed),   // #benchInscription: the gold plaque
    Wood(R.drawable.texture_wood),         // body background
}

@Composable
private fun rememberTextureBrush(texture: Texture): ShaderBrush {
    val resources = LocalContext.current.resources
    return remember(texture) {
        val bitmap = BitmapFactory.decodeResource(resources, texture.res).asImageBitmap()
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
}

/** A background colour with one of the website's textures multiplied over it, like CSS background-color + background-image. */
@Composable
fun Modifier.textured(color: Color, texture: Texture, shape: Shape = PanelShape): Modifier {
    val brush = rememberTextureBrush(texture)
    return this
        .clip(shape)
        .background(color)
        .drawBehind { drawRect(brush, blendMode = BlendMode.Multiply) }
}

/** A website-style panel: textured fill with the thick soft-black border. */
@Composable
fun Modifier.panel(
    color: Color,
    texture: Texture,
    shape: Shape = PanelShape,
    borderWidth: Dp = PanelBorder,
): Modifier = this
    .border(borderWidth, BorderBlack, shape)
    .textured(color, texture, shape)

/**
 * A small round paper button with the website's border. The visible circle is [size], but it
 * keeps a 48dp touch target, without the grey surround IconButton draws when made smaller.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Box(
        modifier
            .size(maxOf(size, 48.dp))
            .clip(CircleShape)
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).panel(SoftWhite, Texture.Paper, CircleShape, 2.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = SoftBlack, modifier = Modifier.size(size * 0.55f))
        }
    }
}
