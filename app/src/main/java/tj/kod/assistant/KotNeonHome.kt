package tj.kod.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class KotHomeAction(
    val id: String,
    val glyph: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
)

@Composable
fun KotNeonHome(
    viewModel: AssistantViewModel,
    onOpenTask: (String) -> Unit,
) {
    val profileName = viewModel.profiles.firstOrNull {
        it.id == viewModel.activeProfileId
    }?.name ?: "Я"

    val actions = listOf(
        KotHomeAction(
            id = "chat",
            glyph = "●",
            title = "Чат",
            subtitle = "Задать вопрос",
            accent = KotNeonGreen,
        ),
        KotHomeAction(
            id = "web",
            glyph = "⌕",
            title = "Поиск",
            subtitle = "Найти информацию",
            accent = KotNeonCyan,
        ),
        KotHomeAction(
            id = "image",
            glyph = "▣",
            title = "Фото",
            subtitle = "Создать изображение",
            accent = Color(0xFF8C73FF),
        ),
        KotHomeAction(
            id = "video",
            glyph = "▶",
            title = "Видео",
            subtitle = "Создать видео",
            accent = Color(0xFFFFB24C),
        ),
        KotHomeAction(
            id = "code",
            glyph = "</>",
            title = "Программист",
            subtitle = "Код и отладка",
            accent = KotNeonBlue,
        ),
        KotHomeAction(
            id = "models",
            glyph = "∞",
            title = "Офлайн",
            subtitle = "Модели без интернета",
            accent = KotNeonGreen,
        ),
    )

    Scaffold(
        containerColor = KotInk,
        bottomBar = {
            KotBottomBar(onOpenTask = onOpenTask)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF071019),
                            KotInk,
                            KotInk,
                        )
                    )
                )
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                KotTopBar(
                    profileName = profileName,
                    onOpenProfile = { onOpenTask("profiles") },
                )
            }

            item {
                KotHeroCard(
                    status = viewModel.status,
                    onOpenChat = { onOpenTask("chat") },
                )
            }

            item {
                Text(
                    text = "Быстрый доступ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            items(actions.size / 2) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    actions
                        .slice(rowIndex * 2..rowIndex * 2 + 1)
                        .forEach { action ->
                            KotQuickCard(
                                action = action,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenTask(action.id) },
                            )
                        }
                }
            }

            item {
                KotOfflineBanner(
                    onOpen = { onOpenTask("models") },
                )
            }

            item {
                Text(
                    text = "Все возможности",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KotMiniLink(
                        title = "Инструменты",
                        subtitle = "Телефон и файлы",
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenTask("autopilot") },
                    )
                    KotMiniLink(
                        title = "Обновления",
                        subtitle = "Новая версия KOT",
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenTask("updates") },
                    )
                }
            }
        }
    }
}

@Composable
private fun KotTopBar(
    profileName: String,
    onOpenProfile: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    color = Color(0xFF0A1D1A),
                    shape = RoundedCornerShape(15.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_kot_logo),
                contentDescription = "KOT",
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "KOT",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(
                text = "Ваш AI ассистент",
                style = MaterialTheme.typography.bodySmall,
                color = KotMuted,
            )
        }

        Card(
            modifier = Modifier.clickable(onClick = onOpenProfile),
            colors = CardDefaults.cardColors(
                containerColor = KotPanelRaised,
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            ),
        ) {
            Text(
                text = profileName.take(10),
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 9.dp,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = KotNeonGreen,
            )
        }
    }
}

@Composable
private fun KotHeroCard(
    status: String,
    onOpenChat: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenChat),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B141C),
        ),
        border = BorderStroke(
            1.dp,
            KotNeonGreen.copy(alpha = 0.32f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0B171B),
                            Color(0xFF0B1118),
                            Color(0xFF0B1420),
                        )
                    )
                )
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(
                            color = Color(0xFF081F1B),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_kot_logo),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    KotNeonGreen,
                                    CircleShape,
                                )
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "KOT на связи",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    Text(
                        text = "Думаю. Помогаю. Решаю.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KotMuted,
                    )

                    Text(
                        text = status.ifBlank { "Готов к работе" },
                        style = MaterialTheme.typography.bodySmall,
                        color = KotNeonCyan,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun KotQuickCard(
    action: KotHomeAction,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .height(118.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = KotPanelRaised,
        ),
        border = BorderStroke(
            1.dp,
            action.accent.copy(alpha = 0.24f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        action.accent.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = action.glyph,
                    color = action.accent,
                    fontWeight = FontWeight.Black,
                )
            }

            Column {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = action.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = KotMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun KotOfflineBanner(
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0B1D18),
        ),
        border = BorderStroke(
            1.dp,
            KotNeonGreen.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        KotNeonGreen.copy(alpha = 0.12f),
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "↓",
                    color = KotNeonGreen,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Офлайн-модели",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Текст, фото и видео без интернета",
                    style = MaterialTheme.typography.bodySmall,
                    color = KotMuted,
                )
            }

            Text(
                text = "›",
                fontSize = 28.sp,
                color = KotNeonGreen,
            )
        }
    }
}

@Composable
private fun KotMiniLink(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .height(82.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = KotPanel,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(13.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = KotMuted,
            )
        }
    }
}

@Composable
private fun KotBottomBar(
    onOpenTask: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080D13))
            .padding(
                horizontal = 8.dp,
                vertical = 8.dp,
            ),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KotBottomItem(
            label = "Главная",
            glyph = "⌂",
            selected = true,
            onClick = {},
        )
        KotBottomItem(
            label = "Чаты",
            glyph = "●",
            onClick = { onOpenTask("chat") },
        )
        KotBottomItem(
            label = "Инструменты",
            glyph = "◆",
            onClick = { onOpenTask("autopilot") },
        )
        KotBottomItem(
            label = "Загрузки",
            glyph = "↓",
            onClick = { onOpenTask("models") },
        )
        KotBottomItem(
            label = "Профиль",
            glyph = "○",
            onClick = { onOpenTask("profiles") },
        )
    }
}

@Composable
private fun KotBottomItem(
    label: String,
    glyph: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(
                horizontal = 6.dp,
                vertical = 4.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = glyph,
            fontSize = 19.sp,
            color = if (selected) KotNeonGreen else KotMuted,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) KotNeonGreen else KotMuted,
            textAlign = TextAlign.Center,
        )
    }
}
