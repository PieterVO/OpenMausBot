package com.openmausbot.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Chat

@Composable
internal fun ChatPoster(chat: Chat, empty: Boolean, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("chat-poster").padding(top = 20.dp, bottom = 28.dp)
        .clickable(role = Role.Button, onClick = onOpen), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatAvatar(chat, size = 88.dp)
        Text(chat.name, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        if (chat.subtitle.isNotBlank()) Text(chat.subtitle, style = MaterialTheme.typography.bodyMedium, color = secondaryTint)
        (chat as? Chat.BotChat)?.bot?.modelSelection?.let { model ->
            Text(model.model, style = MaterialTheme.typography.bodySmall, color = secondaryTint)
        }
        if (empty) Text(stringResource(R.string.mobile_chat_say_hi, chat.name), style = MaterialTheme.typography.bodyMedium,
            color = chatTint.ink, modifier = Modifier.padding(top = 8.dp))
    }
}
