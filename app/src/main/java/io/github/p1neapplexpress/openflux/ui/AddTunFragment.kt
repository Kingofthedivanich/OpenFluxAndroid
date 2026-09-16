package io.github.p1neapplexpress.openflux.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.google.android.material.switchmaterial.SwitchMaterial
import io.github.p1neapplexpress.openflux.R
import io.github.p1neapplexpress.openflux.data.TransportType
import io.github.p1neapplexpress.openflux.data.Tunnel
import io.github.p1neapplexpress.openflux.event.AppEvent
import io.github.p1neapplexpress.openflux.util.dpToPx
import kotlinx.serialization.json.Json
import kotlin.random.Random

class AddTunFragment : BaseFragment() {

    companion object {
        private const val ARG_EDIT_JSON = "edit_json"

        fun new() = AddTunFragment()

        fun edit(tunnel: Tunnel): AddTunFragment = AddTunFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_EDIT_JSON, Json.encodeToString(Tunnel.serializer(), tunnel))
            }
        }
    }

    private val vm: TunnelsViewModel by activityViewModels()
    private var transport = TransportType.yandex
    private var debug = false
    private var legacyCodec = false
    private var editing: Tunnel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = arguments?.getString(ARG_EDIT_JSON)
        if (raw != null) {
            editing = runCatching { Json.decodeFromString(Tunnel.serializer(), raw) }.getOrNull()
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        i.inflate(R.layout.fragment_add_tun, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val transportLayout = view.findViewById<View>(R.id.select_transport_layout)
        val maxContainer = view.findViewById<View>(R.id.maxContainer)
        val yandexContainer = view.findViewById<View>(R.id.yandexUrlContainer)
        val transportLabel = view.findViewById<TextView>(R.id.selectedTransport)
        val debugLabel = view.findViewById<TextView>(R.id.selectedDebug)
        val debugSwitch = view.findViewById<SwitchMaterial>(R.id.debugSwitch)
        val legacyCodecLabel = view.findViewById<TextView>(R.id.selectedLegacyCodec)
        val legacyCodecSwitch = view.findViewById<SwitchMaterial>(R.id.legacyCodecSwitch)
        val docUrl = view.findViewById<TextView>(R.id.documentUrl)
        val maxToken = view.findViewById<TextView>(R.id.maxToken)
        val maxUid = view.findViewById<TextView>(R.id.maxUserId)
        val name = view.findViewById<TextView>(R.id.name)
        val save = view.findViewById<Button>(R.id.saveButton)

        // ─── Заполнение при редактировании ───
        editing?.let { t ->
            name.setText(t.name)
            transport = TransportType.from(t.transportType)

            when (transport) {
                TransportType.yandex -> {
                    maxContainer.isVisible = false
                    yandexContainer.isVisible = true
                    transportLabel.text = getString(R.string.yandex_docs_backend)
                    docUrl.setText(argValue(t.transportConnPayload, "--url"))
                }
                TransportType.vyandex -> {
                    maxContainer.isVisible = false
                    yandexContainer.isVisible = true
                    transportLabel.text = getString(R.string.vyandex_backend)
                    docUrl.setText(argValue(t.transportConnPayload, "--url"))
                }
                TransportType.max -> {
                    maxContainer.isVisible = true
                    yandexContainer.isVisible = false
                    transportLabel.text = getString(R.string.max_messenger_backend)
                    maxToken.setText(argValue(t.transportConnPayload, "--maxToken"))
                    maxUid.setText(argValue(t.transportConnPayload, "--maxUid"))
                }
                TransportType.cupsonline -> {
                    maxContainer.isVisible = false
                    yandexContainer.isVisible = true
                    transportLabel.text = getString(R.string.cupsonline_backend)
                    docUrl.setText(argValue(t.transportConnPayload, "--url"))
                }
                TransportType.mailru -> {
                    maxContainer.isVisible = false
                    yandexContainer.isVisible = true
                    transportLabel.text = getString(R.string.mailru_backend)
                    docUrl.setText(argValue(t.transportConnPayload, "--url"))
                }
            }

            debug = t.transportConnPayload.contains("--debug")
            debugSwitch.isChecked = debug
            debugLabel.text = getString(if (debug) R.string.on else R.string.off)

            legacyCodec = argValue(t.transportConnPayload, "--codec") == "legacy"
            legacyCodecSwitch.isChecked = legacyCodec
            legacyCodecLabel.text = getString(if (legacyCodec) R.string.on else R.string.off)

            save.text = getString(R.string.action_edit)
        }

        transportLayout.setOnClickListener {
            it.showTransportDropdown(
                onYandex = {
                    transport = TransportType.yandex
                    maxContainer.isVisible = false
                    yandexContainer.isVisible = true
                    transportLabel.text = getString(R.string.yandex_docs_backend)
                },
                onVyandex = {
                    transport = TransportType.vyandex
                    maxContainer.isVisible = false
                    yandexContainer.isVisible = true
                    transportLabel.text = getString(R.string.vyandex_backend)
                },
                onMax = {
                    transport = TransportType.max
                    maxContainer.isVisible = true
                    yandexContainer.isVisible = false
                    transportLabel.text = getString(R.string.max_messenger_backend)
                },
                onCupsonline = {
                    transport = TransportType.cupsonline
                    maxContainer.isVisible = false
                    yandexContainer.isVisible = true
                    transportLabel.text = getString(R.string.cupsonline_backend)
                },
                onMailru = {
                    transport = TransportType.mailru
                    maxContainer.isVisible = false
                    yandexContainer.isVisible = true
                    transportLabel.text = getString(R.string.mailru_backend)
                },
            )
        }

        debugSwitch.setOnCheckedChangeListener { _, checked ->
            debug = checked
            debugLabel.text = getString(if (checked) R.string.on else R.string.off)
        }

        legacyCodecSwitch.setOnCheckedChangeListener { _, checked ->
            legacyCodec = checked
            legacyCodecLabel.text = getString(if (checked) R.string.on else R.string.off)
        }

        save.setOnClickListener {
            val n = name.text.trim().toString()
            if (n.isEmpty()) {
                Toast.makeText(requireContext(), R.string.name_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newTunnel = createTunnel(
                id = editing?.id ?: Random(System.currentTimeMillis()).nextLong(),
                name = n,
                docUrl = docUrl.text.trim().toString(),
                maxToken = maxToken.text.trim().toString(),
                maxUid = maxUid.text.trim().toString(),
            ) ?: return@setOnClickListener

            val old = editing
            if (old != null) {
                vm.updateTunnel(old, newTunnel)
                Toast.makeText(requireContext(), R.string.config_saved, Toast.LENGTH_SHORT).show()
            } else {
                vm.addTunnel(newTunnel)
            }

            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        if (editing == null) {
            transportLabel.text = getString(R.string.yandex_docs_backend)
            debugLabel.text = getString(R.string.off)
            legacyCodecLabel.text = getString(R.string.off)
        }
    }

    private fun argValue(payload: List<String>, key: String): String {
        val idx = payload.indexOf(key)
        return if (idx >= 0 && idx + 1 < payload.size) payload[idx + 1] else ""
    }

    private fun createTunnel(
        id: Long,
        name: String,
        docUrl: String,
        maxToken: String,
        maxUid: String,
    ): Tunnel? {
        val payload = when (transport) {
            TransportType.yandex -> {
                if (docUrl.isEmpty()) return null
                buildList {
                    add("--role"); add("client"); add("--transport"); add("yandex")
                    add("--url"); add(docUrl)
                    if (legacyCodec) { add("--codec"); add("legacy") }
                    if (debug) add("--debug")
                }
            }
            TransportType.vyandex -> {
                if (docUrl.isEmpty()) return null
                buildList {
                    add("--role"); add("client"); add("--transport"); add("vyandex")
                    add("--url"); add(docUrl)
                    if (legacyCodec) { add("--codec"); add("legacy") }
                    if (debug) add("--debug")
                }
            }
            TransportType.max -> {
                if (maxToken.isEmpty() || maxUid.isEmpty()) return null
                buildList {
                    add("--role"); add("client"); add("--transport"); add("oneme")
                    add("--maxToken"); add(maxToken)
                    add("--maxUid"); add(maxUid)
                    if (legacyCodec) { add("--codec"); add("legacy") }
                    if (debug) add("--debug")
                }
            }
            TransportType.cupsonline -> {
                if (docUrl.isEmpty()) return null
                buildList {
                    add("--role"); add("client"); add("--transport"); add("cupsonline")
                    add("--url"); add(docUrl)
                    if (legacyCodec) { add("--codec"); add("legacy") }
                    if (debug) add("--debug")
                }
            }
            TransportType.mailru -> {
                if (docUrl.isEmpty()) return null
                buildList {
                    add("--role"); add("client"); add("--transport"); add("mailru")
                    add("--url"); add(docUrl)
                    if (legacyCodec) { add("--codec"); add("legacy") }
                    if (debug) add("--debug")
                }
            }
        }
        return Tunnel(
            id = id,
            name = name,
            transportType = transport.name,
            transportConnPayload = payload,
        )
    }

    override fun onNewEvent(ev: AppEvent) = Unit

    private fun View.showTransportDropdown(
        onYandex: () -> Unit,
        onVyandex: () -> Unit,
        onMax: () -> Unit,
        onCupsonline: () -> Unit,
        onMailru: () -> Unit,
    ) {
        val popupView = LayoutInflater.from(context).inflate(R.layout.dropdown_transport_menu, null)
        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.bg_dropdown_transports))
            elevation = 8.dpToPx(context).toFloat()
            animationStyle = R.style.DropdownAnimation
            isOutsideTouchable = true
            isFocusable = true
        }

        popupView.findViewById<View>(R.id.option_yandex)?.setOnClickListener {
            onYandex(); popup.dismiss()
        }
        popupView.findViewById<View>(R.id.option_vyandex)?.setOnClickListener {
            onVyandex(); popup.dismiss()
        }
        popupView.findViewById<View>(R.id.option_max)?.setOnClickListener {
            onMax(); popup.dismiss()
        }
        popupView.findViewById<View>(R.id.option_cupsonline)?.setOnClickListener {
            onCupsonline(); popup.dismiss()
        }
        popupView.findViewById<View>(R.id.option_mailru)?.setOnClickListener {
            onMailru(); popup.dismiss()
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.showAsDropDown(this, 0, -popupView.measuredHeight - height - 8.dpToPx(context))
    }
}
