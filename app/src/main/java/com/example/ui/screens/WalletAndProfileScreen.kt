package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DEFAULT_PROMO_CODES
import com.example.data.model.DepositRequest
import com.example.data.model.EXALOTE_FIRESTORE_RULES_SNIPPET
import com.example.data.model.EXALOTE_SQL_SCHEMA
import com.example.data.model.PromoCode
import com.example.data.model.UserProfile
import com.example.data.model.WalletTransaction
import com.example.data.model.WithdrawalRequest
import com.example.data.model.calculatePromoBonus
import com.example.ui.theme.ExaloteCyan
import com.example.ui.theme.ExaloteEmerald
import com.example.ui.theme.ExaloteGold
import com.example.ui.theme.ExaloteOrange
import com.example.ui.viewmodel.AiThinkingState

@Composable
fun ThreeBucketWalletScreen(
  userProfile: UserProfile?,
  deposits: List<DepositRequest>,
  withdrawals: List<WithdrawalRequest>,
  transactions: List<WalletTransaction>,
  promoCodes: List<PromoCode> = DEFAULT_PROMO_CODES,
  onSubmitDeposit: (Double, String, String) -> Unit,
  onRequestWithdrawal: (Double, String) -> Unit
) {
  var depositAmount by remember { mutableStateOf("100") }
  var utrNumber by remember { mutableStateOf("") }
  var promoCodeInput by remember { mutableStateOf("WELCOME20") }
  var withdrawAmount by remember { mutableStateOf("50") }
  var withdrawUpi by remember(userProfile?.upiId) {
    mutableStateOf(userProfile?.upiId ?: "")
  }
  var activeTab by remember { mutableIntStateOf(0) }

  val parsedDepositAmt = depositAmount.toDoubleOrNull() ?: 0.0
  val matchedPromo = remember(promoCodeInput, promoCodes) {
    val clean = promoCodeInput.trim().uppercase()
    if (clean.isBlank()) null
    else promoCodes.find { it.code.equals(clean, ignoreCase = true) && it.isActive }
      ?: DEFAULT_PROMO_CODES.find { it.code.equals(clean, ignoreCase = true) && it.isActive }
  }
  val previewBonus = remember(parsedDepositAmt, matchedPromo) {
    calculatePromoBonus(parsedDepositAmt, matchedPromo)
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      // 3-Bucket Hero Wallet Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, ExaloteGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.linearGradient(
                listOf(Color(0xFF1F1638), MaterialTheme.colorScheme.surface)
              )
            )
            .padding(20.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "TOTAL WALLET BALANCE",
                style = MaterialTheme.typography.labelSmall,
                color = ExaloteGold
              )
              Text(
                text = "₹${String.format("%.2f", userProfile?.walletBalance ?: 0.0)}",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
              )
            }
            Icon(
              imageVector = Icons.Filled.AccountBalanceWallet,
              contentDescription = "Wallet",
              tint = ExaloteGold,
              modifier = Modifier.size(40.dp)
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            BucketMiniCard(
              modifier = Modifier.weight(1f),
              title = "DEPOSIT",
              amount = userProfile?.depositBalance ?: 0.0,
              subtitle = "100% Usable",
              accent = ExaloteCyan
            )
            BucketMiniCard(
              modifier = Modifier.weight(1f),
              title = "WINNINGS",
              amount = userProfile?.winningsBalance ?: 0.0,
              subtitle = "Withdrawable",
              accent = ExaloteEmerald
            )
            BucketMiniCard(
              modifier = Modifier.weight(1f),
              title = "BONUS",
              amount = userProfile?.bonusBalance ?: 0.0,
              subtitle = "10% / Match",
              accent = ExaloteGold
            )
          }
        }
      }
    }

    item {
      TabRow(
        selectedTabIndex = activeTab,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
      ) {
        Tab(
          selected = activeTab == 0,
          onClick = { activeTab = 0 },
          text = { Text("UPI Deposit") },
          modifier = Modifier.testTag("wallet_tab_deposit")
        )
        Tab(
          selected = activeTab == 1,
          onClick = { activeTab = 1 },
          text = { Text("Withdraw") },
          modifier = Modifier.testTag("wallet_tab_withdraw")
        )
        Tab(
          selected = activeTab == 2,
          onClick = { activeTab = 2 },
          text = { Text("Ledger (${transactions.size})") },
          modifier = Modifier.testTag("wallet_tab_ledger")
        )
      }
    }

    if (activeTab == 0) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Text(
              text = "Manual UPI Deposit + Promo Bonus",
              style = MaterialTheme.typography.titleLarge,
              color = Color.White
            )
            Text(
              text = "Send UPI payment to Official Merchant ID: exalote.exn@upi and enter your unique 12-digit UTR number below. Promo bonuses are credited strictly to your non-withdrawable Bonus Balance.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
              value = depositAmount,
              onValueChange = { depositAmount = it.filter { c -> c.isDigit() || c == '.' }.take(8) },
              label = { Text("Deposit Amount (₹10 – ₹1,00,000)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("deposit_amount_input")
            )

            // Active Promo Code Chips
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Filled.LocalOffer,
                contentDescription = "Promo Codes",
                tint = ExaloteGold,
                modifier = Modifier.size(18.dp)
              )
              promoCodes.forEach { promo ->
                val isSelected = promoCodeInput.trim().equals(promo.code, ignoreCase = true)
                FilterChip(
                  selected = isSelected,
                  onClick = {
                    promoCodeInput = if (isSelected) "" else promo.code
                  },
                  label = {
                    Text("${promo.code} (+${promo.bonusPercent}% up to ₹${promo.maxBonus.toInt()}, Min ₹${promo.minDeposit.toInt()})")
                  },
                  modifier = Modifier.testTag("promo_chip_${promo.code}")
                )
              }
            }

            OutlinedTextField(
              value = promoCodeInput,
              onValueChange = { promoCodeInput = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(30) },
              label = { Text("Promo Code (Optional • e.g. WELCOME20)") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("deposit_promo_input")
            )

            if (promoCodeInput.isNotBlank()) {
              val mPromo = matchedPromo
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = if (previewBonus > 0.0) ExaloteGold.copy(alpha = 0.14f)
                  else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = when {
                    mPromo == null -> "Promo code '${promoCodeInput.trim()}' not found or inactive."
                    parsedDepositAmt < mPromo.minDeposit ->
                      "Add ₹${String.format("%.0f", mPromo.minDeposit - parsedDepositAmt)} more to unlock ${mPromo.code} (${mPromo.bonusPercent}% bonus on min ₹${mPromo.minDeposit.toInt()})."
                    else ->
                      "Promo ${mPromo.code} Active: +₹${String.format("%.2f", previewBonus)} Extra Bonus will be credited to your Bonus Balance upon Admin approval!"
                  },
                  style = MaterialTheme.typography.labelLarge,
                  color = if (previewBonus > 0.0) ExaloteGold else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(10.dp)
                )
              }
            }

            OutlinedTextField(
              value = utrNumber,
              onValueChange = { utrNumber = it.filter { c -> c.isDigit() }.take(12) },
              label = { Text("12-Digit UPI UTR Reference (${utrNumber.length}/12)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("deposit_utr_input")
            )

            Button(
              onClick = {
                val amt = depositAmount.toDoubleOrNull() ?: 0.0
                onSubmitDeposit(amt, utrNumber, promoCodeInput.trim())
                utrNumber = ""
              },
              enabled = utrNumber.length == 12 && (depositAmount.toDoubleOrNull() ?: 0.0) >= 10.0,
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("submit_deposit_button"),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = ExaloteOrange)
            ) {
              Text(
                text = if (previewBonus > 0.0) {
                  "Submit ₹$depositAmount Deposit (+₹${String.format("%.0f", previewBonus)} Bonus)"
                } else {
                  "Submit Deposit for Live Approval"
                },
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      items(deposits, key = { it.id }) { dep ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(12.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Deposit ₹${dep.amount}", style = MaterialTheme.typography.titleMedium)
              Text("UTR: ${dep.utrNumber}", style = MaterialTheme.typography.labelSmall)
              if (dep.promoCode.isNotBlank() && dep.bonusAmount > 0.0) {
                Text(
                  text = "Promo ${dep.promoCode}: +₹${String.format("%.2f", dep.bonusAmount)} Bonus",
                  style = MaterialTheme.typography.labelSmall,
                  color = ExaloteGold
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
        }
      }
    } else if (activeTab == 1) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Text(
              text = "Withdraw Winnings (Immediate Escrow)",
              style = MaterialTheme.typography.titleLarge,
              color = Color.White
            )
            Text(
              text = "Available Withdrawable Winnings: ₹${String.format("%.2f", userProfile?.winningsBalance ?: 0.0)} (Min ₹50). Bonus & Deposit balances cannot be withdrawn directly.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
              value = withdrawAmount,
              onValueChange = { withdrawAmount = it.filter { c -> c.isDigit() || c == '.' }.take(8) },
              label = { Text("Withdrawal Amount (Min ₹50)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("withdraw_amount_input")
            )

            OutlinedTextField(
              value = withdrawUpi,
              onValueChange = { withdrawUpi = it.trim().take(80) },
              label = { Text("Receiving UPI ID (e.g. player@okaxis)") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("withdraw_upi_input")
            )

            Button(
              onClick = {
                val amt = withdrawAmount.toDoubleOrNull() ?: 0.0
                onRequestWithdrawal(amt, withdrawUpi)
              },
              enabled = (withdrawAmount.toDoubleOrNull() ?: 0.0) >= 50.0 && withdrawUpi.contains("@"),
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("submit_withdrawal_button"),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = ExaloteEmerald)
            ) {
              Text("Request Winnings Withdrawal", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      items(withdrawals, key = { it.id }) { wd ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(12.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Withdraw ₹${wd.amount}", style = MaterialTheme.typography.titleMedium)
              Text("UPI: ${wd.upiId}", style = MaterialTheme.typography.labelSmall)
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
        }
      }
    } else {
      items(transactions, key = { it.id }) { tx ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(12.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(tx.description, style = MaterialTheme.typography.titleMedium)
              Text("Type: ${tx.type.uppercase()}", style = MaterialTheme.typography.labelSmall)
            }
            val isCredit = tx.type in listOf("deposit", "prize_payout", "refund", "bonus")
            Text(
              text = "${if (isCredit) "+" else "-"}₹${tx.amount}",
              style = MaterialTheme.typography.titleLarge,
              color = if (isCredit) ExaloteEmerald else ExaloteOrange
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BucketMiniCard(
  modifier: Modifier = Modifier,
  title: String,
  amount: Double,
  subtitle: String,
  accent: Color
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
      .padding(10.dp)
  ) {
    Text(text = title, style = MaterialTheme.typography.labelSmall, color = accent)
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = "₹${String.format("%.1f", amount)}",
      style = MaterialTheme.typography.titleLarge,
      color = Color.White
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
fun ProfileAndAiScreen(
  userProfile: UserProfile?,
  isAdmin: Boolean,
  aiState: AiThinkingState,
  onSaveBinding: (username: String, ffUid: String, ffIgn: String, upiId: String) -> Unit,
  onApplyReferralCode: (String) -> Unit = {},
  onRunHighThinkingQuery: (String) -> Unit,
  onOpenDbConfigModal: () -> Unit,
  onSignOut: () -> Unit
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current

  var username by remember(userProfile?.username) { mutableStateOf(userProfile?.username ?: "") }
  var ffUid by remember(userProfile?.ffUid) { mutableStateOf(userProfile?.ffUid ?: "") }
  var ffIgn by remember(userProfile?.ffIgn) { mutableStateOf(userProfile?.ffIgn ?: "") }
  var upiId by remember(userProfile?.upiId) { mutableStateOf(userProfile?.upiId ?: "") }
  var friendReferralCode by remember { mutableStateOf("") }
  var copyFeedback by remember { mutableStateOf<String?>(null) }

  var aiPrompt by remember {
    mutableStateOf(
      "Analyze our active tournaments, 3-bucket wallet fee deduction rules, and any player result claims or hack reports. Give me a step-by-step tactical breakdown."
    )
  }

  LaunchedEffect(userProfile) {
    if (userProfile != null) {
      if (username.isBlank()) username = userProfile.username
      if (ffUid.isBlank()) ffUid = userProfile.ffUid
      if (ffIgn.isBlank()) ffIgn = userProfile.ffIgn
      if (upiId.isBlank()) upiId = userProfile.upiId
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Refer & Earn ("Anti-Fake Account Logic") Card
    val myRefCode = userProfile?.referralCode?.ifBlank { "EXNPLAYER" } ?: "EXNPLAYER"
    val shareLink = "https://exalote.gg/register?ref=$myRefCode"
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, ExaloteEmerald.copy(alpha = 0.55f), RoundedCornerShape(18.dp)),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.CardGiftcard,
              contentDescription = "Refer & Earn",
              tint = ExaloteEmerald
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "Refer & Earn Bonus Program",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
              )
              Text(
                text = "Friend gets ₹5 Signup Bonus • You get ₹10 on their 1st Approved Deposit",
                style = MaterialTheme.typography.labelSmall,
                color = ExaloteEmerald
              )
            }
          }
        }

        // Referral Stats Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text("YOUR REFERRAL CODE", style = MaterialTheme.typography.labelSmall, color = ExaloteGold)
              Text(
                text = myRefCode,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
              )
            }
          }
          Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text("VERIFIED REFERRALS", style = MaterialTheme.typography.labelSmall, color = ExaloteCyan)
              Text(
                text = "${userProfile?.totalReferrals ?: 0} Players (₹${String.format("%.0f", userProfile?.referralBonusEarned ?: 0.0)})",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(myRefCode))
              copyFeedback = "Copied code $myRefCode!"
            },
            modifier = Modifier
              .weight(1f)
              .testTag("copy_referral_code_button"),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy Code", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(copyFeedback ?: "Copy Code")
          }

          Button(
            onClick = {
              val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                  Intent.EXTRA_TEXT,
                  "Join me on EXALOTE EXN Free Fire MAX Tournaments! Use my Referral Code $myRefCode for an instant ₹5 Bonus: $shareLink"
                )
              }
              context.startActivity(Intent.createChooser(sendIntent, "Share Referral Link"))
            },
            modifier = Modifier
              .weight(1f)
              .testTag("share_referral_link_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ExaloteEmerald, contentColor = Color.Black)
          ) {
            Icon(imageVector = Icons.Filled.Share, contentDescription = "Share Link", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Share Link", fontWeight = FontWeight.Bold)
          }
        }

        if (userProfile?.referredBy.isNullOrBlank()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = friendReferralCode,
              onValueChange = { friendReferralCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(20) },
              label = { Text("Have a Friend's Referral Code? (+₹5 Bonus)") },
              singleLine = true,
              modifier = Modifier
                .weight(1f)
                .testTag("referral_code_input")
            )
            Button(
              onClick = {
                onApplyReferralCode(friendReferralCode)
                friendReferralCode = ""
              },
              enabled = friendReferralCode.length >= 4,
              modifier = Modifier
                .height(54.dp)
                .testTag("apply_referral_code_button"),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = ExaloteGold, contentColor = Color.Black)
            ) {
              Text("Claim ₹5", fontWeight = FontWeight.Bold)
            }
          }
        } else {
          BadgePill(
            text = if (userProfile?.referralRewarded == true) {
              "Referral Linked • 1st Deposit Bonus Unlocked for Referrer"
            } else {
              "Referral Claimed (+₹5 Credited) • Referrer unlocks ₹10 on your 1st Deposit"
            },
            color = ExaloteEmerald
          )
        }
      }
    }

    // 2. Free Fire UID & IGN Binding Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.VerifiedUser,
              contentDescription = "FF Binding",
              tint = ExaloteOrange
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Free Fire MAX Identity Binding",
              style = MaterialTheme.typography.titleLarge
            )
          }
          BadgePill(
            text = if (isAdmin) "ADMIN ACCOUNT" else "PLAYER ACCOUNT",
            color = if (isAdmin) ExaloteGold else ExaloteCyan
          )
        }

        OutlinedTextField(
          value = username,
          onValueChange = { username = it.take(50) },
          label = { Text("Display Username") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_username_input")
        )

        OutlinedTextField(
          value = ffUid,
          onValueChange = { ffUid = it.filter { c -> c.isDigit() }.take(12) },
          label = { Text("Free Fire Numeric UID (8–12 Digits) • Required") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_ff_uid_input")
        )

        OutlinedTextField(
          value = ffIgn,
          onValueChange = { ffIgn = it.take(60) },
          label = { Text("Free Fire In-Game Name (IGN) • Required") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_ff_ign_input")
        )

        OutlinedTextField(
          value = upiId,
          onValueChange = { upiId = it.trim().take(100) },
          label = { Text("Default Payout UPI ID (e.g. name@okaxis)") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_upi_input")
        )

        Button(
          onClick = { onSaveBinding(username, ffUid, ffIgn, upiId) },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("save_profile_binding_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ExaloteOrange)
        ) {
          Text("Save Free Fire UID & IGN Binding", fontWeight = FontWeight.Bold)
        }
      }
    }

    // 3. High-Thinking AI Analyst Card (gemini-4-argon + ThinkingLevel.HIGH)
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, ExaloteGold.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Filled.Psychology,
            contentDescription = "High Thinking AI",
            tint = ExaloteGold
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "High-Thinking Esports & Anti-Cheat Analyst",
              style = MaterialTheme.typography.titleLarge,
              color = ExaloteGold
            )
            Text(
              text = "Powered by gemini-4-argon (ThinkingLevel.HIGH)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        OutlinedTextField(
          value = aiPrompt,
          onValueChange = { aiPrompt = it },
          label = { Text("Ask complex strategy, prize math, or anti-cheat audit query") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_high_thinking_input")
        )

        Button(
          onClick = { onRunHighThinkingQuery(aiPrompt) },
          enabled = aiState !is AiThinkingState.Thinking && aiPrompt.isNotBlank(),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("run_high_thinking_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = ExaloteGold,
            contentColor = Color.Black
          )
        ) {
          Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = "Analyze")
          Spacer(modifier = Modifier.width(8.dp))
          Text("Run High-Thinking Analysis", fontWeight = FontWeight.Bold)
        }

        when (aiState) {
          is AiThinkingState.Idle -> {}
          is AiThinkingState.Thinking -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ExaloteGold)
              Text("Deep reasoning in progress (ThinkingLevel.HIGH)...", color = ExaloteGold)
            }
          }
          is AiThinkingState.Streaming -> {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text(
                text = aiState.partialText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
              )
            }
          }
          is AiThinkingState.Success -> {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text(
                text = aiState.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
              )
            }
          }
          is AiThinkingState.Error -> {
            Text(
              text = aiState.message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }
      }
    }

    // 4. Database & Admin Config + Sign Out Buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      OutlinedButton(
        onClick = onOpenDbConfigModal,
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("profile_db_config_button"),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(imageVector = Icons.Filled.Security, contentDescription = "DB Config", modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("DB & Rules Config")
      }

      OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("sign_out_button"),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Sign Out")
      }
    }
  }
}

