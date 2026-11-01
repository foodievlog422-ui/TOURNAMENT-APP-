package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.theme.ExaloteEmerald
import com.example.ui.theme.ExaloteGold
import com.example.ui.theme.ExaloteOrange

@Composable
fun AuthScreen(
  onOpenDbConfigModal: () -> Unit,
  onLaunchWebPortal: () -> Unit = {}
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {},
      onUnauthenticated = {},
      scope = scope
    )
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xFF1A1025),
              MaterialTheme.colorScheme.background,
              Color(0xFF0A1426)
            )
          )
        )
        .windowInsetsPadding(WindowInsets.safeDrawing),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 520.dp)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(listOf(ExaloteOrange, ExaloteGold))
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = "Exalote Esports Trophy",
            tint = Color.White,
            modifier = Modifier.size(46.dp)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "EXALOTE EXN",
          style = MaterialTheme.typography.displayLarge,
          color = Color.White
        )
        Text(
          text = "FREE FIRE MAX PAID TOURNAMENT ARENA",
          style = MaterialTheme.typography.labelLarge,
          color = ExaloteGold,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            FeatureHighlightRow(
              icon = Icons.Filled.VerifiedUser,
              tint = ExaloteOrange,
              title = "Mandatory FF UID & IGN Binding",
              subtitle = "8–12 digit Free Fire MAX UID verification & anti-duplicate slot protection"
            )
            FeatureHighlightRow(
              icon = Icons.Filled.AccountBalanceWallet,
              tint = ExaloteGold,
              title = "Atomic 3-Bucket Wallet System",
              subtitle = "10% Bonus -> Deposit -> Winnings deduction order & live UPI UTR queue"
            )
            FeatureHighlightRow(
              icon = Icons.Filled.LockOpen,
              tint = ExaloteEmerald,
              title = "Real-Time Custom Room Reveal",
              subtitle = "Instant Room ID & Password unlock for joined players + 1-click Admin payouts"
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
          ) {
            Text(
              text = errorMessage ?: "",
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(12.dp)
            )
          }
        }

        Button(
          onClick = {
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = { isLoading = false },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              scope = scope,
              onAuthCancelled = { isLoading = false }
            )
          },
          enabled = !isLoading,
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("google_sign_in_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = ExaloteOrange,
            contentColor = Color.White
          )
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = Color.White,
              strokeWidth = 2.dp
            )
          } else {
            Text(
              text = "Sign in with Google",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
          onClick = onLaunchWebPortal,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("launch_web_portal_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = ExaloteEmerald,
            contentColor = Color.White
          )
        ) {
          Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = "Launch Website Mode",
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Launch Website Mode (Instant Browser Test)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
          onClick = onOpenDbConfigModal,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("open_db_config_button"),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Security,
            contentDescription = "Database Config",
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Database & Admin Config / SQL & Rules")
        }
      }
    }
  }
}

@Composable
private fun FeatureHighlightRow(
  icon: ImageVector,
  tint: Color,
  title: String,
  subtitle: String
) {
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(tint.copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = tint,
        modifier = Modifier.size(22.dp)
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
