package com.chargehub.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chargehub.app.data.model.ChargingStation
import com.chargehub.app.data.model.GeoPoint
import com.chargehub.app.ui.directions.DirectionsScreen
import com.chargehub.app.ui.nearby.NearbyStationsScreen

private enum class AppDestination(val label: String, val glyph: String) {
    HOME("Home", "⌂"), NEARBY("Nearby", "⌁"), OFFERS("Offers", "✦"), WALLET("Wallet", "₹")
}

private data class DirectionsRequest(val station: ChargingStation, val origin: GeoPoint?)

@Composable
fun ChargeHubRoot() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("chargehub_session", Context.MODE_PRIVATE) }
    var signedIn by remember { mutableStateOf(preferences.getBoolean("signed_in", false)) }
    var displayName by remember { mutableStateOf(preferences.getString("display_name", "Driver").orEmpty()) }

    if (!signedIn) {
        AccountGate { name ->
            displayName = name.ifBlank { "Driver" }
            preferences.edit().putBoolean("signed_in", true).putString("display_name", displayName).apply()
            signedIn = true
        }
    } else {
        ChargeHubDashboard(
            displayName = displayName,
            onSignOut = {
                preferences.edit().clear().apply()
                signedIn = false
            }
        )
    }
}

@Composable
private fun AccountGate(onContinue: (String) -> Unit) {
    var signUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val ready = email.contains("@") && password.length >= 6 && (!signUp || name.isNotBlank())

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("CHARGEHUB", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            if (signUp) "Create your charging account" else "Charge with confidence",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        Text("Find, pay for and manage EV charging in one place.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        if (signUp) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            password,
            { password = it },
            Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = { onContinue(name) }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text(if (signUp) "Create account" else "Log in")
        }
        TextButton(onClick = { signUp = !signUp }, modifier = Modifier.fillMaxWidth()) {
            Text(if (signUp) "Already have an account? Log in" else "New to ChargeHub? Sign up")
        }
        Text(
            "Your password is never stored on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Connect the account service before releasing this screen to production.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChargeHubDashboard(displayName: String, onSignOut: () -> Unit) {
    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var directionsRequest by remember { mutableStateOf<DirectionsRequest?>(null) }
    val request = directionsRequest

    if (request != null) {
        DirectionsScreen(station = request.station, origin = request.origin, onBack = { directionsRequest = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (destination == AppDestination.HOME) "Good to see you, $displayName" else destination.label) },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } }
            )
        },
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Text(item.glyph) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (destination) {
            AppDestination.HOME -> HomeContent(
                padding = padding,
                onNearby = { destination = AppDestination.NEARBY },
                onOffers = { destination = AppDestination.OFFERS },
                onWallet = { destination = AppDestination.WALLET }
            )
            AppDestination.NEARBY -> NearbyStationsScreen(
                onShowDirections = { station, origin -> directionsRequest = DirectionsRequest(station, origin) }
            )
            AppDestination.OFFERS -> OffersContent(padding)
            AppDestination.WALLET -> WalletContent(padding)
        }
    }
}

@Composable
private fun HomeContent(
    padding: PaddingValues,
    onNearby: () -> Unit,
    onOffers: () -> Unit,
    onWallet: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Ready for the road?", style = MaterialTheme.typography.headlineSmall)
            Text("Live stations, transparent payment and your charging essentials.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { FeatureCard("Nearby charging", "Find stations around your live location", "Explore", onNearby) }
        item { FeatureCard("ChargeHub wallet", "Use credit, gift cards and offers at checkout", "View wallet", onWallet) }
        item { FeatureCard("Offers for your next charge", "Rewards are applied securely before payment", "View offers", onOffers) }
    }
}

@Composable
private fun FeatureCard(title: String, detail: String, action: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun OffersContent(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Offers are personalised by your ChargeHub account.", style = MaterialTheme.typography.titleMedium)
        Text("Eligible discounts are calculated by the server at checkout, so the final amount is always trustworthy.")
    }
}

@Composable
private fun WalletContent(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ChargeHub wallet", style = MaterialTheme.typography.headlineSmall)
        Text("Your available credit, gift cards and transaction history will appear here once the account service is connected.")
    }
}