@Composable
fun DatabaseAndAdminConfigDialog(
  databaseId: String,
  currentUserId: String,
  currentUserEmail: String,
  isAdmin: Boolean,
  onGrantAdmin: (String, String) -> Unit,
  onDismiss: () -> Unit
) {
  val clipboardManager = LocalClipboardManager.current
  var selectedSchemaTab by remember { mutableIntStateOf(0) }
  var targetUid by remember { mutableStateOf(currentUserId) }
  var targetEmail by remember { mutableStateOf(currentUserEmail) }
  var copiedFeedback by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text("Live Cloud Database & Admin Config", style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "LIVE FIREBASE ENTERPRISE CONNECTION",
              style = MaterialTheme.typography.labelSmall,
              color = ExaloteEmerald
            )
            Text("Database ID: $databaseId", style = MaterialTheme.typography.labelSmall)
            Text("Your UID: ${currentUserId.ifBlank { "Not signed in" }}", style = MaterialTheme.typography.labelSmall)
            Text(
              text = "Active Role: ${if (isAdmin) "ADMIN (Full Access)" else "USER (Player Access)"}",
              style = MaterialTheme.typography.labelLarge,
              color = if (isAdmin) ExaloteGold else ExaloteCyan
            )
          }
        }

        if (isAdmin && currentUserId.isNotBlank()) {
          Text(
            text = "Authorize Additional Admin UID (/admins/{uid})",
            style = MaterialTheme.typography.titleMedium
          )
          OutlinedTextField(
            value = targetUid,
            onValueChange = { targetUid = it.trim() },
            label = { Text("Target Firebase UID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          Button(
            onClick = { onGrantAdmin(targetUid, targetEmail) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ExaloteGold, contentColor = Color.Black)
          ) {
            Text("Grant Admin Privileges in Firestore")
          }
        }

        TabRow(selectedTabIndex = selectedSchemaTab) {
          Tab(
            selected = selectedSchemaTab == 0,
            onClick = { selectedSchemaTab = 0 },
            text = { Text("SQL Schema") }
          )
          Tab(
            selected = selectedSchemaTab == 1,
            onClick = { selectedSchemaTab = 1 },
            text = { Text("Firestore Rules") }
          )
        }

        val codeToShow = if (selectedSchemaTab == 0) EXALOTE_SQL_SCHEMA else EXALOTE_FIRESTORE_RULES_SNIPPET
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = copiedFeedback ?: "1-Click Copy Schema / Security Rules",
            style = MaterialTheme.typography.labelSmall,
            color = ExaloteGold
          )
          OutlinedButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(codeToShow))
              copiedFeedback = "Copied to clipboard!"
            }
          ) {
            Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy")
          }
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF060A12))
            .padding(10.dp)
        ) {
          Text(
            text = codeToShow,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFE2E8F0)
          )
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss) {
        Text("Done")
      }
    }
  )
}
