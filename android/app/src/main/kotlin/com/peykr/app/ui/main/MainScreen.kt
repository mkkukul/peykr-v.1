package com.peykr.app.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.peykr.app.Main
import com.peykr.app.R
import com.peykr.app.Settings
import com.peykr.app.TrustedPeople
import com.peykr.app.data.DefaultDataRepository
import com.peykr.app.localization.AppLanguage
import com.peykr.app.ui.theme.AppHorizontalPadding
import com.peykr.app.ui.theme.DividerDark
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.PremiumIcon
import com.peykr.app.ui.theme.PremiumIconKind
import com.peykr.app.ui.theme.PremiumInfoRow
import com.peykr.app.ui.theme.PremiumSurface
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.SectionHeader
import com.peykr.app.ui.theme.TextMuted
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary

@Composable
fun MainScreen(onNavigate: (NavKey) -> Unit, onPeopleClick: () -> Unit, onSettingsClick: () -> Unit, modifier: Modifier = Modifier, viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) }) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val count = (state as? MainScreenUiState.Success)?.data?.size ?: 0
  Scaffold(modifier, containerColor = Navy950, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PremiumBottomNavigation(Main, onNavigate) }) { padding ->
    Page(Modifier.padding(padding)) {
      HomeBrand()
      PrototypeBadge()
      FocusEmblem()
      Text(stringResource(R.string.home_headline), color = TextPrimary, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
      Text(stringResource(R.string.home_description), color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
      PremiumSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { PremiumIcon(PremiumIconKind.Backup, stringResource(R.string.system_status)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(stringResource(R.string.system_status), color = TextSecondary, style = MaterialTheme.typography.bodySmall); Text(stringResource(R.string.system_status_value), color = TextPrimary, style = MaterialTheme.typography.titleMedium) }; Box(Modifier.size(9.dp).clip(CircleShape).background(PrimaryTurquoise)) } }
      PremiumInfoRow(PremiumIconKind.People, stringResource(R.string.trusted_people), stringResource(R.string.trusted_people_count, count), stringResource(R.string.trusted_people), onPeopleClick)
      PremiumInfoRow(PremiumIconKind.Location, stringResource(R.string.location_sharing), stringResource(R.string.location_off), stringResource(R.string.location_sharing), onSettingsClick)
      Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
  }
}

@Composable private fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = AppHorizontalPadding).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
@Composable private fun HomeBrand() { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { PremiumIcon(PremiumIconKind.Shield, "PEYKR"); Spacer(Modifier.width(12.dp)); Column { Text("PEYKR", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(stringResource(R.string.home_kicker), color = TextSecondary, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.weight(1f)); Text(stringResource(R.string.prototype_label), color = PrimaryTurquoise, style = MaterialTheme.typography.labelMedium) } }
@Composable private fun PrototypeBadge() { Text(stringResource(R.string.prototype_label), color = PrimaryTurquoise, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 48.dp)) }
@Composable private fun FocusEmblem() { Box(Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.size(160.dp).semantics { contentDescription = "PEYKR güvenlik sembolü" }) { val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f); drawCircle(Color(0x1834D9FF), 66.dp.toPx()); drawCircle(PrimaryTurquoise, 43.dp.toPx(), style = Stroke(2.dp.toPx())); drawArc(Brush.sweepGradient(listOf(PrimaryTurquoise, Color.Transparent)), 210f, 240f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)); drawCircle(PrimaryTurquoise, 4.dp.toPx(), center); drawLine(PrimaryTurquoise, center.copy(y = center.y + 4.dp.toPx()), center.copy(y = center.y + 17.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round); drawLine(PrimaryTurquoise, center.copy(y = center.y + 4.dp.toPx()), center.copy(y = center.y - 9.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round) } } }

@Composable fun TrustedPeopleScreen(onNavigate: (NavKey) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) }) {
  val state by viewModel.uiState.collectAsStateWithLifecycle(); val people = (state as? MainScreenUiState.Success)?.data.orEmpty()
  Scaffold(modifier, containerColor = Navy950, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PremiumBottomNavigation(TrustedPeople, onNavigate) }) { padding -> Page(Modifier.padding(padding)) {
    BackAction(onBack); SectionHeader(stringResource(R.string.trusted_people), stringResource(R.string.trusted_people_description))
    if (people.isEmpty()) { Spacer(Modifier.height(28.dp)); TrustIllustration(); Text(stringResource(R.string.trusted_people_empty_title), color = TextPrimary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally)); Text(stringResource(R.string.trusted_people_empty_description), color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally)); PremiumSurface(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { PremiumIcon(PremiumIconKind.Info, stringResource(R.string.trusted_people_privacy_note)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.trusted_people_privacy_note), color = TextSecondary, style = MaterialTheme.typography.bodyMedium) } } } else people.forEach { person -> Text(person, color = TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 14.dp)) }
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
  } }
}

