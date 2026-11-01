package com.example.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.ExaloteEmerald
import com.example.ui.theme.ExaloteGold

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveWebPortalScreen(
  onBackToNativeApp: () -> Unit
) {
  BackHandler(onBack = onBackToNativeApp)
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  var copiedNotice by remember { mutableStateOf<String?>(null) }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top Control Bar for Switching Between Website Mode & Native App + 1-Click HTML Export
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onBackToNativeApp,
            modifier = Modifier.testTag("back_to_native_app_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to Native App",
              tint = Color.White
            )
          }
          Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = "Web Mode",
            tint = ExaloteEmerald,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Column {
            Text(
              text = "LIVE WEBSITE MODE (NO LOGIN NEEDED TO TEST)",
              style = MaterialTheme.typography.labelLarge,
              color = ExaloteEmerald
            )
            Text(
              text = copiedNotice ?: "Also saved at /web/index.html in project files",
              style = MaterialTheme.typography.labelSmall,
              color = ExaloteGold
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = {
              runCatching {
                val html = context.assets.open("exalote_web.html").bufferedReader().use { it.readText() }
                clipboardManager.setText(AnnotatedString(html))
                copiedNotice = "Copied full index.html to clipboard!"
              }
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("copy_web_html_button")
          ) {
            Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy HTML", modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy Website HTML", style = MaterialTheme.typography.labelSmall)
          }

          Button(
            onClick = onBackToNativeApp,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ExaloteGold, contentColor = Color.Black)
          ) {
            Text("Native App", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
          }
        }
      }

      // Embedded Interactive Website
      AndroidView(
        modifier = Modifier
          .fillMaxSize()
          .weight(1f),
        factory = { ctx ->
          WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              databaseEnabled = true
              allowFileAccess = true
              allowContentAccess = true
              cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl("file:///android_asset/exalote_web.html")
          }
        }
      )
    }
  }
}
