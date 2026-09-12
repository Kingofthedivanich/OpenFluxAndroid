package io.github.p1neapplexpress.openflux.service

import android.annotation.SuppressLint
import android.content.Intent
import android.os.IBinder
import io.github.p1neapplexpress.openflux.IUnifiedService
import io.github.p1neapplexpress.openflux.event.AppEvent
import io.github.p1neapplexpress.openflux.event.EventBus
import io.github.p1neapplexpress.openflux.NativeBridge
import io.github.p1neapplexpress.openflux.util.Constants
import io.github.p1neapplexpress.openflux.util.Logx

@SuppressLint("VpnServicePolicy")
class SocksVpnService : android.net.VpnService() {

    companion object {
        private const val TAG = "SocksVpnService"
    }

    private lateinit var vpn: VpnServiceController
    private lateinit var supervisor: NativeProcessSupervisor
    private lateinit var tun2socks: Tun2SocksLauncher
    private lateinit var notifications: VpnNotificationManager

    private var lastIntent: Intent? = null

    private val binder = object : IUnifiedService.Stub() {
        override fun isVpnRunning(): Boolean = vpn.isRunning.get()
        override fun stopVpn() = stopEverything()
        override fun isFServiceRunning(): Boolean = supervisor.isConnected
        override fun stopOpenFluxNative() = supervisor.stop()

        override fun startOpenFluxNative(transport: String?, args: Array<String>) {
            transport ?: return
            supervisor.start(transport, args.toList())
        }

        override fun startTun2Socks() {
            synchronized(this@SocksVpnService) {
                val fd = vpn.fd
                if (fd <= 0) {
                    Logx.e(TAG, "no tun fd; aborting tun2socks start")
                    return
                }
                val i = lastIntent ?: run {
                    Logx.e(TAG, "no lastIntent; aborting tun2socks start")
                    return
                }

                val ok = tun2socks.start(
                    fd = fd,
                    server = i.getStringExtra(Constants.INTENT_SERVER) ?: "127.0.0.1",
                    port = i.getIntExtra(Constants.INTENT_PORT, 1080),
                    username = i.getStringExtra(Constants.INTENT_USERNAME),
                    password = i.getStringExtra(Constants.INTENT_PASSWORD),
                    dns = i.getStringExtra(Constants.INTENT_DNS) ?: "8.8.8.8",
                    dnsPort = i.getIntExtra(Constants.INTENT_DNS_PORT, 53),
                    ipv6 = i.getBooleanExtra(Constants.INTENT_IPV6_PROXY, false),
                    udpgw = i.getStringExtra(Constants.INTENT_UDP_GW),
                )

                if (ok) {
                    vpn.isRunning.set(true)
                    notifications.startSpeedUpdates()
                    EventBus.dispatch(AppEvent.LogMessage("[I] tun2socks running"))
                    Logx.i(TAG, "tun2socks running")
                } else {
                    Logx.e(TAG, "tun2socks failed")
                    EventBus.dispatch(AppEvent.LogMessage("[E] tun2socks failed"))
                    stopEverything()
                }
            }
        }

        override fun getFd(): Int = vpn.fd
    }

    override fun onCreate() {
        super.onCreate()
        NativeBridge.ensureLoaded(applicationContext)
        vpn = VpnServiceController(this)
        supervisor = NativeProcessSupervisor(applicationContext)
        tun2socks = Tun2SocksLauncher(applicationContext)
        notifications = VpnNotificationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_STICKY
        lastIntent = intent
        notifications.startForeground()

        if (vpn.isConfigured()) {
            Logx.d(TAG, "VPN already configured, ignoring")
            return START_STICKY
        }

        vpn.configure(intent)
        EventBus.dispatch(AppEvent.LogMessage("[S] VPN configured"))
        Logx.i(TAG, "VPN configured")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onRevoke() {
        Logx.w(TAG, "onRevoke")
        stopEverything()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    private fun stopEverything() {
        Logx.i(TAG, "stopEverything")
        notifications.stopSpeedUpdates()
        runCatching { tun2socks.stop() }
        runCatching { supervisor.stop() }
        runCatching { vpn.stop() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
