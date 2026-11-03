package com.peykr.app.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.peykr.app.ui.theme.EmptyState
import com.peykr.app.ui.theme.GlassCard
import com.peykr.app.ui.theme.GlassShape
import com.peykr.app.ui.theme.IconContainer
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.Navy900
import com.peykr.app.ui.theme.PrimaryActionButton
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.Purple
import com.peykr.app.ui.theme.SectionHeader
import com.peykr.app.ui.theme.SettingsRow
import com.peykr.app.ui.theme.StatusIndicator
import com.peykr.app.ui.theme.TextMuted
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary
import com.peykr.app.ui.theme.Violet
import com.peykr.app.ui.theme.PEYKRTheme

@Composable
fun MainScreen(
  onNavigate: (NavKey) -> Unit,
  onPeopleClick: () -> Unit,
  onSettingsClick: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val trustedPeople = (uiState as? MainScreenUiState.Success)?.data.orEmpty()
  Scaffold(
    modifier = modifier,
    containerColor = Navy950,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = {
      PEYKRBottomNavigation(current = Main, onNavigate = onNavigate)
    },
  ) { innerPadding ->
    HomeContent(
      trustedPeopleCount = trustedPeople.size,
      onPeopleClick = onPeopleClick,
      onSettingsClick = onSettingsClick,
      modifier = Modifier.padding(innerPadding),
    )
  }
}

@Composable
private fun HomeContent(
  trustedPeopleCount: Int,
  onPeopleClick: () -> Unit,
  onSettingsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AppBackground(modifier) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = AppHorizontalPadding)
        .padding(top = 20.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      PEYKRBrandHeader()
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Güvendesiniz.", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        Text(
          "Bağlantılarınızı yönetin, konum paylaşımını dilediğiniz gibi kontrol edin.",
          style = MaterialTheme.typography.bodyLarge,
          color = TextSecondary,
        )
      }
      SecurityStatusCard()
      SecurityIllustration()
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TrustedPeopleSummary(
          count = trustedPeopleCount,
          onClick = onPeopleClick,
          modifier = Modifier.weight(1f),
        )
        LocationStatusCard(
          onClick = onSettingsClick,
          modifier = Modifier.weight(1f),
        )
      }
      QuickActions(onPeopleClick = onPeopleClick, onSettingsClick = onSettingsClick)
      Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
  }
}

@Composable
private fun PEYKRBrandHeader() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Brush.linearGradient(listOf(PrimaryTurquoise, Violet))),
      contentAlignment = Alignment.Center,
    ) {
      Text("P", color = Navy950, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.width(11.dp))
    Text("PEYKR", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.weight(1f))
    StatusIndicator("Prototip", active = false)
  }
}

