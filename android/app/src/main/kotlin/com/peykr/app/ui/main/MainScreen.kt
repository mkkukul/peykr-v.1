package com.peykr.app.ui.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.peykr.app.ui.theme.GradientButton
import com.peykr.app.ui.theme.IconContainer
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.Navy900
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
    bottomBar = {
      BottomNavigationBar(current = Main, onNavigate = onNavigate)
    },
  ) { innerPadding ->
    HomeContent(
      trustedPeopleCount = trustedPeople.size,
      onPeopleClick = onPeopleClick,
      onAddPerson = onPeopleClick,
      onSettingsClick = onSettingsClick,
      modifier = Modifier.padding(innerPadding),
    )
  }
}

@Composable
private fun HomeContent(
  trustedPeopleCount: Int,
  onPeopleClick: () -> Unit,
  onAddPerson: () -> Unit,
  onSettingsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AppBackground(modifier = modifier) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = AppHorizontalPadding)
        .padding(top = 22.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      BrandHeader()
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Güvendesiniz.", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        Text(
          "Bağlantılarınızı yönetin, konum paylaşımını dilediğiniz gibi kontrol edin.",
          style = MaterialTheme.typography.bodyLarge,
          color = TextSecondary,
        )
      }
      SecurityIllustration()
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconContainer("◌", "Prototip durumu", Violet)
          Spacer(Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("Prototip sürüm", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text("Güvenlik özellikleri henüz etkin değil.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
          }
          Text("BETA", color = Violet, style = MaterialTheme.typography.labelLarge)
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(
          title = "Güvenilir Kişiler",
          value = trustedPeopleCount.toString(),
          symbol = "◉",
          tint = PrimaryTurquoise,
          modifier = Modifier.weight(1f),
          onClick = onPeopleClick,
        )
        InfoCard(
          title = "Konum Paylaşımı",
          value = "Kapalı",
          symbol = "⌖",
          tint = Cyan,
          modifier = Modifier.weight(1f),
          onClick = onSettingsClick,
        )
      }
      GradientButton(text = "Güvenilir Kişi Ekle", onClick = onAddPerson, modifier = Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun BrandHeader() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(13.dp))
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
private fun SecurityIllustration(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(190.dp)
      .clip(GlassShape)
      .background(Brush.radialGradient(listOf(Color(0x302B93B6), Color(0x100C1F3E), Navy900)))
      .semantics { contentDescription = "Soyut güvenlik ve güven illüstrasyonu" },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(170.dp)) {
      val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
      drawCircle(Color(0x1834D9FF), radius = 72.dp.toPx())
      drawCircle(Color(0x145D42C4), radius = 54.dp.toPx())
      drawArc(
        brush = Brush.sweepGradient(listOf(PrimaryTurquoise, Violet, PrimaryTurquoise)),
        startAngle = -45f,
        sweepAngle = 280f,
        useCenter = false,
        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
      )
      drawCircle(Color(0xFF112B50), radius = 36.dp.toPx())
      drawCircle(PrimaryTurquoise, radius = 30.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
      drawLine(PrimaryTurquoise, center - androidx.compose.ui.geometry.Offset(0f, 4.dp.toPx()), center + androidx.compose.ui.geometry.Offset(0f, 18.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
      drawLine(PrimaryTurquoise, center - androidx.compose.ui.geometry.Offset(0f, 4.dp.toPx()), center - androidx.compose.ui.geometry.Offset(0f, 18.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
  }
}

@Composable
private fun InfoCard(
  title: String,
  value: String,
  symbol: String,
  tint: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  GlassCard(modifier = modifier.animateContentSize(), onClick = onClick) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
      IconContainer(symbol, title, tint)
      Text(title, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
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
    bottomBar = { BottomNavigationBar(current = TrustedPeople, onNavigate = onNavigate) },
  ) { innerPadding ->
    AppBackground(modifier = Modifier.padding(innerPadding)) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = AppHorizontalPadding)
          .padding(top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
      ) {
        ScreenHeader("Güvenilir Kişiler", "Güvendiğiniz kişileri ekleyin ve bağlantılarınızı yönetin.", onBack)
        if (people.isEmpty()) {
          EmptyState(
            title = "Henüz güvenilir kişi yok.",
            supportingText = "Aileniz, yakınlarınız veya güvendiğiniz cihazları ekleyin.",
            modifier = Modifier.padding(top = 38.dp),
          )
          GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("Ekleme akışı prototip aşamasında.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
          }
        } else {
          people.forEach { person ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
              Text(person, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
          }
        }
        GradientButton(text = "+ Kişi Ekle", onClick = { }, modifier = Modifier.fillMaxWidth())
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
    bottomBar = { BottomNavigationBar(current = Settings, onNavigate = onNavigate) },
  ) { innerPadding ->
    AppBackground(modifier = Modifier.padding(innerPadding)) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = AppHorizontalPadding)
          .padding(top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ScreenHeader("Ayarlar", "PEYKR deneyiminizi ve izinlerinizi yönetin.", onBack)
        SettingsSectionTitle("Uygulama")
        SettingsRow("P", "Prototip Sürüm", "Özellikler geliştirme aşamasında.", Violet)
        SettingsRow("i", "Hakkında", "PEYKR V.1 · Gizlilik odaklı iletişim.", Cyan)
        SettingsSectionTitle("Bildirimler")
        SettingsRow("•", "Bildirimler", "Bildirim ayarları henüz uygulanmadı.", TextMuted)
        SettingsSectionTitle("Gizlilik")
        SettingsRow("⌖", "Konum Paylaşımı", "Kapalı · Her kişi için ayrı izin gerekir.", PrimaryTurquoise)
        SettingsRow("◌", "Gizlilik", "Hesap, kurtarma ve bulut yedekleme yok.", Purple)
      }
    }
  }
}

@Composable
private fun ScreenHeader(title: String, supportingText: String, onBack: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
private fun SettingsSectionTitle(title: String) {
  Text(
    title.uppercase(),
    color = TextMuted,
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
  )
}

@Composable
private fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          listOf(Navy950, Color(0xFF07152D), Navy950),
        ),
      ),
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .size(210.dp)
        .background(Brush.radialGradient(listOf(Purple.copy(alpha = 0.12f), Color.Transparent))),
    )
    content()
  }
}

@Composable
private fun BottomNavigationBar(
  current: NavKey,
  onNavigate: (NavKey) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 18.dp, vertical = 10.dp)
      .clip(RoundedCornerShape(24.dp))
      .background(Color(0xE6102342))
      .padding(8.dp),
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
      .clip(RoundedCornerShape(16.dp))
      .background(if (selected) Color(0x1F00E5D4) else Color.Transparent)
      .padding(horizontal = 18.dp, vertical = 8.dp)
      .clickable(onClick = onClick)
      .semantics { contentDescription = label },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(symbol, color = if (selected) PrimaryTurquoise else TextMuted, style = MaterialTheme.typography.titleMedium)
    Text(label, color = if (selected) TextPrimary else TextMuted, style = MaterialTheme.typography.labelLarge)
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
  Text(text = "Hello $name!", modifier = modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
}

@Composable
fun MainScreenPreview() {
  PEYKRTheme {
    HomeContent(
      trustedPeopleCount = 0,
      onPeopleClick = {},
      onAddPerson = {},
      onSettingsClick = {},
    )
  }
}
