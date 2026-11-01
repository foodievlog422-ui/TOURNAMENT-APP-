package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.MatchClaim
import com.example.data.model.MatchRegistration
import com.example.data.model.TeammateInput
import com.example.data.model.TournamentMatch
import com.example.data.model.UserProfile
import com.example.data.model.calculateEntryFeeBreakdown
import com.example.data.model.isRoomCredentialsVisible
import com.example.data.model.requiredTeammatesForFormat
import com.example.ui.theme.ExaloteCyan
import com.example.ui.theme.ExaloteEmerald
import com.example.ui.theme.ExaloteGold
import com.example.ui.theme.ExaloteOrange
import java.io.ByteArrayOutputStream

@Composable
fun MatchesLobbyScreen(
  matches: List<TournamentMatch>,
  registrations: List<MatchRegistration>,
  claims: List<MatchClaim>,
  userProfile: UserProfile?,
  isAdmin: Boolean,
  onJoinMatch: (String, List<TeammateInput>) -> Unit,
  onSubmitClaim: (
    matchId: String,
    matchTitle: String,
    kills: Int,
    rank: Int,
    screenshotBase64: String,
    screenshotMime: String,
    suspectIgn: String,
    reason: String
  ) -> Unit,
  onNavigateToProfileBinding: () -> Unit,
  onSeedMatchesIfEmpty: () -> Unit
) {
  var selectedFormatFilter by remember { mutableStateOf("All") }
  var matchToJoin by remember { mutableStateOf<TournamentMatch?>(null) }
  var matchToClaim by remember { mutableStateOf<TournamentMatch?>(null) }
  val clipboardManager = LocalClipboardManager.current
  val currentTimeMs = System.currentTimeMillis()

  val filteredMatches = remember(matches, selectedFormatFilter) {
    if (selectedFormatFilter == "All") {
      matches
    } else {
      matches.filter { it.format.equals(selectedFormatFilter, ignoreCase = true) }
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // Format Filter Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Filled.FilterList,
        contentDescription = "Filter formats",
        tint = ExaloteGold,
        modifier = Modifier.size(18.dp)
      )
      listOf("All", "Solo", "1v1", "2v2", "Duo", "Squad", "4v4").forEach { fmt ->
        FilterChip(
          selected = selectedFormatFilter == fmt,
          onClick = { selectedFormatFilter = fmt },
          label = { Text(fmt) },
          modifier = Modifier.testTag("filter_chip_$fmt")
        )
      }
    }

    // Binding Warning Banner if user has not bound FF UID/IGN yet
    if (userProfile != null && !userProfile.hasValidFreeFireBinding) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
          containerColor = ExaloteOrange.copy(alpha = 0.18f)
        ),
        shape = RoundedCornerShape(14.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Mandatory Free Fire MAX UID & IGN Unbound",
              style = MaterialTheme.typography.titleMedium,
              color = ExaloteOrange
            )
            Text(
              text = "Bind your 8–12 digit numeric UID in Profile before joining paid rooms.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onNavigateToProfileBinding,
            modifier = Modifier.testTag("banner_bind_uid_button"),
            colors = ButtonDefaults.buttonColors(containerColor = ExaloteOrange)
          ) {
            Text("Bind Now")
          }
        }
      }
    }

    if (filteredMatches.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = "No matches",
            tint = ExaloteGold,
            modifier = Modifier.size(56.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "No Active Tournaments in This Filter",
            style = MaterialTheme.typography.titleLarge
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = if (isAdmin) {
              "Click below to seed 5 official EXALOTE EXN tournaments (Solo, 1v1, 2v2, 4v4) live!"
            } else {
              "New tournaments created by the Admin appear here instantaneously in real time."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (isAdmin) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              onClick = onSeedMatchesIfEmpty,
              modifier = Modifier.testTag("seed_matches_button"),
              colors = ButtonDefaults.buttonColors(containerColor = ExaloteOrange)
            ) {
              Text("Seed 5 Official EXALOTE EXN Matches")
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        items(filteredMatches, key = { it.id }) { match ->
          val myReg = registrations.find { it.matchId == match.id }
          val myClaim = claims.find { it.matchId == match.id }
          val roomVisible = isRoomCredentialsVisible(match, myReg, currentTimeMs)

          MatchItemCard(
            match = match,
            registration = myReg,
            claim = myClaim,
            roomVisible = roomVisible,
            onCopyText = { label, value ->
              clipboardManager.setText(AnnotatedString(value))
            },
            onJoinClick = { matchToJoin = match },
            onClaimClick = { matchToClaim = match }
          )
        }
      }
    }
  }

  // Atomic Join Dialog with 3-Bucket Fee Breakdown & Teammate UID/IGN Validation
  val activeJoinMatch = matchToJoin
  if (activeJoinMatch != null) {
    JoinMatchDialog(
      match = activeJoinMatch,
      userProfile = userProfile,
      onDismiss = { matchToJoin = null },
      onNavigateToProfileBinding = {
        matchToJoin = null
        onNavigateToProfileBinding()
      },
      onConfirmJoin = { teammates ->
        onJoinMatch(activeJoinMatch.id, teammates)
        matchToJoin = null
      }
    )
  }

  // Result Claim, Screenshot Picker & Hacker Report Dialog
  val activeClaimMatch = matchToClaim
  if (activeClaimMatch != null) {
    SubmitResultClaimDialog(
      match = activeClaimMatch,
      existingClaim = claims.find { it.matchId == activeClaimMatch.id },
      onDismiss = { matchToClaim = null },
      onSubmit = { kills, rank, base64, mime, suspectIgn, reason ->
        onSubmitClaim(
          activeClaimMatch.id,
          activeClaimMatch.title,
          kills,
          rank,
          base64,
          mime,
          suspectIgn,
          reason
        )
        matchToClaim = null
      }
    )
  }
}

