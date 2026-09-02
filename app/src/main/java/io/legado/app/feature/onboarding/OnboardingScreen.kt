package io.legado.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.config.themeConfig.ThemeCard
import io.legado.app.ui.config.themeConfig.ThemeColorSelector
import io.legado.app.ui.config.themeConfig.ThemeModeSelector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    BackHandler {
        onIntent(OnboardingIntent.Prev)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = LegadoTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(56.dp)
            )
            AppText(
                text = pageTitle(state.page),
                style = LegadoTheme.typography.headlineLargeEmphasized,
                modifier = Modifier.padding(top = 8.dp)
            )
            AppText(
                text = pageSummary(state.page),
                style = LegadoTheme.typography.bodyLargeEmphasized,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            AppLinearProgressIndicator(
                progress = state.page * 1f / state.pageCount,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Crossfade(
            targetState = state.page,
            label = "onboarding-page",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> PrivacyPage(state)
                1 -> WebDavPage(state, onIntent)
                2 -> BookFolderPage(state, onIntent)
                else -> ThemePage(state, onIntent)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            MediumTonalButton(
                onClick = { onIntent(OnboardingIntent.Next) },
                text = nextButtonText(state.page, state.pageCount),
                icon = Icons.AutoMirrored.Filled.ArrowForward
            )
        }
    }

    BusyDialog(state, onIntent)
    BackupSelectorDialog(state, onIntent)
    RestoreErrorDialog(state, onIntent)
}

@Composable
private fun BusyDialog(state: OnboardingUiState, onIntent: (OnboardingIntent) -> Unit) {
    val busyText = state.busyText ?: return
    AppAlertDialog(
        show = true,
        onDismissRequest = { onIntent(OnboardingIntent.CancelBusy) },
        content = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppCircularProgressIndicator(modifier = Modifier.size(28.dp))
                AppText(text = busyText)
            }
        }
    )
}

