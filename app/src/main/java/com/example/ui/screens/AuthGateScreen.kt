package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.KingoLogoBadge
import com.example.ui.components.isInternetAvailable
import com.example.ui.theme.AmberDark
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.MainViewModel

/**
 * Mandatory Login / Sign-Up Gate Screen for Kingo King User App.
 * Features:
 * - Instant Sign In with Email & Password
 * - Mandatory 6-Digit Email OTP Verification during Sign Up (Create Account)
 * - Complete Forgot Password & Account Recovery flow via 6-Digit Email OTP
 * - Automatic background cloud sync (no server URL configuration exposed to end users)
 */
@Composable
fun AuthGateScreen(
    viewModel: MainViewModel,
    onRequireInternetPopup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var authTabIndex by remember { mutableIntStateOf(0) } // 0 = Sign In, 1 = Create Account
    var isForgotPasswordMode by remember { mutableStateOf(false) }

    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }

    // Email Verification OTP states (Sign Up & Forgot Password)
    var otpInput by remember { mutableStateOf("") }
    var generatedOtpPreview by remember { mutableStateOf<String?>(null) }
    var isOtpSent by remember { mutableStateOf(false) }
    var isSendingOtp by remember { mutableStateOf(false) }

    // Forgot Password new password states
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmNewPasswordInput by remember { mutableStateOf("") }

    var authFeedback by remember { mutableStateOf<String?>(null) }
    var isSuccessFeedback by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("auth_gate_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Royal KINGO KING Header & Logo
            KingoLogoBadge(isAdmin = false, size = 76.dp)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "KINGO KING",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isForgotPasswordMode) {
                        "Verify your registered email address with a 6-digit OTP to reset your password."
                    } else {
                        "Sign in or create your verified account to access live tasks & earn real rewards."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (isForgotPasswordMode) {
                        // =========================================================
                        // FORGOT PASSWORD / ACCOUNT RECOVERY FLOW
                        // =========================================================
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    isForgotPasswordMode = false
                                    isOtpSent = false
                                    otpInput = ""
                                    generatedOtpPreview = null
                                    newPasswordInput = ""
                                    confirmNewPasswordInput = ""
                                    authFeedback = null
                                },
                                modifier = Modifier.testTag("back_to_sign_in_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Sign In",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Back to Sign In", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Reset Your Password",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Step 1: Enter your registered email to receive a 6-digit verification OTP.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                if (isOtpSent) {
                                    isOtpSent = false
                                    generatedOtpPreview = null
                                    otpInput = ""
                                }
                            },
                            label = { Text("Registered Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("forgot_password_email_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                if (!isInternetAvailable(context)) {
                                    onRequireInternetPopup()
                                    return@OutlinedButton
                                }
                                isSendingOtp = true
                                authFeedback = null
                                viewModel.sendEmailVerificationOtp(
                                    email = emailInput,
                                    isPasswordReset = true
                                ) { ok, msg, code ->
                                    isSendingOtp = false
                                    isSuccessFeedback = ok
                                    authFeedback = msg
                                    if (ok) {
                                        isOtpSent = true
                                        generatedOtpPreview = code
                                        Toast.makeText(context, "OTP Sent!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isSendingOtp && emailInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("forgot_password_send_otp_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSendingOtp) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking Account & Sending OTP...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MarkEmailRead,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isOtpSent) "Resend 6-Digit OTP" else "Send Verification OTP",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isOtpSent) {
                            Spacer(modifier = Modifier.height(14.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SuccessGreen.copy(alpha = 0.10f))
                                    .border(1.dp, SuccessGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MarkEmailRead,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Verification OTP Sent to Your Email",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = SuccessGreen,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Please check your Email Inbox (or Spam folder) for $emailInput and enter the 6-digit code below.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { if (it.length <= 6) otpInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("6-Digit Verification OTP") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("forgot_password_otp_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = newPasswordInput,
                                onValueChange = { newPasswordInput = it },
                                label = { Text("New Password (min 4 chars)") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("forgot_password_new_pw_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = confirmNewPasswordInput,
                                onValueChange = { confirmNewPasswordInput = it },
                                label = { Text("Confirm New Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("forgot_password_confirm_pw_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    if (!isInternetAvailable(context)) {
                                        onRequireInternetPopup()
                                        return@Button
                                    }
                                    if (otpInput.length != 6) {
                                        isSuccessFeedback = false
                                        authFeedback = "Please enter the 6-digit verification OTP."
                                        return@Button
                                    }
                                    if (newPasswordInput.length < 4) {
                                        isSuccessFeedback = false
                                        authFeedback = "New password must be at least 4 characters."
                                        return@Button
                                    }
                                    if (newPasswordInput != confirmNewPasswordInput) {
                                        isSuccessFeedback = false
                                        authFeedback = "Passwords do not match. Please re-check."
                                        return@Button
                                    }
                                    isLoading = true
                                    authFeedback = null
                                    viewModel.resetPasswordWithOtp(
                                        email = emailInput,
                                        enteredOtp = otpInput,
                                        newPassword = newPasswordInput
                                    ) { ok, msg ->
                                        isLoading = false
                                        isSuccessFeedback = ok
                                        authFeedback = msg
                                        if (ok) {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("forgot_password_submit_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color = Color.Black,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Updating Password...", color = Color.Black, fontWeight = FontWeight.Bold)
                                } else {
                                    Text(
                                        text = "Verify OTP & Reset Password",
                                        color = Color.Black,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        authFeedback?.let { msg ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = msg,
                                color = if (isSuccessFeedback) SuccessGreen else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // =========================================================
                        // STANDARD SIGN IN / CREATE ACCOUNT (WITH EMAIL VERIFICATION)
                        // =========================================================
                        TabRow(
                            selectedTabIndex = authTabIndex,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            Tab(
                                selected = authTabIndex == 0,
                                onClick = {
                                    authTabIndex = 0
                                    authFeedback = null
                                    isOtpSent = false
                                    otpInput = ""
                                    generatedOtpPreview = null
                                },
                                text = { Text("Sign In", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = authTabIndex == 1,
                                onClick = {
                                    authTabIndex = 1
                                    authFeedback = null
                                    isOtpSent = false
                                    otpInput = ""
                                    generatedOtpPreview = null
                                },
                                text = { Text("Create Account", fontWeight = FontWeight.Bold) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (authTabIndex == 1) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Full Name") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_gate_name_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                if (isOtpSent) {
                                    isOtpSent = false
                                    generatedOtpPreview = null
                                    otpInput = ""
                                }
                            },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_gate_email_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password (min 4 chars)") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_gate_password_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (authTabIndex == 0) {
                            // Forgot Password link on Sign In tab
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        isForgotPasswordMode = true
                                        isOtpSent = false
                                        otpInput = ""
                                        generatedOtpPreview = null
                                        authFeedback = null
                                    },
                                    modifier = Modifier.testTag("auth_gate_forgot_password_button")
                                ) {
                                    Text(
                                        text = "Forgot Password?",
                                        color = AmberDark,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            // Mandatory Email Verification OTP Section on Create Account tab
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    if (!isInternetAvailable(context)) {
                                        onRequireInternetPopup()
                                        return@OutlinedButton
                                    }
                                    if (passwordInput.length < 4) {
                                        isSuccessFeedback = false
                                        authFeedback = "Please enter a password of at least 4 characters first."
                                        return@OutlinedButton
                                    }
                                    isSendingOtp = true
                                    authFeedback = null
                                    viewModel.sendEmailVerificationOtp(
                                        email = emailInput,
                                        isPasswordReset = false
                                    ) { ok, msg, code ->
                                        isSendingOtp = false
                                        isSuccessFeedback = ok
                                        authFeedback = msg
                                        if (ok) {
                                            isOtpSent = true
                                            generatedOtpPreview = code
                                            Toast.makeText(context, "Verification OTP sent!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isSendingOtp && emailInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("auth_gate_send_otp_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isSendingOtp) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sending Verification OTP...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = if (isOtpSent) Icons.Default.CheckCircle else Icons.Default.MarkEmailRead,
                                        contentDescription = null,
                                        tint = if (isOtpSent) SuccessGreen else AmberDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isOtpSent) "Resend Email Verification OTP" else "Send 6-Digit Email Verification OTP",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (isOtpSent) {
                                Spacer(modifier = Modifier.height(12.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SuccessGreen.copy(alpha = 0.10f))
                                        .border(1.dp, SuccessGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MarkEmailRead,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Verification OTP Sent to Your Email",
                                                fontWeight = FontWeight.ExtraBold,
                                                color = SuccessGreen,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Please check your Email Inbox (or Spam folder) for $emailInput and enter the 6-digit code below.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = otpInput,
                                    onValueChange = { if (it.length <= 6) otpInput = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Enter 6-Digit Email OTP") },
                                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_gate_otp_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        authFeedback?.let { msg ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = msg,
                                color = if (isSuccessFeedback) SuccessGreen else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Button(
                            onClick = {
                                if (!isInternetAvailable(context)) {
                                    onRequireInternetPopup()
                                    return@Button
                                }
                                if (authTabIndex == 0) {
                                    isLoading = true
                                    authFeedback = null
                                    viewModel.login(emailInput, passwordInput) { success, msg ->
                                        isLoading = false
                                        isSuccessFeedback = success
                                        authFeedback = msg
                                        if (success) {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    // Sign Up requires verified 6-digit OTP!
                                    if (!isOtpSent) {
                                        isSuccessFeedback = false
                                        authFeedback = "Please tap 'Send 6-Digit Email Verification OTP' first to verify your email."
                                        return@Button
                                    }
                                    if (!viewModel.verifyEmailOtp(emailInput, otpInput)) {
                                        isSuccessFeedback = false
                                        authFeedback = "Invalid 6-digit OTP code. Please enter the verification code sent to your email."
                                        return@Button
                                    }
                                    isLoading = true
                                    authFeedback = null
                                    viewModel.signUp(emailInput, passwordInput, nameInput) { success, msg ->
                                        isLoading = false
                                        isSuccessFeedback = success
                                        authFeedback = msg
                                        if (success) {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_gate_submit_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (authTabIndex == 0) "Signing In..." else "Creating Verified Account...",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = if (authTabIndex == 0) "Sign In to Kingo King" else "Verify Email & Create Account",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
