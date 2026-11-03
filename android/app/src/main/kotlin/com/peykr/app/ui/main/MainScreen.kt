package com.peykr.app.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.peykr.app.ui.theme.DarkPanel
import com.peykr.app.ui.theme.DividerDark
import com.peykr.app.ui.theme.GlassCard
import com.peykr.app.ui.theme.IconContainer
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.Purple
import com.peykr.app.ui.theme.SectionHeader
import com.peykr.app.ui.theme.SettingsRow
import com.peykr.app.ui.theme.TextMuted
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary
import com.peykr.app.ui.theme.Violet

@Composable
fun MainScreen(onNavigate: (NavKey) -> Unit, onPeopleClick: () -> Unit, onSettingsClick: () -> Unit, modifier: Modifier = Modifier, viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) }) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val count = (uiState as? MainScreenUiState.Success)?.data?.size ?: 0
  Scaffold(modifier, containerColor = Navy950, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PEYKRBottomNavigation(Main, onNavigate) }) { padding ->
    AppBackground(Modifier.padding(padding)) { HomeContent(count, onPeopleClick, onSettingsClick) }
  }
}

@Composable
private fun HomeContent(count: Int, onPeopleClick: () -> Unit, onSettingsClick: () -> Unit) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = AppHorizontalPadding).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    BrandHeader()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("PEYKR prototipi", style = MaterialTheme.typography.labelLarge, color = PrimaryTurquoise)
      Text("Güvenlik özellikleri henüz etkin değil.", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
      Text("Güvendiğiniz kişiler ve paylaşım tercihleri için sade bir kontrol alanı.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
    PrototypeStatus()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      MetricItem("$count", "Güvenilir kişi", "Yerel kayıt", onPeopleClick, Modifier.weight(1f))
      MetricItem("—", "Konum paylaşımı", "Etkin değil", onSettingsClick, Modifier.weight(1f))
    }
    SectionHeader("Hızlı erişim", color = TextMuted)
    QuickAction("Kişileri yönet", "Kişi ekleme ve eşleştirme prototipte kullanılamıyor.", onPeopleClick)
    QuickAction("Ayarlar", "Tema ve uygulama tercihlerini inceleyin.", onSettingsClick)
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
  }
}

@Composable private fun BrandHeader() {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(PrimaryTurquoise, Violet))), contentAlignment = Alignment.Center) {
      Text("P", color = Navy950, fontWeight = FontWeight.Black)
    }
    Spacer(Modifier.width(11.dp)); Text("PEYKR", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.weight(1f)); Text("PROTOTİP", color = TextMuted, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable private fun PrototypeStatus() {
  GlassCard(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      StatusGlyph(Modifier.size(44.dp))
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("Sistem durumu", color = TextSecondary, style = MaterialTheme.typography.bodySmall); Text("Hazırlanıyor", color = TextPrimary, style = MaterialTheme.typography.titleMedium) }
      Text("AKTİF DEĞİL", color = TextMuted, style = MaterialTheme.typography.labelSmall)
    }
  }
}

@Composable private fun StatusGlyph(modifier: Modifier = Modifier) {
  Canvas(modifier.semantics { contentDescription = "Etkin olmayan prototip durumu" }) { drawCircle(Color(0x2234D9FF)); drawArc(Brush.sweepGradient(listOf(PrimaryTurquoise, Color.Transparent)), 210f, 235f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)); drawCircle(PrimaryTurquoise, 3.dp.toPx()) }
}

@Composable private fun MetricItem(value: String, title: String, detail: String, onClick: () -> Unit, modifier: Modifier) {
  GlassCard(modifier, onClick) { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(value, color = TextPrimary, style = MaterialTheme.typography.headlineSmall); Text(title, color = TextSecondary, style = MaterialTheme.typography.bodySmall); Text(detail, color = PrimaryTurquoise, style = MaterialTheme.typography.labelSmall) } }
}

@Composable private fun QuickAction(title: String, detail: String, onClick: () -> Unit) {
  GlassCard(Modifier.fillMaxWidth(), onClick) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconContainer("•", title, Cyan); Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium); Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }; Text("ÖNİZLEME", color = TextMuted, style = MaterialTheme.typography.labelSmall) } }
}