@Composable
private fun BackupSelectorDialog(state: OnboardingUiState, onIntent: (OnboardingIntent) -> Unit) {
    val backupNames = state.backupNames ?: return
    AppAlertDialog(
        show = true,
        onDismissRequest = { onIntent(OnboardingIntent.DismissBackupSelector) },
        title = stringResource(R.string.select_restore_file),
        onDismiss = { onIntent(OnboardingIntent.DismissBackupSelector) },
        content = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                backupNames.forEach { name ->
                    AppText(
                        text = name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onIntent(OnboardingIntent.RestoreBackup(name)) }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun RestoreErrorDialog(state: OnboardingUiState, onIntent: (OnboardingIntent) -> Unit) {
    val message = state.restoreErrorMessage ?: return
    AppAlertDialog(
        show = true,
        onDismissRequest = { onIntent(OnboardingIntent.DismissRestoreError) },
        title = stringResource(R.string.restore),
        text = message,
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(OnboardingIntent.StartLocalRestore) },
        onDismiss = { onIntent(OnboardingIntent.DismissRestoreError) }
    )
}

@Composable
private fun PrivacyPage(state: OnboardingUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AppText(
            text = stringResource(R.string.privacy_policy),
            style = LegadoTheme.typography.titleMedium,
            color = LegadoTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        AppText(
            text = state.privacyPolicy,
            style = LegadoTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        AppText(
            text = stringResource(R.string.disclaimer),
            style = LegadoTheme.typography.titleMedium,
            color = LegadoTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        AppText(
            text = state.disclaimer,
            style = LegadoTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun WebDavPage(state: OnboardingUiState, onIntent: (OnboardingIntent) -> Unit) {
    val presets = remember {
        listOf(
            "https://dav.jianguoyun.com/dav/",
            "https://webdav.aliyundrive.com/",
            "https://soya.infini-cloud.net/dav/"
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppTextField(
            value = state.webDavUrl,
            onValueChange = { onIntent(OnboardingIntent.UpdateWebDavUrl(it)) },
            label = stringResource(R.string.web_dav_url)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                GlassCard(
                    onClick = { onIntent(OnboardingIntent.UpdateWebDavUrl(preset)) },
                    modifier = Modifier.weight(1f)
                ) {
                    AppText(
                        text = preset.removePrefix("https://").removeSuffix("/"),
                        style = LegadoTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
        AppTextField(
            value = state.webDavAccount,
            onValueChange = { onIntent(OnboardingIntent.UpdateWebDavAccount(it)) },
            label = stringResource(R.string.web_dav_account)
        )
        AppTextField(
            value = state.webDavPassword,
            onValueChange = { onIntent(OnboardingIntent.UpdateWebDavPassword(it)) },
            label = stringResource(R.string.web_dav_pw)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            MediumTonalButton(
                onClick = { onIntent(OnboardingIntent.FetchBackups) },
                text = stringResource(R.string.restore),
                modifier = Modifier.weight(1f)
            )
            MediumTonalButton(
                onClick = { onIntent(OnboardingIntent.SaveAndTestWebDav) },
                text = stringResource(R.string.action_save),
                modifier = Modifier.weight(1f)
            )
        }
        AppText(
            text = stringResource(R.string.set_local_password_summary),
            style = LegadoTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        AppTextField(
            value = state.appAccessPassword,
            onValueChange = { onIntent(OnboardingIntent.UpdateAppAccessPassword(it)) },
            label = stringResource(R.string.set_local_password)
        )
    }
}

@Composable
private fun BookFolderPage(state: OnboardingUiState, onIntent: (OnboardingIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AppText(
            text = stringResource(R.string.welcome_book_folder_tip),
            style = LegadoTheme.typography.bodySmall,
            color = LegadoTheme.colorScheme.onSurfaceVariant
        )
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                AppText(
                    text = stringResource(R.string.select_book_folder),
                    style = LegadoTheme.typography.titleMedium
                )
                AppText(
                    text = state.bookFolderUri
                        ?: stringResource(R.string.welcome_book_folder_not_selected),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
                MediumTonalButton(
                    onClick = { onIntent(OnboardingIntent.SelectFolder) },
                    text = stringResource(R.string.select_folder),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ThemePage(state: OnboardingUiState, onIntent: (OnboardingIntent) -> Unit) {
    val context = LocalContext.current
    val isDark = LegadoTheme.isDark
    val themeItems = stringArrayResource(R.array.themes_item)
    val themeValues = stringArrayResource(R.array.themes_value)
    val themes = remember(themeItems, themeValues) {
        themeItems.zip(themeValues).toList()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ThemeCard(
            context = context,
            value = state.theme.appTheme,
            isDark = isDark,
            isAmoled = state.theme.isPureBlack,
            paletteStyle = state.theme.paletteStyle,
            customLightSeedColor = state.theme.customPrimary,
            customNightSeedColor = state.theme.customNightPrimary
        )
        Box(modifier = Modifier.padding(top = 16.dp)) {
            ThemeModeSelector(
                selectedMode = state.themeMode,
                onModeSelected = { onIntent(OnboardingIntent.SetThemeMode(it)) }
            )
        }
        Box(modifier = Modifier.padding(top = 16.dp)) {
            ThemeColorSelector(
                context = context,
                themes = themes,
                selectedTheme = state.theme.appTheme,
                isDark = isDark,
                isAmoled = state.theme.isPureBlack,
                paletteStyle = state.theme.paletteStyle,
                customLightSeedColor = state.theme.customPrimary,
                customNightSeedColor = state.theme.customNightPrimary,
                onThemeSelected = { onIntent(OnboardingIntent.SelectTheme(it)) }
            )
        }
    }
}

@Composable
private fun pageTitle(page: Int): String = stringResource(
    when (page) {
        0 -> R.string.onboarding_title_welcome
        1 -> R.string.onboarding_title_backup
        2 -> R.string.onboarding_title_book_folder
        else -> R.string.onboarding_title_theme
    }
)

@Composable
private fun pageSummary(page: Int): String = stringResource(
    when (page) {
        0 -> R.string.onboarding_summary_welcome
        1 -> R.string.onboarding_summary_backup
        2 -> R.string.onboarding_summary_book_folder
        else -> R.string.onboarding_summary_theme
    }
)

@Composable
private fun nextButtonText(page: Int, pageCount: Int): String = stringResource(
    when {
        page == 0 -> R.string.onboarding_next_agree
        page == pageCount - 1 -> R.string.onboarding_next_done
        else -> R.string.onboarding_next_step
    }
)