@Composable
private fun SecurityStatusCard() {
  GlassCard(modifier = Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(Color(0x1234D9FF)),
        contentAlignment = Alignment.Center,
      ) {
        Text("◌", color = Cyan, style = MaterialTheme.typography.headlineSmall)
      }
      Spacer(Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text("Sistem durumu", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text("Prototip", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
      }
      Column(horizontalAlignment = Alignment.End) {
        Text("HAZIRLANIYOR", color = Violet, style = MaterialTheme.typography.labelSmall)
        Text("Aktif değil", color = TextMuted, style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
private fun SecurityIllustration(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(164.dp)
      .clip(GlassShape)
      .background(
        Brush.radialGradient(
          listOf(Color(0x1B00E5D4), Color(0x0C273E62), Navy900),
        ),
      )
      .semantics { contentDescription = "Soyut, etkin olmayan güvenlik illüstrasyonu" },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(150.dp)) {
      val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
      drawCircle(Color(0x0F00E5D4), radius = 68.dp.toPx())
      drawArc(
        brush = Brush.sweepGradient(listOf(PrimaryTurquoise, Color.Transparent, Violet)),
        startAngle = 210f,
        sweepAngle = 250f,
        useCenter = false,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
      )
      drawCircle(Color(0xFF12223C), radius = 35.dp.toPx())
      drawCircle(Color(0xFF29435C), radius = 28.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
      drawLine(
        color = PrimaryTurquoise,
        start = center - androidx.compose.ui.geometry.Offset(0f, 3.dp.toPx()),
        end = center + androidx.compose.ui.geometry.Offset(0f, 14.dp.toPx()),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
      )
      drawLine(
        color = PrimaryTurquoise,
        start = center - androidx.compose.ui.geometry.Offset(0f, 3.dp.toPx()),
        end = center - androidx.compose.ui.geometry.Offset(0f, 14.dp.toPx()),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
      )
    }
  }
}

@Composable
private fun TrustedPeopleSummary(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
  GlassCard(modifier = modifier, onClick = onClick) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      IconContainer("◉", "Güvenilir kişiler", PrimaryTurquoise)
      Text("Güvenilir kişiler", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
      Text("$count kişi", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
    }
  }
}

@Composable
private fun LocationStatusCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
  GlassCard(modifier = modifier, onClick = onClick) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      IconContainer("⌖", "Konum paylaşımı", Cyan)
      Text("Konum paylaşımı", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
      Text("Kapalı", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
    }
  }
}

@Composable
private fun QuickActions(onPeopleClick: () -> Unit, onSettingsClick: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Hızlı erişim", color = TextMuted, style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      QuickAction("Kişileri yönet", "›", onPeopleClick, Modifier.weight(1f))
      QuickAction("Ayarlar", "›", onSettingsClick, Modifier.weight(1f))
    }
  }
}

@Composable
private fun QuickAction(label: String, symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(Color(0x6610182A))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    Text(symbol, color = PrimaryTurquoise, style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
fun TrustedPeopleScreen(
  onNavigate: (NavKey) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val people = (uiState as? MainScreenUiState.Success)?.data.orEmpty()
  Scaffold(
    modifier = modifier,
    containerColor = Navy950,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = { PEYKRBottomNavigation(current = TrustedPeople, onNavigate = onNavigate) },
  ) { innerPadding ->
    AppBackground(Modifier.padding(innerPadding)) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = AppHorizontalPadding)
          .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        ScreenHeader("Güvenilir Kişiler", "Güvendiğiniz kişileri ekleyin ve bağlantılarınızı yönetin.", onBack)
        if (people.isEmpty()) {
          EmptyState(
            title = "Henüz güvenilir kişi yok.",
            supportingText = "Güvenilir bir ilişki, iki cihaz sahibinin açık onayıyla kurulacaktır.",
            modifier = Modifier.padding(top = 28.dp),
          )
          GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconContainer("i", "Prototip bilgisi", Violet)
              Spacer(Modifier.width(12.dp))
              Text(
                "Kişi ekleme akışı prototipte henüz kullanılamıyor.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
          PrimaryActionButton(
            text = "+ Kişi Ekle · Yakında",
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          people.forEach { person ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
              Text(person, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
          }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
      }
    }
  }
}

@Composable
fun SettingsScreen(
  onNavigate: (NavKey) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    containerColor = Navy950,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = { PEYKRBottomNavigation(current = Settings, onNavigate = onNavigate) },
  ) { innerPadding ->
    AppBackground(Modifier.padding(innerPadding)) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = AppHorizontalPadding)
          .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        ScreenHeader("Ayarlar", "PEYKR deneyiminizi ve izinlerinizi yönetin.", onBack)
        SettingsSection("Görünüm") {
          SettingsRow("◐", "Tema", "Koyu tema · Tasarım önceliği", Cyan)
        }
        SettingsSection("Dil") {
          SettingsRow("文", "Uygulama dili", "Türkçe · Değiştirme henüz kullanılamıyor", Violet)
        }
        SettingsSection("Gizlilik") {
          SettingsRow("⌖", "Konum paylaşımı", "Kapalı · Kişi başına ayrı izin gerekir", PrimaryTurquoise)
          SettingsRow("◌", "Veri ve yedekleme", "Bulut yedekleme ve hesap kurtarma yok", Purple)
        }
        SettingsSection("Güvenlik") {
          SettingsRow("◇", "Cihaz doğrulama", "İşletim sistemi kimlik doğrulaması planlandı", Violet)
        }
        SettingsSection("PEYKR hakkında") {
          SettingsRow("i", "Prototip sürüm", "PEYKR V.1 · Özellikler geliştirme aşamasında", Cyan)
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
      }
    }
  }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, color = TextMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
    content()
  }
}

@Composable
private fun ScreenHeader(title: String, supportingText: String, onBack: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text(
      "‹  Geri",
      color = PrimaryTurquoise,
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier
        .clickable(onClick = onBack)
        .semantics { contentDescription = "Geri" },
    )
    SectionHeader(title, supportingText)
  }
}

@Composable
private fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(listOf(Navy950, Color(0xFF0A1222), Navy950)),
      ),
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .size(180.dp)
        .background(Brush.radialGradient(listOf(Color(0x143C6E9E), Color.Transparent))),
    )
    content()
  }
}

@Composable
private fun PEYKRBottomNavigation(current: NavKey, onNavigate: (NavKey) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xF20A1222))
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .windowInsetsBottomHeight(WindowInsets.navigationBars),
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    NavigationItem("⌂", "Ana Sayfa", current == Main) { onNavigate(Main) }
    NavigationItem("◉", "Kişiler", current == TrustedPeople) { onNavigate(TrustedPeople) }
    NavigationItem("⚙", "Ayarlar", current == Settings) { onNavigate(Settings) }
  }
}

@Composable
private fun NavigationItem(symbol: String, label: String, selected: Boolean, onClick: () -> Unit) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 7.dp)
      .semantics { contentDescription = label },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Box(
      modifier = Modifier
        .size(if (selected) 5.dp else 0.dp)
        .background(PrimaryTurquoise, CircleShape),
    )
    Text(symbol, color = if (selected) PrimaryTurquoise else TextMuted, style = MaterialTheme.typography.titleMedium)
    Text(label, color = if (selected) TextPrimary else TextMuted, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
internal fun MainScreen(data: List<String>, modifier: Modifier = Modifier) {
  Column(modifier = modifier.padding(16.dp)) {
    data.forEach { Greeting(it) }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text("Hello $name!", modifier = modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
}

@Composable
fun MainScreenPreview() {
  PEYKRTheme {
    HomeContent(trustedPeopleCount = 0, onPeopleClick = {}, onSettingsClick = {})
  }
}
