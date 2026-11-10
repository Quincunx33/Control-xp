package com.example.nexusremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NexusCyan

@Composable
fun AccountGate(viewModel: AgentViewModel) {
  var isRegister by remember { mutableStateOf(false) }
  var name by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  val loading by viewModel.authLoading.collectAsState()
  val error by viewModel.authError.collectAsState()

  Column(
    modifier = Modifier.fillMaxSize().background(Color(0xFF070B14)).padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text("NEXUS REMOTE", color = NexusCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Spacer(Modifier.height(12.dp))
    Text(if (isRegister) "Create your account" else "Welcome back", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
    Spacer(Modifier.height(8.dp))
    Text("Use the same account on the Android app and web dashboard.", color = Color.LightGray, fontSize = 13.sp)
    Spacer(Modifier.height(24.dp))
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
    ) {
      Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isRegister) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (!error.isNullOrBlank()) Text(error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Button(
          onClick = { viewModel.authenticate(email, password, if (isRegister) name else null) },
          enabled = !loading && email.contains("@") && password.length >= 8 && (!isRegister || name.trim().length >= 2),
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = NexusCyan)
        ) {
          if (loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp), color = Color.Black)
          else Text(if (isRegister) "Create account" else "Sign in", color = Color(0xFF06111A), fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { isRegister = !isRegister }) {
          Text(if (isRegister) "Already registered? Sign in" else "New here? Create an account", color = NexusCyan)
        }
      }
    }
    Spacer(Modifier.height(18.dp))
    Text("Only connect devices you own or are authorized to manage.", color = Color.Gray, fontSize = 11.sp)
  }
}
