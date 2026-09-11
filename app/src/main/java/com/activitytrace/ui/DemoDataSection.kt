package com.activitytrace.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.activitytrace.R

/** Entry card into the demo-data screen, shared by the Settings and the welcome screen. */
@Composable
fun DemoDataSection(onNavigate: () -> Unit) {
    Text(
        text = stringResource(R.string.demo_data_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate() },
    ) {
        Text(
            text = stringResource(R.string.demo_data_description),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
}