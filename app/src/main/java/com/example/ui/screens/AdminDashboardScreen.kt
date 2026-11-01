package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DepositRequest
import com.example.data.model.EXALOTE_CATEGORIES
import com.example.data.model.MatchClaim
import com.example.data.model.TournamentMatch
import com.example.data.model.WithdrawalRequest
import com.example.data.model.calculatePrizeAmount
import com.example.ui.theme.ExaloteCyan
import com.example.ui.theme.ExaloteEmerald
import com.example.ui.theme.ExaloteGold
import com.example.ui.theme.ExaloteOrange

@Composable
fun AdminDashboardScreen(
  isAdmin: Boolean,
  matches: List<TournamentMatch>,
  claims: List<MatchClaim>,
  deposits: List<DepositRequest>,
  withdrawals: List<WithdrawalRequest>,
  onCreateMatch: (
    title: String,
    categoryCode: String,
    session: String,
    matchDate: String,
    startTime: String,
    startTimeEpochMs: Long,
    maxPlayers: Int,
    entryFee: Double,
    prizePool: Double,
    perKillRate: Double,
    rank1Prize: Double,
    rank2Prize: Double,
    rank3Prize: Double,
    mapName: String,
    matchRules: String
  ) -> Unit,
  onSeedMatches: () -> Unit,
  onPublishRoom: (matchId: String, roomId: String, roomPassword: String, roomPublished: Boolean, status: String) -> Unit,
  onVerifyClaimAndDistributePrize: (claimId: String, verifiedKills: Int, verifiedRank: Int, lockMatchPrizes: Boolean) -> Unit,
  onApproveOrRejectDeposit: (depositId: String, approve: Boolean) -> Unit,
  onApproveOrRejectWithdrawal: (withdrawalId: String, approve: Boolean) -> Unit,
  onRunAiAuditOnClaims: (String) -> Unit
) {
  if (!isAdmin) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = "Admin Only",
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(56.dp)
      )
      Spacer(modifier = Modifier.height(12.dp))
      Text("Restricted Admin Route", style = MaterialTheme.typography.headlineMedium)
      Text(
        "Match Creation, Room ID Publishing, Deposit/Withdrawal Approvals, and Result Payouts are strictly protected for Admin accounts.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    return
  }

  var selectedTab by remember { mutableIntStateOf(0) }

  Column(modifier = Modifier.fillMaxSize()) {
    TabRow(
      selectedTabIndex = selectedTab,
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text("Rooms (${matches.size})") },
        modifier = Modifier.testTag("admin_tab_rooms")
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("Results (${claims.count { it.status == "pending" }})") },
        modifier = Modifier.testTag("admin_tab_results")
      )
      Tab(
        selected = selectedTab == 2,
        onClick = { selectedTab = 2 },
        text = { Text("Deposits (${deposits.count { it.status == "Pending" }})") },
        modifier = Modifier.testTag("admin_tab_deposits")
      )
      Tab(
        selected = selectedTab == 3,
        onClick = { selectedTab = 3 },
        text = { Text("Payouts (${withdrawals.count { it.status == "Pending" }})") },
        modifier = Modifier.testTag("admin_tab_withdrawals")
      )
    }

    when (selectedTab) {
      0 -> AdminMatchesAndRoomsTab(
        matches = matches,
        onCreateMatch = onCreateMatch,
        onSeedMatches = onSeedMatches,
        onPublishRoom = onPublishRoom
      )
      1 -> AdminResultsVerificationTab(
        claims = claims,
        matches = matches,
        onVerifyClaimAndDistributePrize = onVerifyClaimAndDistributePrize,
        onRunAiAuditOnClaims = onRunAiAuditOnClaims
      )
      2 -> AdminDepositsQueueTab(
        deposits = deposits,
        onDecision = onApproveOrRejectDeposit
      )
      3 -> AdminWithdrawalsQueueTab(
        withdrawals = withdrawals,
        onDecision = onApproveOrRejectWithdrawal
      )
    }
  }
}

