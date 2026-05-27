package com.activitytrace.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.activitytrace.model.CapturedItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    SearchBar(
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onSearch = { viewModel.onSearch(query) },
        active = true,
        onActiveChange = {},
        modifier = modifier.fillMaxSize(),
    ) {
        val grouped = results.groupBy { it.appPackage }

        LazyColumn {
            if (query.isNotBlank() && results.isEmpty()) {
                item {
                    Text(
                        text = "No results for \"$query\"",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            grouped.forEach { (app, appItems) ->
                item(key = "header_$app") {
                    Text(
                        text = app,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                appItems.forEach { captured ->
                    item(key = captured.id) {
                        ResultCard(captured)
                    }
                }
                item(key = "divider_$app") {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultCard(item: CapturedItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(),
    ) {
        ListItem(
            headlineContent = { Text(item.text, maxLines = 2) },
            supportingContent = { Text(formatTimestamp(item.timestamp)) },
        )
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
