package com.kiwicup.scheduledmessenger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kiwicup.scheduledmessenger.core.Attachment

/** Thumbnails of media waiting to be sent, each with a remove button. */
@Composable
fun AttachmentStrip(attachments: List<Attachment>, onRemove: (Attachment) -> Unit, modifier: Modifier = Modifier) {
    if (attachments.isEmpty()) return
    LazyRow(modifier = modifier.testTag("attachment_strip")) {
        itemsIndexed(attachments, key = { _, a -> a.uri }) { index, attachment ->
            Box(modifier = Modifier.padding(4.dp)) {
                AttachmentThumb(attachment, size = 72.dp, tag = "pending_attachment_$index")
                IconButton(
                    onClick = { onRemove(attachment) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .testTag("remove_attachment_$index")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                }
            }
        }
    }
}

/** Renders an image, or a labelled placeholder for other media. */
@Composable
fun AttachmentThumb(attachment: Attachment, size: androidx.compose.ui.unit.Dp, tag: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .testTag(tag)
    ) {
        if (attachment.isImage) {
            AsyncImage(
                model = attachment.uri,
                contentDescription = "Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
                Text(
                    text = if (attachment.isVideo) "Video" else attachment.mimeType.substringBefore('/').replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
