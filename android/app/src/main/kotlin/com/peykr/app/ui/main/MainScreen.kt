package com.peykr.app.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.peykr.app.Main
import com.peykr.app.Settings
import com.peykr.app.TrustedPeople
import com.peykr.app.data.DefaultDataRepository
import com.peykr.app.ui.theme.AppHorizontalPadding
import com.peykr.app.ui.theme.Cyan
import com.peykr.app.ui.theme.DividerDark
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.SectionHeader
import com.peykr.app.ui.theme.TextMuted
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary

@Composable
fun MainScreen(onNavigate: (NavKey) -> Unit, onPeopleClick: () -> Unit, onSettingsClick: () -> Unit, modifier: Modifier = Modifier, viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) }) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val count = (uiState as? MainScreenUiState.Success)?.data?.size ?: 0
  Scaffold(modifier, containerColor = Navy950, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PEYKRBottomNavigation(Main, onNavigate) }) { padding ->
    MinimalPage(Modifier.padding(padding)) {
      BrandHeader()
      Text("PEYKR prototipi", color = PrimaryTurquoise, style = MaterialTheme.typography.labelLarge)
      FocusSymbol()
      Text("Güvenlik özellikleri henüz etkin değil.", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
      Text("Bu sürüm yalnızca temel arayüzü gösterir.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.height(12.dp))
      Text("Sistem durumu", color = TextMuted, style = MaterialTheme.typography.labelLarge)
      Text("Hazırlanıyor · Aktif değil", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(10.dp))
      HorizontalDivider(color = DividerDark)
      MinimalSummary("Güvenilir kişiler", "$count kişi", onPeopleClick)
      MinimalSummary("Konum paylaşımı", "Kapalı", onSettingsClick)
      Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
  }
}

@Composable private fun MinimalPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) { Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = AppHorizontalPadding).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }

@Composable private fun BrandHeader() { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("PEYKR", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("PROTOTİP", color = TextMuted, style = MaterialTheme.typography.labelSmall) } }

@Composable private fun FocusSymbol() { Canvas(Modifier.fillMaxWidth().height(76.dp).semantics { contentDescription = "PEYKR focus sembolü" }) { val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f); drawCircle(PrimaryTurquoise, 22.dp.toPx(), center, style = Stroke(1.5.dp.toPx())); drawCircle(PrimaryTurquoise, 4.dp.toPx(), center); drawLine(PrimaryTurquoise, center.copy(x = center.x - 34.dp.toPx()), center.copy(x = center.x - 23.dp.toPx()), 1.dp.toPx(), cap = StrokeCap.Round); drawLine(PrimaryTurquoise, center.copy(x = center.x + 23.dp.toPx()), center.copy(x = center.x + 34.dp.toPx()), 1.dp.toPx(), cap = StrokeCap.Round) } }

@Composable private fun MinimalSummary(title: String, value: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).semantics { contentDescription = title }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium); Text(value, color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }; Text("Görüntüle", color = PrimaryTurquoise, style = MaterialTheme.typography.labelMedium) } }

@Composable
fun TrustedPeopleScreen(onNavigate: (NavKey) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) }) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle(); val people = (uiState as? MainScreenUiState.Success)?.data.orEmpty()
  Scaffold(modifier, containerColor = Navy950, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PEYKRBottomNavigation(TrustedPeople, onNavigate) }) { padding ->
    MinimalPage(Modifier.padding(padding)) {
      BackAction(onBack); SectionHeader("Güvenilir kişiler", "Güvendiğiniz kişileri bu prototipte yalnızca yerel olarak görüntüleyin.")
      if (people.isEmpty()) {
        Spacer(Modifier.height(54.dp)); EmptyPeopleMark(); Text("Henüz güvenilir kişi yok", color = TextPrimary, style = MaterialTheme.typography.titleLarge); Text("Eşleştirme ve davet akışı henüz kullanılabilir değil.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(20.dp)); HorizontalDivider(color = DividerDark); Text("Gerçek kişi veya iletişim bilgisi eklenmiyor.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
      } else people.forEach { person -> Text(person, color = TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 14.dp)) }
      Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
  }
}

@Composable private fun EmptyPeopleMark() { Canvas(Modifier.size(48.dp).semantics { contentDescription = "Boş güvenilir kişiler durumu" }) { drawCircle(PrimaryTurquoise, 18.dp.toPx(), style = Stroke(1.5.dp.toPx())); drawCircle(PrimaryTurquoise, 3.dp.toPx()); drawLine(PrimaryTurquoise, androidx.compose.ui.geometry.Offset(size.width * .2f, size.height * .8f), androidx.compose.ui.geometry.Offset(size.width * .8f, size.height * .2f), 1.dp.toPx(), cap = StrokeCap.Round) } }

@Composable fun SettingsScreen(onNavigate: (NavKey) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
  Scaffold(modifier, containerColor = Navy950, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PEYKRBottomNavigation(Settings, onNavigate) }) { padding ->
    MinimalPage(Modifier.padding(padding)) {
      SectionHeader("Ayarlar", "PEYKR tercihleri ve gizlilik yaklaşımı.")
      SettingsGroup("Görünüm") { MinimalSetting("Tema", "Koyu tema · Tasarım önceliği", Cyan) }
      SettingsGroup("Dil") { MinimalSetting("Uygulama dili", "Türkçe · Değiştirme henüz kullanılamıyor", Cyan) }
      SettingsGroup("Gizlilik") { MinimalSetting("Konum paylaşımı", "Kapalı · Özellik henüz etkin değil", PrimaryTurquoise); MinimalSetting("Veri ve yedekleme", "Bulut yedekleme ve hesap kurtarma yok", Cyan) }
      SettingsGroup("Güvenlik") { MinimalSetting("Cihaz doğrulama", "İşletim sistemi doğrulaması planlandı", Cyan) }
      SettingsGroup("Hakkında") { MinimalSetting("Prototip sürüm", "PEYKR V.1 · Geliştirme aşamasında", Cyan) }
      Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
  }
}

@Composable private fun SettingsGroup(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, color = TextMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)); content() } }
@Composable private fun MinimalSetting(title: String, detail: String, tint: Color) { Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(tint)); Spacer(Modifier.size(14.dp)); Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium); Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun BackAction(onBack: () -> Unit) { TextButton(onClick = onBack) { Text("Geri", color = PrimaryTurquoise) } }

@Composable private fun PEYKRBottomNavigation(current: NavKey, onNavigate: (NavKey) -> Unit) { Row(Modifier.fillMaxWidth().background(Color(0xF20A1222)).padding(horizontal = 16.dp, vertical = 8.dp).windowInsetsBottomHeight(WindowInsets.navigationBars), horizontalArrangement = Arrangement.SpaceEvenly) { NavigationItem("Ana sayfa", current == Main) { onNavigate(Main) }; NavigationItem("Kişiler", current == TrustedPeople) { onNavigate(TrustedPeople) }; NavigationItem("Ayarlar", current == Settings) { onNavigate(Settings) } } }
@Composable private fun NavigationItem(label: String, selected: Boolean, onClick: () -> Unit) { TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) { Text(label, color = if (selected) PrimaryTurquoise else TextMuted, style = MaterialTheme.typography.labelMedium) } }
