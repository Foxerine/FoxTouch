package ai.foxtouch.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ai.foxtouch.R

/**
 * Validates a URL string. Returns `true` when the value is either blank (optional field)
 * or a syntactically valid HTTP(S) URL.
 */
private fun isValidUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return true
    // Must start with http:// or https:// and have a host part
    val pattern = Regex("""^https?://[^\s/$.?#].\S*$""", RegexOption.IGNORE_CASE)
    return pattern.matches(trimmed)
}

/** Returns `true` when the value looks like a domain/path without a scheme prefix. */
private fun needsHttpsPrefix(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return false
    // Heuristic: contains a dot and no spaces → likely a URL missing scheme
    return trimmed.contains('.') && !trimmed.contains(' ')
}

/**
 * Reusable Base URL text field with validation and auto-complete.
 *
 * - Shows error state when the URL is non-empty and malformed.
 * - Offers a quick "Add https://" button when the user types a bare domain.
 */
@Composable
fun BaseUrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = { Text(stringResource(R.string.base_url)) },
    placeholder: @Composable (() -> Unit)? = { Text(stringResource(R.string.base_url_placeholder)) },
    leadingIcon: @Composable (() -> Unit)? = { Icon(Icons.Default.Link, contentDescription = null) },
) {
    val isError by remember(value) { derivedStateOf { !isValidUrl(value) } }
    val showHttpsHint by remember(value) { derivedStateOf { needsHttpsPrefix(value) } }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        singleLine = true,
        isError = isError,
        supportingText = when {
            isError -> ({
                Text(
                    stringResource(R.string.url_invalid),
                    color = MaterialTheme.colorScheme.error,
                )
            })
            showHttpsHint -> ({
                TextButton(onClick = { onValueChange("https://${value.trim()}") }) {
                    Text(
                        stringResource(R.string.url_add_https),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            })
            else -> null
        },
        modifier = modifier.fillMaxWidth(),
    )
}
