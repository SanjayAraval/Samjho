package com.packetloss.samjho.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LoginScreen(
    onAuthSuccess: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isSignUp by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .width(420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                // --------------------------------------------------
                // Branding
                // --------------------------------------------------

                Text(
                    text = "Samjho",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Understand what your doctor said.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // --------------------------------------------------
                // Login Card
                // --------------------------------------------------

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {

                        Text(
                            text = if (isSignUp) {
                                "Create your account"
                            } else {
                                "Welcome back"
                            },
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isSignUp) {
                                "Create an account to continue with Samjho."
                            } else {
                                "Log in to continue using Samjho."
                            },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.65f
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // --------------------------------------------------
                        // Email
                        // --------------------------------------------------

                        Text(
                            text = "Email",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("Enter your email")
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // --------------------------------------------------
                        // Password
                        // --------------------------------------------------

                        Text(
                            text = "Password",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("Enter your password")
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                TextButton(
                                    onClick = {
                                        passwordVisible = !passwordVisible
                                    }
                                ) {
                                    Text(
                                        text = if (passwordVisible) {
                                            "Hide"
                                        } else {
                                            "Show"
                                        },
                                        fontSize = 12.sp
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // --------------------------------------------------
                        // Error message
                        // --------------------------------------------------

                        errorMessage?.let { error ->

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = error,
                                    modifier = Modifier.padding(12.dp),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // --------------------------------------------------
                        // Login / Sign Up button
                        // --------------------------------------------------

                        Button(
                            onClick = {

                                if (email.isBlank()) {
                                    errorMessage = "Please enter your email"
                                    return@Button
                                }

                                if (password.isBlank()) {
                                    errorMessage = "Please enter your password"
                                    return@Button
                                }

                                if (isSignUp && password.length < 6) {
                                    errorMessage =
                                        "Password must be at least 6 characters"
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = null

                                val task = if (isSignUp) {
                                    auth.createUserWithEmailAndPassword(
                                        email.trim(),
                                        password
                                    )
                                } else {
                                    auth.signInWithEmailAndPassword(
                                        email.trim(),
                                        password
                                    )
                                }

                                task.addOnCompleteListener { result ->

                                    isLoading = false

                                    if (result.isSuccessful) {
                                        onAuthSuccess()
                                    } else {
                                        errorMessage =
                                            result.exception?.localizedMessage
                                                ?: "Authentication failed"
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {

                            if (isLoading) {

                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .height(22.dp)
                                        .width(22.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )

                            } else {

                                Text(
                                    text = if (isSignUp) {
                                        "Create Account"
                                    } else {
                                        "Login"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // --------------------------------------------------
                        // Login / Sign Up switch
                        // --------------------------------------------------

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Text(
                                text = if (isSignUp) {
                                    "Already have an account?"
                                } else {
                                    "Don't have an account?"
                                },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.65f
                                )
                            )

                            TextButton(
                                onClick = {
                                    isSignUp = !isSignUp
                                    errorMessage = null
                                    passwordVisible = false
                                }
                            ) {

                                Text(
                                    text = if (isSignUp) {
                                        "Login instead"
                                    } else {
                                        "Create an account"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --------------------------------------------------
                // Privacy / offline message
                // --------------------------------------------------

                Text(
                    text = "Your account is securely managed with Firebase.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(
                        alpha = 0.5f
                    )
                )
            }
        }
    }
}