@Composable
fun TrustedPeopleScreen(onNavigate: (NavKey) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) }) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle(); val people = (uiState as? MainScreenUiState.Success)?.data.orEmpty()
  Scaffold(modifier, containerColor = Navy950, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PEYKRBottomNavigation(TrustedPeople, onNavigate) }) { padding ->
    AppBackground(Modifier.padding(padding)) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = AppHorizontalPadding).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
      ScreenHeader("Güvenilir kişiler", "Bağlantılarınız bu prototipte yalnızca yerel olarak görüntülenir.", onBack)
      if (people.isEmpty()) {
        EmptyPeopleVisual()
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Henüz güvenilir kişi yok", color = TextPrimary, style = MaterialTheme.typography.titleLarge); Text("Eşleştirme ve davet akışı henüz kullanılabilir değil.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
        GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { IconContainer("i", "Prototip bilgisi", Violet); Spacer(Modifier.width(12.dp)); Text("Gerçek kişi veya iletişim bilgisi bu prototipte eklenmiyor.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) } }
      } else people.forEach { person -> GlassCard(Modifier.fillMaxWidth()) { Text(person, color = TextPrimary, style = MaterialTheme.typography.titleMedium) } }
      Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    } }
  }
}

@Composable private fun EmptyPeopleVisual() { Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(22.dp)).background(DarkPanel), contentAlignment = Alignment.Center) { Canvas(Modifier.size(130.dp)) { drawCircle(Color(0x1834D9FF)); drawCircle(PrimaryTurquoise, 25.dp.toPx(), style = Stroke(1.5.dp.toPx())); drawCircle(Cyan, 5.dp.toPx()); drawCircle(Violet, 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .72f, size.height * .3f)); drawLine(DividerDark, androidx.compose.ui.geometry.Offset(size.width * .25f, size.height * .68f), androidx.compose.ui.geometry.Offset(size.width * .75f, size.height * .32f), 1.dp.toPx()) } } }

@Composable fun SettingsScreen(onNavigate: (NavKey) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
  Scaffold(modifier, containerColor = Navy950, contentWindowInsets = WindowInsets.safeDrawing, bottomBar = { PEYKRBottomNavigation(Settings, onNavigate) }) { padding ->
    AppBackground(Modifier.padding(padding)) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = AppHorizontalPadding).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      ScreenHeader("Ayarlar", "PEYKR tercihleri ve gizlilik yaklaşımı.", onBack)
      SettingsGroup("Görünüm") { SettingsRow("◐", "Tema", "Koyu tema · Tasarım önceliği", Cyan) }
      SettingsGroup("Dil") { SettingsRow("文", "Uygulama dili", "Türkçe · Değiştirme henüz kullanılamıyor", Violet) }
      SettingsGroup("Gizlilik") { SettingsRow("⌖", "Konum paylaşımı", "Kapalı · Özellik henüz etkin değil", PrimaryTurquoise); SettingsRow("◌", "Veri ve yedekleme", "Bulut yedekleme ve hesap kurtarma yok", Purple) }
      SettingsGroup("Güvenlik") { SettingsRow("◇", "Cihaz doğrulama", "İşletim sistemi doğrulaması planlandı", Violet) }
      SettingsGroup("Hakkında") { SettingsRow("i", "Prototip sürüm", "PEYKR V.1 · Geliştirme aşamasında", Cyan) }
      Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    } }
  }
}

@Composable private fun SettingsGroup(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, color = TextMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp)); content() } }
@Composable private fun ScreenHeader(title: String, supportingText: String, onBack: () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { androidx.compose.material3.TextButton(onClick = onBack) { Text("Geri", color = PrimaryTurquoise) }; SectionHeader(title, supportingText) } }
@Composable private fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) { Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy950, Color(0xFF091322), Navy950)))) { Box(Modifier.align(Alignment.TopEnd).size(180.dp).background(Brush.radialGradient(listOf(Color(0x143C6E9E), Color.Transparent)))); content() } }

@Composable private fun PEYKRBottomNavigation(current: NavKey, onNavigate: (NavKey) -> Unit) { Row(Modifier.fillMaxWidth().background(Color(0xF20A1222)).padding(horizontal = 18.dp, vertical = 10.dp).windowInsetsBottomHeight(WindowInsets.navigationBars), horizontalArrangement = Arrangement.SpaceEvenly) { NavigationItem("⌂", "Ana sayfa", current == Main) { onNavigate(Main) }; NavigationItem("•", "Kişiler", current == TrustedPeople) { onNavigate(TrustedPeople) }; NavigationItem("⚙", "Ayarlar", current == Settings) { onNavigate(Settings) } } }
@Composable private fun NavigationItem(symbol: String, label: String, selected: Boolean, onClick: () -> Unit) { Column(Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) Color(0x1434D9FF) else Color.Transparent).semantics { contentDescription = label }.padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { androidx.compose.material3.TextButton(onClick = onClick) { Text(symbol, color = if (selected) PrimaryTurquoise else TextMuted, style = MaterialTheme.typography.titleMedium) }; Text(label, color = if (selected) TextPrimary else TextMuted, style = MaterialTheme.typography.labelSmall) } }
