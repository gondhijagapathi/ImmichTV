package com.jagapathi.immichtv.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class LocalNetworkMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    /**
     * The TV's IPv4 address on its Wi-Fi or Ethernet network, or null while it has neither.
     * Only those transports are considered so VPN and virtual interfaces, which a phone on the
     * same Wi-Fi can't reach, are never picked. Updates as networks come and go, e.g. when
     * Wi-Fi connects a few seconds after the TV boots.
     */
    val ipv4Address: Flow<String?> = callbackFlow {
        val addresses = ConcurrentHashMap<Network, String>()
        fun publish() = trySend(addresses.values.minOrNull())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                val address = linkProperties.linkAddresses
                    .map { it.address }
                    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                    ?.hostAddress
                if (address != null) addresses[network] = address else addresses.remove(network)
                publish()
            }

            override fun onLost(network: Network) {
                addresses.remove(network)
                publish()
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            // A home LAN without internet access is still fine for talking to the phone.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        publish()
        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
