package com.example.data.model

const val EXALOTE_SQL_SCHEMA = """
-- EXALOTE EXN Full SQL & Cloud Schema (with Refer & Earn + Promo Codes)
CREATE TABLE users (
  id VARCHAR(128) PRIMARY KEY,
  username VARCHAR(60) NOT NULL,
  email VARCHAR(120) NOT NULL UNIQUE,
  wallet_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  deposit_balance DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  winnings_balance DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  bonus_balance DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  ff_uid VARCHAR(12) NULL,
  ff_ign VARCHAR(60) NULL,
  upi_id VARCHAR(100) NULL,
  referral_code VARCHAR(20) DEFAULT NULL UNIQUE,
  referred_by VARCHAR(128) DEFAULT NULL,
  referral_rewarded TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE promo_codes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  bonus_percent INT NOT NULL DEFAULT 10,
  max_bonus DECIMAL(10,2) NOT NULL DEFAULT 100.00,
  min_deposit DECIMAL(10,2) NOT NULL DEFAULT 50.00,
  is_active TINYINT(1) NOT NULL DEFAULT 1
);

INSERT INTO promo_codes (code, bonus_percent, max_bonus, min_deposit, is_active)
VALUES ('WELCOME20', 20, 100.00, 50.00, 1)
ON DUPLICATE KEY UPDATE is_active = 1;

CREATE TABLE matches (
  id VARCHAR(128) PRIMARY KEY,
  title VARCHAR(150) NOT NULL,
  category_code VARCHAR(60) NOT NULL,
  category_name VARCHAR(100) NOT NULL,
  format VARCHAR(30) NOT NULL DEFAULT 'Solo',
  session ENUM('morning','evening') NOT NULL,
  match_date DATE NOT NULL,
  start_time TIME NOT NULL,
  max_players INT NOT NULL DEFAULT 48,
  joined_count INT NOT NULL DEFAULT 0,
  entry_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  prize_pool DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  per_kill_rate DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  rank1_prize DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  rank2_prize DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  rank3_prize DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  status ENUM('Upcoming','Registration Closed','Live','Completed','Cancelled') NOT NULL DEFAULT 'Upcoming',
  room_id VARCHAR(50) NULL,
  room_password VARCHAR(50) NULL,
  room_published BOOLEAN NOT NULL DEFAULT FALSE,
  map_name VARCHAR(50) NOT NULL DEFAULT 'Bermuda',
  match_rules TEXT NULL,
  prizes_distributed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE registrations (
  id VARCHAR(128) PRIMARY KEY,
  match_id VARCHAR(128) NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  status ENUM('Approved','Pending','Rejected') NOT NULL DEFAULT 'Approved',
  fee_status ENUM('none','paid','refunded') NOT NULL DEFAULT 'paid',
  ff_uid VARCHAR(12) NOT NULL,
  ff_ign VARCHAR(60) NOT NULL,
  slot_number INT NOT NULL,
  team_members JSON NULL,
  UNIQUE (match_id, user_id),
  UNIQUE (match_id, ff_uid)
);

CREATE TABLE match_claims (
  id VARCHAR(128) PRIMARY KEY,
  match_id VARCHAR(128) NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  claimed_kills INT NOT NULL DEFAULT 0,
  claimed_rank INT NOT NULL DEFAULT 1,
  screenshot_base64 MEDIUMTEXT NULL,
  report_suspect_ign VARCHAR(100) NULL,
  report_reason TEXT NULL,
  status ENUM('pending','verified','rejected') NOT NULL DEFAULT 'pending',
  awarded_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00
);

CREATE TABLE deposits (
  id VARCHAR(128) PRIMARY KEY,
  user_id VARCHAR(128) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  utr_number CHAR(12) NOT NULL UNIQUE,
  promo_code VARCHAR(30) DEFAULT NULL,
  bonus_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  status ENUM('Pending','Approved','Rejected') NOT NULL DEFAULT 'Pending'
);

CREATE TABLE withdrawals (
  id VARCHAR(128) PRIMARY KEY,
  user_id VARCHAR(128) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  upi_id VARCHAR(100) NOT NULL,
  status ENUM('Pending','Completed','Rejected') NOT NULL DEFAULT 'Pending'
);
"""

const val EXALOTE_FIRESTORE_RULES_SNIPPET = """
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} { allow read, write: if false; }
    function isSignedIn() { return request.auth != null; }
    function isBootstrappedAdmin() {
      return isSignedIn() &&
             'email_verified' in request.auth.token &&
             request.auth.token.email_verified == true &&
             'email' in request.auth.token &&
             request.auth.token.email == 'foodievlog422@gmail.com';
    }
    function isAdmin() {
      return isSignedIn() && (
        isBootstrappedAdmin() ||
        exists(/databases/$(database)/documents/admins/$(request.auth.uid))
      );
    }
    // Full zero-trust schema validation active on:
    // /users/{userId}, /referral_codes/{code}, /promo_codes/{code},
    // /matches/{matchId}, /matches/{matchId}/secrets/{secretId},
    // /registrations/{registrationId}, /deposits/{depositId},
    // /withdrawals/{withdrawalId}, /transactions/{transactionId}
  }
}
"""
