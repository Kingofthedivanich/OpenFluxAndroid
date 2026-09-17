package io.github.p1neapplexpress.openflux.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.p1neapplexpress.openflux.IUnifiedService
import io.github.p1neapplexpress.openflux.data.Tunnel
import io.github.p1neapplexpress.openflux.data.TunnelRepository
import io.github.p1neapplexpress.openflux.data.TunnelState
import io.github.p1neapplexpress.openflux.data.TunnelViewType
import io.github.p1neapplexpress.openflux.event.AppEvent
import io.github.p1neapplexpress.openflux.event.EventBus
import io.github.p1neapplexpress.openflux.service.SocksVpnService
import io.github.p1neapplexpress.openflux.util.Logx
import io.github.p1neapplexpress.openflux.vpn.VPNConfig
import io.github.p1neapplexpress.openflux.vpn.VpnIntentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TunnelsViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "TunnelsViewModel"
        private const val POLL_MS = 250L
        private const val BIND_TIMEOUT_MS = 5_000L

        // The service gives OpenFlux 45 s to open its SOCKS5 port and reports failures itself.
        private const val TRANSPORT_TIMEOUT_MS = 50_000L
        private const val TUN2SOCKS_TIMEOUT_MS = 10_000L
    }

    private val repo = TunnelRepository(app)

    @Volatile
    private var service: IUnifiedService? = null
    private var bound = false
    private var activeTunnelData: Tunnel? = null

    // Starts and teardowns run one after another so a quick stop/start cannot
    // unbind or stop the session that was just started.
    private var startJob: Job? = null
    private var teardownJob: Job? = null

    @Volatile
    private var bindRequested = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IUnifiedService.Stub.asInterface(binder)
            bound = true
            Logx.d(TAG, "service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            Logx.d(TAG, "service disconnected")
            if (_active.value !is TunnelState.Idle) {
                _active.value = TunnelState.Idle
                stopUptimeCounter()
                refresh()
            }
        }
    }

    private val _tunnels = MutableStateFlow<List<TunnelViewType>>(emptyList())
    val tunnels: StateFlow<List<TunnelViewType>> = _tunnels.asStateFlow()

    private val _active = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val active: StateFlow<TunnelState> = _active.asStateFlow()

    private val _uptimeSeconds = MutableStateFlow(0L)
    val uptimeSeconds: StateFlow<Long> = _uptimeSeconds.asStateFlow()

    private val _selected = MutableStateFlow<Tunnel?>(null)
    val selected: StateFlow<Tunnel?> = _selected.asStateFlow()

    val selectedTunnelId: Long? get() = _selected.value?.id

    private var uptimeJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            EventBus.events.collect { ev ->
                if (ev is AppEvent.NativeProcessExited && _active.value.isActive) fail(ev.message)
            }
        }
    }

    fun startCurrent() {
        val tunnel = _active.value.tunnel
            ?: _selected.value
            ?: repo.getSelected()
            ?: return
        startTunnel(tunnel)
    }

    fun startTunnel(tunnel: Tunnel) {
        val running = _active.value
        if (running is TunnelState.Running && running.tunnel == tunnel) return
        if (running.isActive) stop()
        val pendingTeardown = teardownJob

        repo.setSelectedId(tunnel.id)
        _selected.value = tunnel
        _active.value = TunnelState.Connecting(tunnel)

        startJob = CoroutineScope(Dispatchers.IO).launch {
            pendingTeardown?.join()
            _active.value = TunnelState.Connecting(tunnel)

            val ctx = getApplication<Application>()
            val intent = VpnIntentFactory.build(ctx, VPNConfig(name = tunnel.name))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            bindRequested = true
            ctx.bindService(
                Intent(ctx, SocksVpnService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
            activeTunnelData = tunnel

            if (awaitBinding() == null) {
                fail("Service not connected")
                return@launch
            }
            Logx.i(TAG, "service bound, starting transport")

            _active.value = TunnelState.StartingTransport(tunnel)
            try {
                service?.startOpenFluxNative(
                    tunnel.transportType,
                    tunnel.transportConnPayload.toTypedArray(),
                    tunnel.encryptionKey,
                )
            } catch (e: Exception) {
                fail("Transport failed: ${e.message}")
                return@launch
            }

            awaitService(TRANSPORT_TIMEOUT_MS, "Transport did not start") { it.isFServiceRunning() }
                ?.let { fail(it); return@launch }

            Logx.i(TAG, "starting tun2socks")
            _active.value = TunnelState.StartingTun2Socks(tunnel)
            try {
                service?.startTun2Socks()
            } catch (e: Exception) {
                fail("tun2socks failed: ${e.message}")
                return@launch
            }

            awaitService(TUN2SOCKS_TIMEOUT_MS, "tun2socks did not start") { it.isVpnRunning() }
                ?.let { fail(it); return@launch }

            _active.value = TunnelState.Running(tunnel)
            startUptimeCounter()
            refresh()
        }
    }

    /** Polls [ready] until it holds; returns null on success or the error to show. */
    private suspend fun awaitService(
        timeoutMs: Long,
        timeoutMessage: String,
        ready: (IUnifiedService) -> Boolean,
    ): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val s = service ?: return "Service not connected"
            try {
                s.nativeError()?.let { return it }
                if (ready(s)) return null
            } catch (e: Exception) {
                Logx.e(TAG, "service call failed", e)
            }
            delay(POLL_MS)
        }
        return timeoutMessage
    }

    fun stop() {
        Logx.i(TAG, "stop()")
        teardown(TunnelState.Idle)
    }

    private fun fail(message: String) {
        // The start loop and the exit event can both report the same failure.
        if (startJob == null && teardownJob?.isActive == true) return
        Logx.e(TAG, message)
        teardown(TunnelState.Error(message))
    }

    private fun teardown(finalState: TunnelState) {
        startJob?.cancel()
        startJob = null
        val previous = teardownJob
        teardownJob = CoroutineScope(Dispatchers.IO).launch {
            previous?.join()
            if (bindRequested) {
                // A cancelled start may still be binding; the service must be stopped anyway.
                val s = awaitBinding()
                try {
                    s?.stopOpenFluxNative()
                    s?.stopVpn()
                } catch (e: Exception) {
                    Logx.e(TAG, "stop failed", e)
                }
                try { getApplication<Application>().unbindService(connection) } catch (_: Exception) {}
                bindRequested = false
            }
            bound = false
            service = null
            activeTunnelData = null
            _active.value = finalState
            stopUptimeCounter()
            refresh()
        }
    }

    private suspend fun awaitBinding(): IUnifiedService? {
        var waited = 0L
        while (service == null && waited < BIND_TIMEOUT_MS) {
            delay(50)
            waited += 50
        }
        return service
    }

    fun refresh() {
        val list = repo.load()
        val running = (_active.value as? TunnelState.Running)?.tunnel
        _tunnels.value = list.map { TunnelViewType(it, enabled = it == running) }
        _selected.value = repo.getSelected()
    }

    fun selectTunnel(tunnel: Tunnel) {
        if (_active.value.isActive) {
            stop()
        }
        repo.setSelectedId(tunnel.id)
        _selected.value = tunnel
    }

    fun addTunnel(tunnel: Tunnel) {
        val current = repo.load().toMutableList()
        if (current.none { it.id == tunnel.id }) {
            current.add(tunnel)
            repo.save(current)
            refresh()
        }
    }

    fun removeTunnel(tunnel: Tunnel) {
        if (_active.value.tunnel == tunnel) stop()
        val current = repo.load().toMutableList()
        current.removeAll { it.id == tunnel.id }
        repo.save(current)
        refresh()
    }

    fun updateTunnel(old: Tunnel, new: Tunnel) {
        val current = repo.load().toMutableList()
        val idx = current.indexOfFirst { it.id == old.id }
        if (idx < 0) return
        if (_active.value.tunnel == old) stop()
        current[idx] = new
        repo.save(current)
        refresh()
    }

    private fun startUptimeCounter() {
        uptimeJob?.cancel()
        _uptimeSeconds.value = 0L
        uptimeJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            while (isActive) {
                _uptimeSeconds.value = (System.currentTimeMillis() - startedAt) / 1000L
                delay(1_000L)
            }
        }
    }

    private fun stopUptimeCounter() {
        uptimeJob?.cancel()
        uptimeJob = null
        _uptimeSeconds.value = 0L
    }

    override fun onCleared() {
        super.onCleared()
        startJob?.cancel()
        stopUptimeCounter()
        try { getApplication<Application>().unbindService(connection) } catch (_: Exception) {}
    }
}
