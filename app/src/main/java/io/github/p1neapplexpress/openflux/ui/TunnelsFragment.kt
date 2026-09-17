package io.github.p1neapplexpress.openflux.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity.RESULT_OK
import android.net.VpnService
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import io.github.p1neapplexpress.openflux.R
import io.github.p1neapplexpress.openflux.data.Tunnel
import io.github.p1neapplexpress.openflux.data.TunnelState
import io.github.p1neapplexpress.openflux.event.AppEvent
import io.github.p1neapplexpress.openflux.ui.widget.AuroraView
import io.github.p1neapplexpress.openflux.ui.widget.PulseRingsView
import io.github.p1neapplexpress.openflux.util.toUptimeHms
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class TunnelsFragment : BaseFragment() {

    private val vm: TunnelsViewModel by activityViewModels()

    private lateinit var aurora: AuroraView
    private lateinit var pulseRings: PulseRingsView
    private lateinit var ringOuter: View
    private lateinit var ringMid: View
    private lateinit var connectButton: View
    private lateinit var powerIcon: ImageView
    private lateinit var configSelector: View
    private lateinit var configDot: View
    private lateinit var tunnelName: TextView
    private lateinit var chevron: ImageView
    private lateinit var statusText: TextView
    private lateinit var uptimeText: TextView

    private var rotationAnim: ObjectAnimator? = null
    private var breathAnim: ObjectAnimator? = null
    private var currentVisualState: TunnelState? = null
    private var popup: PopupWindow? = null

    /** Tunnel to start once the VPN permission dialog returns; null starts the selected one. */
    private var pendingStart: Tunnel? = null

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val tunnel = pendingStart
        pendingStart = null
        if (result.resultCode != RESULT_OK) {
            Toast.makeText(requireContext(), R.string.vpn_permission_required, Toast.LENGTH_LONG).show()
        } else if (tunnel != null) {
            vm.startTunnel(tunnel)
        } else {
            vm.startCurrent()
        }
    }

    private val qrScanner = registerForActivityResult(ScanQRCode()) { result ->
        val raw = (result as? QRResult.QRSuccess)?.content?.rawValue
            ?: return@registerForActivityResult
        runCatching { qrJson.decodeFromString<Tunnel>(raw) }
            .onSuccess { vm.addTunnel(it); requestVpnAndStart(it) }
            .onFailure {
                Toast.makeText(requireContext(), R.string.qr_scan_failed, Toast.LENGTH_LONG).show()
            }
    }

    // QR codes may come from newer app versions with fields this one doesn't know.
    private val qrJson = Json { ignoreUnknownKeys = true }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_tunnels, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        aurora = view.findViewById(R.id.aurora)
        pulseRings = view.findViewById(R.id.pulseRings)
        ringOuter = view.findViewById(R.id.ringOuter)
        ringMid = view.findViewById(R.id.ringMid)
        connectButton = view.findViewById(R.id.connectButton)
        powerIcon = view.findViewById(R.id.powerIcon)
        configSelector = view.findViewById(R.id.configSelector)
        configDot = view.findViewById(R.id.configDot)
        tunnelName = view.findViewById(R.id.tunnelName)
        chevron = view.findViewById(R.id.chevron)
        statusText = view.findViewById(R.id.statusText)
        uptimeText = view.findViewById(R.id.uptimeText)

        connectButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            // Stopping also cancels a start that is still waiting for the transport.
            if (vm.active.value.isActive) vm.stop() else requestVpnAndStart()
        }

        configSelector.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showConfigDropdown(it)
        }

        view.findViewById<View>(R.id.switchButton).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, AddTunFragment.new())
                .addToBackStack("switch")
                .commit()
        }
        view.findViewById<View>(R.id.addButton).setOnClickListener {
            qrScanner.launch(null)
        }

        observe()
    }

    private fun requestVpnAndStart(tunnel: Tunnel? = null) {
        val intent = VpnService.prepare(requireActivity())
        when {
            intent != null -> {
                pendingStart = tunnel
                vpnPermission.launch(intent)
            }
            tunnel != null -> vm.startTunnel(tunnel)
            else -> vm.startCurrent()
        }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.active.collect { applyState(it) } }
                launch { vm.uptimeSeconds.collect { renderUptime(it) } }
                launch { vm.selected.collect { renderSelected(it) } }
            }
        }
    }

    private fun renderSelected(tunnel: Tunnel?) {
        tunnelName.text = tunnel?.name ?: getString(R.string.no_configs)
        configDot.background.setTint(
            ContextCompat.getColor(
                requireContext(),
                if (tunnel != null) R.color.state_idle else R.color.state_error
            )
        )
    }

    

    private fun showConfigDropdown(anchor: View) {
        val tunnels = vm.tunnels.value.map { it.tunnel }
        if (tunnels.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_configs, Toast.LENGTH_SHORT).show()
            return
        }

        val inflater = LayoutInflater.from(requireContext())
        val content = inflater.inflate(R.layout.dropdown_configs, null)
        val items = content.findViewById<LinearLayout>(R.id.dropdown_items)
        val selectedId = vm.selectedTunnelId

        for (tunnel in tunnels) {
            val row = inflater.inflate(R.layout.item_dropdown_config, items, false)
            row.findViewById<TextView>(R.id.item_name).text = tunnel.name
            val check = row.findViewById<ImageView>(R.id.item_check)
            check.isVisible = tunnel.id == selectedId

            row.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                vm.selectTunnel(tunnel)
                popup?.dismiss()
            }

            row.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showItemContextMenu(it, tunnel)
                true
            }

            items.addView(row)
        }

        val pw = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.bg_dropdown_menu)
            )
        }

        popup = pw

        
        content.alpha = 0f
        content.translationY = -12f
        content.scaleY = 0.95f

        pw.showAsDropDown(anchor, 0, 8)

        content.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        
        chevron.animate().rotation(180f).setDuration(180).start()
        pw.setOnDismissListener {
            chevron.animate().rotation(0f).setDuration(180).start()
            popup = null
        }
    }

    

    private fun showItemContextMenu(anchor: View, tunnel: Tunnel) {
        val inflater = LayoutInflater.from(requireContext())
        val menuView = inflater.inflate(R.layout.popup_item_menu, null)

        val menu = PopupWindow(
            menuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.bg_menu_popup)
            )
        }

        menuView.findViewById<View>(R.id.menu_edit).setOnClickListener {
            menu.dismiss()
            popup?.dismiss()
            parentFragmentManager.beginTransaction()
                .replace(R.id.main, AddTunFragment.edit(tunnel))
                .addToBackStack("edit")
                .commit()
        }

        menuView.findViewById<View>(R.id.menu_delete).setOnClickListener {
            menu.dismiss()
            confirmDelete(tunnel)
        }

        menuView.alpha = 0f
        menuView.translationY = -8f
        menuView.scaleX = 0.96f
        menuView.scaleY = 0.96f

        menu.showAsDropDown(anchor, 0, 4)

        menuView.animate()
            .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(160)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    private fun confirmDelete(tunnel: Tunnel) {
        val active = vm.active.value
        if (active.isActive && active.tunnel == tunnel) {
            Toast.makeText(requireContext(), R.string.cannot_delete_active, Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_config_title)
            .setMessage(R.string.delete_config_msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                vm.removeTunnel(tunnel)
                popup?.dismiss()
                Toast.makeText(requireContext(), R.string.config_deleted, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun applyState(state: TunnelState) {
        if (state == currentVisualState) return
        currentVisualState = state

        val color = state.color
        aurora.setStateColor(color)

        when (state) {
            is TunnelState.Idle -> {
                statusText.text = getString(R.string.tap_to_connect)
                statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                crossFadeStatus()
                aurora.setIntensity(0.4f)
                pulseRings.stop()
                stopRotation()
                startBreath()
                animateIcon(scale = 1f, alpha = 0.92f)
                hideUptime()
            }

            is TunnelState.Connecting,
            is TunnelState.StartingTransport,
            is TunnelState.StartingTun2Socks -> {
                val label = when (state) {
                    is TunnelState.Connecting -> getString(R.string.connecting)
                    is TunnelState.StartingTransport -> getString(R.string.starting_transport)
                    is TunnelState.StartingTun2Socks -> getString(R.string.starting_tsocks)
                    else -> ""
                }
                statusText.text = label
                statusText.setTextColor(color)
                crossFadeStatus()
                aurora.setIntensity(0.75f)
                startRotation()
                pulseRings.setColor(color)
                pulseRings.start(color, intervalMs = 1800L)
                stopBreath()
                animateIcon(scale = 0.94f, alpha = 0.7f)
                hideUptime()
            }

            is TunnelState.Running -> {
                statusText.text = getString(R.string.running)
                statusText.setTextColor(color)
                crossFadeStatus()
                aurora.setIntensity(1f)
                stopRotation()
                pulseRings.setColor(color)
                pulseRings.start(color, intervalMs = 1400L)
                startBreath()
                animateIcon(scale = 1.08f, alpha = 1f)
                popButton()
                showUptime()
            }

            is TunnelState.Error -> {
                statusText.text = state.message
                statusText.setTextColor(color)
                crossFadeStatus()
                aurora.setIntensity(0.9f)
                pulseRings.stop()
                stopRotation()
                stopBreath()
                animateIcon(scale = 1f, alpha = 1f)
                shake()
                hideUptime()
            }
        }
    }

    private fun renderUptime(seconds: Long) {
        if (seconds <= 0L) {
            hideUptime(); return
        }
        val text = seconds.toUptimeHms()
        if (uptimeText.text != text) {
            uptimeText.text = text
            uptimeText.animate().cancel()
            uptimeText.scaleX = 0.96f; uptimeText.scaleY = 0.96f
            uptimeText.animate().scaleX(1f).scaleY(1f).setDuration(180L)
                .setInterpolator(OvershootInterpolator(1.4f)).start()
        }
    }

    private fun crossFadeStatus() {
        statusText.animate().cancel()
        statusText.alpha = 0.6f
        statusText.animate().alpha(1f).setDuration(260L)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun animateIcon(scale: Float, alpha: Float) {
        powerIcon.animate().cancel()
        powerIcon.animate().scaleX(scale).scaleY(scale).alpha(alpha)
            .setDuration(320L).setInterpolator(AccelerateDecelerateInterpolator()).start()
    }

    private fun popButton() {
        connectButton.animate().cancel()
        connectButton.scaleX = 0.94f; connectButton.scaleY = 0.94f
        connectButton.animate().scaleX(1f).scaleY(1f).setDuration(420L)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
    }

    private fun shake() {
        val props = PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, -14f, 14f, -10f, 10f, -4f, 4f, 0f)
        ObjectAnimator.ofPropertyValuesHolder(connectButton, props).apply {
            duration = 520L
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startRotation() {
        if (rotationAnim?.isRunning == true) return
        rotationAnim = ObjectAnimator.ofFloat(ringOuter, View.ROTATION, 0f, 360f).apply {
            duration = 4200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopRotation() {
        rotationAnim?.cancel()
        rotationAnim = null
        ringOuter.rotation = 0f
    }

    private fun startBreath() {
        if (breathAnim?.isRunning == true) return
        breathAnim = ObjectAnimator.ofPropertyValuesHolder(
            ringOuter,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.02f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.02f),
        ).apply {
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopBreath() {
        breathAnim?.cancel()
        breathAnim = null
        ringOuter.scaleX = 1f
        ringOuter.scaleY = 1f
    }

    private fun showUptime() {
        if (uptimeText.alpha > 0.05f) return
        uptimeText.translationY = 16f
        uptimeText.animate().alpha(1f).translationY(0f)
            .setDuration(500L).setInterpolator(OvershootInterpolator(1.2f)).start()
    }

    private fun hideUptime() {
        if (uptimeText.alpha < 0.05f) return
        uptimeText.animate().alpha(0f).setDuration(200L).start()
    }

    override fun onDestroyView() {
        rotationAnim?.cancel()
        breathAnim?.cancel()
        pulseRings.stop()
        popup?.dismiss()
        popup = null
        super.onDestroyView()
    }

    override fun onNewEvent(ev: AppEvent) = Unit

    companion object {
        fun new() = TunnelsFragment()
    }
}