@Composable
private fun MatchItemCard(
  match: TournamentMatch,
  registration: MatchRegistration?,
  claim: MatchClaim?,
  roomVisible: Boolean,
  onCopyText: (String, String) -> Unit,
  onJoinClick: () -> Unit,
  onClaimClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = if (registration != null) ExaloteEmerald.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.outline,
        shape = RoundedCornerShape(18.dp)
      )
      .testTag("match_card_${match.id}"),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          BadgePill(text = match.format.uppercase(), color = ExaloteOrange)
          BadgePill(text = match.mapName, color = ExaloteCyan)
          BadgePill(text = match.session.uppercase(), color = ExaloteGold)
        }
        BadgePill(
          text = match.status,
          color = when (match.status) {
            "Live" -> ExaloteEmerald
            "Completed" -> Color(0xFF64748B)
            else -> ExaloteGold
          }
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = match.title,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White
      )
      Text(
        text = "${match.categoryName} • ${match.matchDate} at ${match.startTime}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Prize & Rate Breakdown Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
          .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        StatColumn(label = "ENTRY FEE", value = "₹${match.entryFee.toInt()}", color = ExaloteOrange)
        StatColumn(label = "PRIZE POOL", value = "₹${match.prizePool.toInt()}", color = ExaloteGold)
        StatColumn(label = "PER KILL", value = "₹${match.perKillRate.toInt()}", color = ExaloteEmerald)
        StatColumn(label = "RANK #1", value = "₹${match.rank1Prize.toInt()}", color = ExaloteCyan)
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Slot Progress Bar
      val progress = (match.joinedCount.toFloat() / match.maxPlayers.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Slots Occupied: ${match.joinedCount} / ${match.maxPlayers}",
          style = MaterialTheme.typography.labelLarge
        )
        Text(
          text = "${match.maxPlayers - match.joinedCount} Left",
          style = MaterialTheme.typography.labelSmall,
          color = ExaloteGold
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp)),
        color = ExaloteOrange,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
      )

      // Joined Status & Custom Room ID / Password Section
      if (registration != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (roomVisible) ExaloteEmerald.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceVariant
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "REGISTERED • SLOT #${registration.slotNumber} (${registration.status})",
                style = MaterialTheme.typography.labelLarge,
                color = ExaloteEmerald
              )
              Text(
                text = "UID: ${registration.ffUid}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            if (registration.teamMembers.isNotEmpty()) {
              Text(
                text = "Squad Roster: ${registration.teamMembers.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (roomVisible) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Filled.Key,
                      contentDescription = "Room Credentials",
                      tint = ExaloteEmerald,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "ROOM ID: ${match.roomId}",
                      style = MaterialTheme.typography.titleMedium,
                      color = Color.White
                    )
                  }
                  Text(
                    text = "PASSWORD: ${match.roomPassword.ifBlank { "None" }}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ExaloteGold
                  )
                }
                Row {
                  IconButton(
                    onClick = { onCopyText("Room ID", match.roomId) },
                    modifier = Modifier.testTag("copy_room_id_${match.id}")
                  ) {
                    Icon(
                      imageVector = Icons.Filled.ContentCopy,
                      contentDescription = "Copy Room ID",
                      tint = ExaloteEmerald
                    )
                  }
                }
              }
            } else {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.Lock,
                  contentDescription = "Room Locked",
                  tint = ExaloteGold,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Room ID & Password hidden — auto-reveals live 15m before start or when Admin publishes!",
                  style = MaterialTheme.typography.bodyMedium,
                  color = ExaloteGold
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (registration == null) {
          val canJoin = match.status == "Upcoming" && match.joinedCount < match.maxPlayers
          Button(
            onClick = onJoinClick,
            enabled = canJoin,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("join_match_button_${match.id}"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ExaloteOrange)
          ) {
            Text(
              text = when {
                match.joinedCount >= match.maxPlayers -> "MATCH FULL"
                match.status != "Upcoming" -> match.status.uppercase()
                else -> "JOIN MATCH • ₹${match.entryFee.toInt()}"
              },
              fontWeight = FontWeight.Bold
            )
          }
        } else {
          OutlinedButton(
            onClick = onClaimClick,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("claim_result_button_${match.id}"),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.PhotoCamera,
              contentDescription = "Submit Result Claim",
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (claim == null) {
                "Submit Result Screenshot / Hacker Report"
              } else {
                "Claim Status: ${claim.status.uppercase()} (₹${claim.awardedAmount.toInt()})"
              }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun JoinMatchDialog(
  match: TournamentMatch,
  userProfile: UserProfile?,
  onDismiss: () -> Unit,
  onNavigateToProfileBinding: () -> Unit,
  onConfirmJoin: (List<TeammateInput>) -> Unit
) {
  val requiredMates = requiredTeammatesForFormat(match.format)
  val teammates = remember(requiredMates) {
    mutableStateListOf<TeammateInput>().apply {
      repeat(requiredMates) { add(TeammateInput()) }
    }
  }
  val breakdown = remember(match.entryFee, userProfile) {
    calculateEntryFeeBreakdown(
      entryFee = match.entryFee,
      bonusBalance = userProfile?.bonusBalance ?: 0.0,
      depositBalance = userProfile?.depositBalance ?: 0.0,
      winningsBalance = userProfile?.winningsBalance ?: 0.0
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Join ${match.title} (${match.format})",
        style = MaterialTheme.typography.titleLarge
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (userProfile == null || !userProfile.hasValidFreeFireBinding) {
          Text(
            text = "You must bind an 8–12 digit Free Fire MAX UID & IGN in your Profile before joining any tournament.",
            color = MaterialTheme.colorScheme.error
          )
        } else {
          Text(
            text = "Leader: ${userProfile.ffIgn} (UID: ${userProfile.ffUid})",
            style = MaterialTheme.typography.labelLarge,
            color = ExaloteEmerald
          )
        }

        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "Atomic 3-Bucket Fee Deduction Order",
              style = MaterialTheme.typography.labelLarge,
              color = ExaloteGold
            )
            Text("1. Bonus Bucket (Max 10%): -₹${breakdown.bonusUsed}")
            Text("2. Deposit Bucket: -₹${breakdown.depositUsed}")
            Text("3. Winnings Bucket: -₹${breakdown.winningsUsed}")
            Text(
              text = if (breakdown.canAfford) {
                "Total Entry Fee: ₹${breakdown.totalFee} (Ready)"
              } else {
                "Shortfall: ₹${breakdown.shortfall} — Please deposit funds first."
              },
              color = if (breakdown.canAfford) ExaloteEmerald else MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Bold
            )
          }
        }

        if (requiredMates > 0) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.GroupAdd,
              contentDescription = "Teammates",
              tint = ExaloteCyan,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Required Teammates ($requiredMates) — No Duplicate UIDs",
              style = MaterialTheme.typography.titleMedium,
              color = ExaloteCyan
            )
          }
          teammates.forEachIndexed { idx, mate ->
            OutlinedTextField(
              value = mate.ffUid,
              onValueChange = { teammates[idx] = mate.copy(ffUid = it.filter { c -> c.isDigit() }.take(12)) },
              label = { Text("Teammate #${idx + 1} FF UID (8-12 digits)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = mate.ffIgn,
              onValueChange = { teammates[idx] = mate.copy(ffIgn = it.take(40)) },
              label = { Text("Teammate #${idx + 1} FF IGN") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }
    },
    confirmButton = {
      if (userProfile == null || !userProfile.hasValidFreeFireBinding) {
        Button(onClick = onNavigateToProfileBinding) {
          Text("Go to Profile Binding")
        }
      } else {
        Button(
          onClick = { onConfirmJoin(teammates.toList()) },
          enabled = breakdown.canAfford,
          colors = ButtonDefaults.buttonColors(containerColor = ExaloteOrange),
          modifier = Modifier.testTag("confirm_join_match_button")
        ) {
          Text("Pay ₹${breakdown.totalFee} & Join")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
private fun SubmitResultClaimDialog(
  match: TournamentMatch,
  existingClaim: MatchClaim?,
  onDismiss: () -> Unit,
  onSubmit: (
    kills: Int,
    rank: Int,
    screenshotBase64: String,
    screenshotMime: String,
    suspectIgn: String,
    reason: String
  ) -> Unit
) {
  val context = LocalContext.current
  var killsText by remember { mutableStateOf((existingClaim?.claimedKills ?: 0).toString()) }
  var rankText by remember { mutableStateOf((existingClaim?.claimedRank ?: 1).toString()) }
  var screenshotBase64 by remember { mutableStateOf(existingClaim?.screenshotBase64 ?: "") }
  var screenshotMime by remember { mutableStateOf(existingClaim?.screenshotMime ?: "image/jpeg") }
  var suspectIgn by remember { mutableStateOf(existingClaim?.reportSuspectIgn ?: "") }
  var reportReason by remember { mutableStateOf(existingClaim?.reportReason ?: "") }

  // Zero-permission Android Photo Picker (ActivityResultContracts.PickVisualMedia)
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
          val originalBitmap = BitmapFactory.decodeStream(stream)
          if (originalBitmap != null) {
            val maxDim = 480
            val ratio = minOf(
              maxDim.toFloat() / originalBitmap.width.coerceAtLeast(1),
              maxDim.toFloat() / originalBitmap.height.coerceAtLeast(1),
              1f
            )
            val scaled = android.graphics.Bitmap.createScaledBitmap(
              originalBitmap,
              (originalBitmap.width * ratio).toInt().coerceAtLeast(1),
              (originalBitmap.height * ratio).toInt().coerceAtLeast(1),
              true
            )
            val bos = ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, bos)
            screenshotBase64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            screenshotMime = "image/jpeg"
          }
        }
      } catch (_: Exception) {
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text("Result Claim & Hacker Report • ${match.title}", style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = killsText,
            onValueChange = { killsText = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text("Claimed Kills") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = rankText,
            onValueChange = { rankText = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text("Claimed Rank (#)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
        }

        OutlinedButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = "Upload Screenshot")
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            if (screenshotBase64.isBlank()) "Select Result Screenshot (JPG/PNG/WEBP)"
            else "Change Result Screenshot (Attached)"
          )
        }

        if (screenshotBase64.isNotBlank()) {
          val decodedBitmap = remember(screenshotBase64) {
            try {
              val bytes = Base64.decode(screenshotBase64, Base64.DEFAULT)
              BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
              null
            }
          }
          if (decodedBitmap != null) {
            Image(
              bitmap = decodedBitmap.asImageBitmap(),
              contentDescription = "Result Screenshot Preview",
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(10.dp)),
              contentScale = ContentScale.Crop
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Filled.ReportProblem,
            contentDescription = "Hacker Report",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Optional Hacker / Fair-Play Report",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
          )
        }

        OutlinedTextField(
          value = suspectIgn,
          onValueChange = { suspectIgn = it.take(80) },
          label = { Text("Suspect Free Fire IGN (Optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = reportReason,
          onValueChange = { reportReason = it.take(400) },
          label = { Text("Hack / Cheat Evidence Details (Optional)") },
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val k = killsText.toIntOrNull() ?: 0
          val r = (rankText.toIntOrNull() ?: 1).coerceIn(1, 100)
          onSubmit(k, r, screenshotBase64, screenshotMime, suspectIgn, reportReason)
        },
        colors = ButtonDefaults.buttonColors(containerColor = ExaloteOrange),
        modifier = Modifier.testTag("submit_claim_confirm_button")
      ) {
        Text("Submit Claim")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun BadgePill(text: String, color: Color) {
  Surface(
    color = color.copy(alpha = 0.16f),
    shape = RoundedCornerShape(6.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = color,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
  }
}

@Composable
private fun StatColumn(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      color = color,
      fontWeight = FontWeight.Bold
    )
  }
}
