package com.penink.vpn.ui.nodes

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditScreen(viewModel: NodeViewModel, nodeId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val nodes by viewModel.nodes.collectAsState()
    val existing = nodes.find { it.id == nodeId }

    var name by remember(nodeId) { mutableStateOf(existing?.name ?: "") }
    var address by remember(nodeId) { mutableStateOf(existing?.address ?: "") }
    var port by remember(nodeId) { mutableStateOf((existing?.port ?: 443).toString()) }
    var password by remember(nodeId) { mutableStateOf(existing?.password ?: "") }

    fun save() {
        val trimmedName = name.trim()
        val trimmedAddr = address.trim()
        if (trimmedName.isBlank() || trimmedAddr.isBlank()) {
            Toast.makeText(context, "請填寫節點名稱與伺服器位址", Toast.LENGTH_SHORT).show()
            return
        }
        val portInt = port.trim().toIntOrNull()
        if (portInt == null || portInt !in 1..65535) {
            Toast.makeText(context, "連接埠格式不正確（1-65535）", Toast.LENGTH_SHORT).show()
            return
        }
        if (nodeId > 0) {
            viewModel.updateNode(nodeId, trimmedName, trimmedAddr, portInt, password.trim())
            Toast.makeText(context, "節點已更新", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addNode(trimmedName, trimmedAddr, portInt, password.trim())
        }
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (nodeId > 0) "編輯節點" else "新增節點", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("節點名稱") },
                placeholder = { Text("例如：我的家裡伺服器") },
                leadingIcon = { Icon(Icons.Default.Label, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("伺服器 IP / 網址") },
                placeholder = { Text("例如：example.com 或 1.2.3.4") },
                leadingIcon = { Icon(Icons.Default.Language, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(14.dp))

            Row {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("連接埠 (Port)") },
                    placeholder = { Text("443") },
                    leadingIcon = { Icon(Icons.Default.Numbers, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(top = 18.dp)) {
                    Text("連線時透過此", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("端口連線", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("預設 443", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密鑰 / 密碼 / UUID") },
                placeholder = { Text("伺服器設定的 Password 或 Secret") },
                leadingIcon = { Icon(Icons.Default.Key, null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = ::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    if (nodeId > 0) "儲存修改" else "儲存節點",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}