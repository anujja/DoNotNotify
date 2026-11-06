package com.donotnotify.donotnotify.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A tab label followed by its count in a small pill. The pill is filled with the primary
 * colour on the selected tab and a faint tint of the label colour elsewhere. It sits beside
 * the title and drops below it only when a long translated title leaves no room.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabTitleWithCount(title: String, count: Int, selected: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(title, modifier = Modifier.align(Alignment.CenterVertically))
        CountPill(count, selected, Modifier.align(Alignment.CenterVertically))
    }
}

@Composable
fun CountPill(count: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else LocalContentColor.current.copy(alpha = 0.12f),
        label = "pillContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else LocalContentColor.current,
        label = "pillContent",
    )
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .background(container, CircleShape)
            .defaultMinSize(minWidth = 18.dp)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
