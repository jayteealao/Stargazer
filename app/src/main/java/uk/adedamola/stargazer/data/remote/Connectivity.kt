package uk.adedamola.stargazer.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/** Whether the phone has a network that reaches the internet. Tests force it offline. */
interface Connectivity {
    val online: StateFlow<Boolean>
    fun isOnline(): Boolean = online.value
}

/**
 * The platform's view of the internet: a network with the internet capability. Not "validated",
 * which emulators and captive networks often lack; a request that fails with an IOException is
 * treated as offline by its caller anyway.
 */
class AndroidConnectivity(context: Context) : Connectivity {

    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val networks = Collections.synchronizedSet(mutableSetOf<Network>())
    private val _online = MutableStateFlow(initiallyOnline())
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networks += network
                _online.value = true
            }

            override fun onLost(network: Network) {
                networks -= network
                _online.value = networks.isNotEmpty()
            }
        })
    }

    private fun initiallyOnline(): Boolean {
        val active = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(active)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
