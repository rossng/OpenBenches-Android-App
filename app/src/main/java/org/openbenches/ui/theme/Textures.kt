package org.openbenches.ui.theme

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