@Composable private fun TrustIllustration() { Canvas(Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = "Güvenilir kişiler sembolü" }) { val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f); drawOval(PrimaryTurquoise, androidx.compose.ui.geometry.Offset(center.x - 86.dp.toPx(), center.y - 20.dp.toPx()), androidx.compose.ui.geometry.Size(172.dp.toPx(), 74.dp.toPx()), style = Stroke(1.5.dp.toPx())); drawCircle(PrimaryTurquoise, 17.dp.toPx(), center.copy(x = center.x - 25.dp.toPx(), y = center.y - 22.dp.toPx())); drawCircle(PrimaryTurquoise, 14.dp.toPx(), center.copy(x = center.x + 25.dp.toPx(), y = center.y - 16.dp.toPx())); drawArc(PrimaryTurquoise, 200f, 140f, false, androidx.compose.ui.geometry.Offset(center.x - 53.dp.toPx(), center.y - 5.dp.toPx()), androidx.compose.ui.geometry.Size(56.dp.toPx(), 52.dp.toPx()), style = Stroke(2.dp.toPx())); drawArc(PrimaryTurquoise, 200f, 140f, false, androidx.compose.ui.geometry.Offset(center.x - 2.dp.toPx(), center.y + 1.dp.toPx()), androidx.compose.ui.geometry.Size(50.dp.toPx(), 46.dp.toPx()), style = Stroke(2.dp.toPx())); drawCircle(Navy950, 28.dp.toPx(), center.copy(x = center.x + 72.dp.toPx(), y = center.y + 20.dp.toPx())); drawCircle(PrimaryTurquoise, 23.dp.toPx(), center.copy(x = center.x + 72.dp.toPx(), y = center.y + 20.dp.toPx()), style = Stroke(2.dp.toPx())) } }

@Composable fun SettingsScreen(onNavigate: (NavKey) -> Unit, onBack: () -> Unit, selectedLanguage: AppLanguage, onLanguageSelected: (AppLanguage) -> Unit, modifier: Modifier = Modifier) {
  Scaffold(modifier, containerColor = Navy950, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PremiumBottomNavigation(Settings, onNavigate) }) { padding -> Page(Modifier.padding(padding)) {
    SectionHeader(stringResource(R.string.settings_title), stringResource(R.string.settings_description))
    SettingsGroup(stringResource(R.string.appearance_section)) { PremiumInfoRow(PremiumIconKind.Theme, stringResource(R.string.theme_setting), stringResource(R.string.theme_detail), stringResource(R.string.theme_setting)) }
    SettingsGroup(stringResource(R.string.language_section)) { PremiumInfoRow(PremiumIconKind.Language, stringResource(R.string.language_setting), languageSummary(selectedLanguage), stringResource(R.string.language_setting), trailing = { LanguageChoice(selectedLanguage, onLanguageSelected) }) }
    SettingsGroup(stringResource(R.string.privacy_section)) { PremiumInfoRow(PremiumIconKind.Location, stringResource(R.string.location_sharing), stringResource(R.string.location_detail), stringResource(R.string.location_sharing)); PremiumInfoRow(PremiumIconKind.Backup, stringResource(R.string.backup_setting), stringResource(R.string.backup_detail), stringResource(R.string.backup_setting)) }
    SettingsGroup(stringResource(R.string.security_section)) { PremiumInfoRow(PremiumIconKind.Device, stringResource(R.string.device_verification), stringResource(R.string.device_verification_detail), stringResource(R.string.device_verification)) }
    SettingsGroup(stringResource(R.string.about_section)) { PremiumInfoRow(PremiumIconKind.Info, stringResource(R.string.prototype_version), stringResource(R.string.prototype_version_detail), stringResource(R.string.prototype_version)) }
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
  } }
}

@Composable private fun SettingsGroup(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, color = TextSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp)); content() } }
@Composable private fun languageSummary(language: AppLanguage): String = stringResource(if (language == AppLanguage.TURKISH) R.string.language_turkish else R.string.language_english)
@Composable
private fun LanguageChoice(selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
  TextButton(onClick = { onSelected(if (selected == AppLanguage.TURKISH) AppLanguage.ENGLISH else AppLanguage.TURKISH) }) {
    Text(stringResource(if (selected == AppLanguage.TURKISH) R.string.language_turkish else R.string.language_english), color = PrimaryTurquoise, style = MaterialTheme.typography.labelMedium)
  }
}
@Composable private fun BackAction(onBack: () -> Unit) { TextButton(onClick = onBack) { Text(stringResource(R.string.back_action), color = PrimaryTurquoise) } }
@Composable private fun PremiumBottomNavigation(current: NavKey, onNavigate: (NavKey) -> Unit) { Row(Modifier.fillMaxWidth().background(Color(0xF20B1220)).padding(horizontal = 16.dp, vertical = 8.dp).windowInsetsBottomHeight(WindowInsets.navigationBars), horizontalArrangement = Arrangement.SpaceEvenly) { NavigationItem(Main, PremiumIconKind.Home, stringResource(R.string.navigation_home), current == Main, onNavigate); NavigationItem(TrustedPeople, PremiumIconKind.People, stringResource(R.string.navigation_people), current == TrustedPeople, onNavigate); NavigationItem(Settings, PremiumIconKind.Settings, stringResource(R.string.navigation_settings), current == Settings, onNavigate) } }
@Composable private fun NavigationItem(key: NavKey, icon: PremiumIconKind, label: String, selected: Boolean, onNavigate: (NavKey) -> Unit) { Column(horizontalAlignment = Alignment.CenterHorizontally) { TextButton(onClick = { onNavigate(key) }, modifier = Modifier.semantics { contentDescription = label }) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) { PremiumIcon(icon, label, if (selected) PrimaryTurquoise else TextMuted); Text(label, color = if (selected) PrimaryTurquoise else TextSecondary, style = MaterialTheme.typography.labelSmall) } } } }