@Composable
private fun AdminMatchesAndRoomsTab(
  matches: List<TournamentMatch>,
  onCreateMatch: (
    title: String,
    categoryCode: String,
    session: String,
    matchDate: String,
    startTime: String,
    startTimeEpochMs: Long,
    maxPlayers: Int,
    entryFee: Double,
    prizePool: Double,
    perKillRate: Double,
    rank1Prize: Double,
    rank2Prize: Double,
    rank3Prize: Double,
    mapName: String,
    matchRules: String
  ) -> Unit,
  onSeedMatches: () -> Unit,
  onPublishRoom: (matchId: String, roomId: String, roomPassword: String, roomPublished: Boolean, status: String) -> Unit
) {
  var selectedCategory by remember { mutableStateOf(EXALOTE_CATEGORIES[2]) }
  var title by remember { mutableStateOf("EXN Bermuda Full Map Pro") }
  var session by remember { mutableStateOf("evening") }
  var matchDate by remember { mutableStateOf("2026-11-01") }
  var startTime by remember { mutableStateOf("18:00") }
  var maxPlayers by remember { mutableStateOf("48") }
  var entryFee by remember { mutableStateOf("25") }
  var prizePool by remember { mutableStateOf("800") }
  var perKillRate by remember { mutableStateOf("12") }
  var rank1Prize by remember { mutableStateOf("250") }
  var rank2Prize by remember { mutableStateOf("150") }
  var rank3Prize by remember { mutableStateOf("100") }
  var mapName by remember { mutableStateOf("Bermuda") }
  var matchRules by remember {
    mutableStateOf("Official EXALOTE EXN Fair Play Rules. No emulators, no scripts.")
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, ExaloteOrange.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Schedule New Tournament", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onSeedMatches) {
              Text("1-Click Seed 5 Matches")
            }
          }

          Text("Select EXALOTE EXN Category Format:", style = MaterialTheme.typography.labelLarge, color = ExaloteGold)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            EXALOTE_CATEGORIES.forEach { cat ->
              FilterChip(
                selected = selectedCategory.code == cat.code,
                onClick = {
                  selectedCategory = cat
                  title = "EXN ${cat.name} (${cat.format})"
                  maxPlayers = cat.defaultMaxPlayers.toString()
                },
                label = { Text("${cat.name} [${cat.format}]") }
              )
            }
          }

          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Tournament Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = mapName,
              onValueChange = { mapName = it },
              label = { Text("Map Name") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = maxPlayers,
              onValueChange = { maxPlayers = it.filter { c -> c.isDigit() }.take(3) },
              label = { Text("Max Slots") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = entryFee,
              onValueChange = { entryFee = it.filter { c -> c.isDigit() || c == '.' } },
              label = { Text("Entry Fee (₹)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = prizePool,
              onValueChange = { prizePool = it.filter { c -> c.isDigit() || c == '.' } },
              label = { Text("Prize Pool (₹)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = perKillRate,
              onValueChange = { perKillRate = it.filter { c -> c.isDigit() || c == '.' } },
              label = { Text("Per Kill (₹)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = rank1Prize,
              onValueChange = { rank1Prize = it.filter { c -> c.isDigit() || c == '.' } },
              label = { Text("Rank #1 (₹)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = rank2Prize,
              onValueChange = { rank2Prize = it.filter { c -> c.isDigit() || c == '.' } },
              label = { Text("Rank #2 (₹)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = rank3Prize,
              onValueChange = { rank3Prize = it.filter { c -> c.isDigit() || c == '.' } },
              label = { Text("Rank #3 (₹)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }

          Button(
            onClick = {
              onCreateMatch(
                title,
                selectedCategory.code,
                session,
                matchDate,
                startTime,
                System.currentTimeMillis() + 3600_000L,
                maxPlayers.toIntOrNull() ?: 48,
                entryFee.toDoubleOrNull() ?: 20.0,
                prizePool.toDoubleOrNull() ?: 500.0,
                perKillRate.toDoubleOrNull() ?: 10.0,
                rank1Prize.toDoubleOrNull() ?: 200.0,
                rank2Prize.toDoubleOrNull() ?: 100.0,
                rank3Prize.toDoubleOrNull() ?: 50.0,
                mapName,
                matchRules
              )
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("admin_create_match_button"),
            colors = ButtonDefaults.buttonColors(containerColor = ExaloteOrange)
          ) {
            Icon(imageVector = Icons.Filled.AddCircle, contentDescription = "Create")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create & Publish Tournament Live", fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    items(matches, key = { it.id }) { match ->
      AdminMatchRoomPublisherCard(match = match, onPublishRoom = onPublishRoom)
    }
  }
}

@Composable
private fun AdminMatchRoomPublisherCard(
  match: TournamentMatch,
  onPublishRoom: (matchId: String, roomId: String, roomPassword: String, roomPublished: Boolean, status: String) -> Unit
) {
  var roomId by remember(match.roomId) { mutableStateOf(match.roomId) }
  var roomPassword by remember(match.roomPassword) { mutableStateOf(match.roomPassword) }
  var selectedStatus by remember(match.status) { mutableStateOf(match.status) }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(match.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
          Text(
            "${match.format} • Slots: ${match.joinedCount}/${match.maxPlayers} • Prizes Locked: ${match.prizesDistributed}",
            style = MaterialTheme.typography.labelSmall
          )
        }
        BadgePill(
          text = if (match.roomPublished) "ROOM LIVE" else "ROOM HIDDEN",
          color = if (match.roomPublished) ExaloteEmerald else ExaloteGold
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = roomId,
          onValueChange = { roomId = it.take(40) },
          label = { Text("Custom Room ID") },
          singleLine = true,
          modifier = Modifier
            .weight(1f)
            .testTag("admin_room_id_${match.id}")
        )
        OutlinedTextField(
          value = roomPassword,
          onValueChange = { roomPassword = it.take(40) },
          label = { Text("Room Password") },
          singleLine = true,
          modifier = Modifier
            .weight(1f)
            .testTag("admin_room_pass_${match.id}")
        )
      }

      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        listOf("Upcoming", "Registration Closed", "Live", "Completed", "Cancelled").forEach { st ->
          FilterChip(
            selected = selectedStatus == st,
            onClick = { selectedStatus = st },
            label = { Text(st) }
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = {
            onPublishRoom(match.id, roomId, roomPassword, true, "Live")
          },
          enabled = roomId.isNotBlank(),
          modifier = Modifier
            .weight(1f)
            .testTag("publish_room_live_button_${match.id}"),
          colors = ButtonDefaults.buttonColors(containerColor = ExaloteEmerald)
        ) {
          Icon(imageVector = Icons.Filled.Publish, contentDescription = "Publish", modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Publish Room Live")
        }

        OutlinedButton(
          onClick = {
            onPublishRoom(match.id, roomId, roomPassword, match.roomPublished, selectedStatus)
          }
        ) {
          Text("Save Status")
        }
      }
    }
  }
}

@Composable
private fun AdminResultsVerificationTab(
  claims: List<MatchClaim>,
  matches: List<TournamentMatch>,
  onVerifyClaimAndDistributePrize: (claimId: String, verifiedKills: Int, verifiedRank: Int, lockMatchPrizes: Boolean) -> Unit,
  onRunAiAuditOnClaims: (String) -> Unit
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("1-Click Result Verification & Payouts", style = MaterialTheme.typography.titleMedium)
            Text(
              "Formula: (Kills * Per_Kill_Rate) + Rank_Prize -> Credited strictly to Winnings Balance.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          OutlinedButton(
            onClick = {
              onRunAiAuditOnClaims(
                "Audit all pending player result claims and hacker reports. Flag any suspicious kill counts or rank conflicts and verify the prize calculation."
              )
            }
          ) {
            Icon(imageVector = Icons.Filled.Psychology, contentDescription = "AI Audit", tint = ExaloteGold)
            Spacer(modifier = Modifier.width(4.dp))
            Text("AI Audit")
          }
        }
      }
    }

    items(claims, key = { it.id }) { claim ->
      val match = matches.find { it.id == claim.matchId }
      var adminKills by remember(claim.id, claim.claimedKills) {
        mutableStateOf(claim.claimedKills.toString())
      }
      var adminRank by remember(claim.id, claim.claimedRank) {
        mutableStateOf(claim.claimedRank.toString())
      }
      var lockMatchPrizes by remember { mutableStateOf(false) }

      val kInt = adminKills.toIntOrNull() ?: 0
      val rInt = (adminRank.toIntOrNull() ?: 1).coerceIn(1, 100)
      val calculatedPrize = if (match != null) calculatePrizeAmount(kInt, rInt, match) else 0.0

      Card(
        modifier = Modifier
          .fillMaxWidth()
          .border(
            1.dp,
            if (claim.hasHackerReport) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            RoundedCornerShape(14.dp)
          ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "${claim.ffIgn} (UID: ${claim.ffUid})",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
              )
              Text(
                text = "Match: ${claim.matchTitle}",
                style = MaterialTheme.typography.labelSmall
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              if (claim.hasHackerReport) {
                BadgePill(text = "HACK REPORT", color = MaterialTheme.colorScheme.error)
              }
              BadgePill(
                text = claim.status.uppercase(),
                color = if (claim.status == "verified") ExaloteEmerald else ExaloteGold
              )
            }
          }

          if (claim.hasHackerReport) {
            Card(
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
              ),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Filled.Warning,
                  contentDescription = "Hack Report",
                  tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    text = "Reported Suspect IGN: ${claim.reportSuspectIgn.ifBlank { "Unspecified" }}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                  )
                  Text(
                    text = "Evidence: ${claim.reportReason}",
                    style = MaterialTheme.typography.bodyMedium
                  )
                }
              }
            }
          }

          if (claim.screenshotBase64.isNotBlank()) {
            val bitmap = remember(claim.screenshotBase64) {
              try {
                val bytes = Base64.decode(claim.screenshotBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
              } catch (_: Exception) {
                null
              }
            }
            if (bitmap != null) {
              Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Uploaded Result Screenshot",
                modifier = Modifier
                  .fillMaxWidth()
                  .height(150.dp)
                  .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
              )
            }
          }

          if (claim.status == "pending") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = adminKills,
                onValueChange = { adminKills = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Verified Kills") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = adminRank,
                onValueChange = { adminRank = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Verified Rank") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = lockMatchPrizes,
                onCheckedChange = { lockMatchPrizes = it }
              )
              Text(
                text = "Final Payout: Lock prizes_distributed = true on match",
                style = MaterialTheme.typography.bodyMedium
              )
            }

            Button(
              onClick = {
                onVerifyClaimAndDistributePrize(claim.id, kInt, rInt, lockMatchPrizes)
              },
              enabled = match?.prizesDistributed != true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("verify_claim_button_${claim.id}"),
              colors = ButtonDefaults.buttonColors(containerColor = ExaloteEmerald)
            ) {
              Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = "Verify")
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (match?.prizesDistributed == true) {
                  "MATCH PRIZES LOCKED (ALREADY DISTRIBUTED)"
                } else {
                  "Verify & Credit ₹$calculatedPrize to Winnings"
                },
                fontWeight = FontWeight.Bold
              )
            }
          } else {
            Text(
              text = "Awarded Prize: ₹${claim.awardedAmount} (Rank #${claim.claimedRank}, ${claim.claimedKills} Kills)",
              style = MaterialTheme.typography.labelLarge,
              color = ExaloteEmerald
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AdminDepositsQueueTab(
  deposits: List<DepositRequest>,
  onDecision: (depositId: String, approve: Boolean) -> Unit
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Live UTR Deposit Queue + Promo & Referral Unlocks", style = MaterialTheme.typography.titleMedium)
          Text(
            "Approving a deposit credits the base amount to Deposit Balance, any Promo Bonus (e.g. WELCOME20) strictly to Bonus Balance, and unlocks the Referrer's ₹10 First-Deposit Bonus if applicable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    items(deposits, key = { it.id }) { dep ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("${dep.username} • ₹${dep.amount}", style = MaterialTheme.typography.titleMedium)
              Text("12-Digit UTR: ${dep.utrNumber}", style = MaterialTheme.typography.labelSmall, color = ExaloteGold)
              if (dep.promoCode.isNotBlank() && dep.bonusAmount > 0.0) {
                Text(
                  text = "Promo ${dep.promoCode}: +₹${String.format("%.2f", dep.bonusAmount)} -> Bonus Bucket",
                  style = MaterialTheme.typography.labelSmall,
                  color = ExaloteEmerald
                )
              }
            }
            BadgePill(
              text = dep.status,
              color = when (dep.status) {
                "Approved" -> ExaloteEmerald
                "Rejected" -> MaterialTheme.colorScheme.error
                else -> ExaloteGold
              }
            )
          }
          if (dep.status == "Pending") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = { onDecision(dep.id, true) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("approve_deposit_${dep.id}"),
                colors = ButtonDefaults.buttonColors(containerColor = ExaloteEmerald)
              ) {
                Text(
                  if (dep.bonusAmount > 0.0) {
                    "Approve (₹${dep.amount} + ₹${String.format("%.0f", dep.bonusAmount)} Bonus)"
                  } else {
                    "Approve & Credit Deposit"
                  }
                )
              }
              OutlinedButton(
                onClick = { onDecision(dep.id, false) },
                modifier = Modifier.weight(1f)
              ) {
                Text("Reject")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AdminWithdrawalsQueueTab(
  withdrawals: List<WithdrawalRequest>,
  onDecision: (withdrawalId: String, approve: Boolean) -> Unit
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    items(withdrawals, key = { it.id }) { wd ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("${wd.username} • ₹${wd.amount} (Escrow)", style = MaterialTheme.typography.titleMedium)
              Text("Payout UPI: ${wd.upiId}", style = MaterialTheme.typography.labelSmall, color = ExaloteCyan)
            }
            BadgePill(
              text = wd.status,
              color = when (wd.status) {
                "Completed" -> ExaloteEmerald
                "Rejected" -> MaterialTheme.colorScheme.error
                else -> ExaloteGold
              }
            )
          }
          if (wd.status == "Pending") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = { onDecision(wd.id, true) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("approve_withdrawal_${wd.id}"),
                colors = ButtonDefaults.buttonColors(containerColor = ExaloteEmerald)
              ) {
                Text("Mark Paid / Complete")
              }
              OutlinedButton(
                onClick = { onDecision(wd.id, false) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("reject_withdrawal_${wd.id}")
              ) {
                Text("Reject & Refund Escrow")
              }
            }
          }
        }
      }
    }
  }
}
