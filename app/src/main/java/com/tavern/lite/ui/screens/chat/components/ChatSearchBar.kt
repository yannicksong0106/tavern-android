package com.tavern.lite.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tavern.lite.R

@Composable
fun ChatSearchBar(
    visible: Boolean,
    searchQuery: String,
    searchResults: List<*>,
    currentSearchIndex: Int,
    onQueryChange: (String) -> Unit,
    onPreviousResult: () -> Unit,
    onNextResult: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.search_messages)) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
            if (searchQuery.isNotBlank()) {
                if (searchResults.isNotEmpty()) {
                    Text(
                        text = "${currentSearchIndex + 1}/${searchResults.size}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = onPreviousResult) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.previous))
                    }
                    IconButton(onClick = onNextResult) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.next))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        }
    }
}
