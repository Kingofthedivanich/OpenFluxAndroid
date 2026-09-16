package io.github.p1neapplexpress.openflux.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import io.github.p1neapplexpress.openflux.R
import io.github.p1neapplexpress.openflux.data.EncryptionKey
import io.github.p1neapplexpress.openflux.data.TransportType
import io.github.p1neapplexpress.openflux.data.Tunnel
import io.github.p1neapplexpress.openflux.data.TunnelPayload
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
    private var editing: Tunnel? = null

    private lateinit var transportLabel: TextView
    private lateinit var urlContainer: TextInputLayout
    private lateinit var maxContainer: View
    private lateinit var cupsWarning: View

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
        transportLabel = view.findViewById(R.id.selectedTransport)
        urlContainer = view.findViewById(R.id.urlContainer)
        maxContainer = view.findViewById(R.id.maxContainer)
        cupsWarning = view.findViewById(R.id.cupsEncryptionWarning)

        val name = view.findViewById<TextView>(R.id.name)
        val docUrl = view.findViewById<TextView>(R.id.documentUrl)
        val maxToken = view.findViewById<TextView>(R.id.maxToken)
        val maxUid = view.findViewById<TextView>(R.id.maxUserId)
        val keyContainer = view.findViewById<TextInputLayout>(R.id.encryptionKeyContainer)
        val key = view.findViewById<TextView>(R.id.encryptionKey)
        val codecSwitch = view.findViewById<SwitchMaterial>(R.id.codecSwitch)
        val codecLabel = view.findViewById<TextView>(R.id.selectedCodec)
        val debugSwitch = view.findViewById<SwitchMaterial>(R.id.debugSwitch)
        val debugLabel = view.findViewById<TextView>(R.id.selectedDebug)
        val save = view.findViewById<Button>(R.id.saveButton)

        // ─── Заполнение при редактировании ───
        val initial = editing
        if (initial != null) {
            val form = TunnelPayload.parse(initial.transportType, initial.transportConnPayload)
            name.text = initial.name
            docUrl.text = form.url
            maxToken.text = form.maxToken
            maxUid.text = form.maxUid
            key.text = initial.encryptionKey.orEmpty()
            codecSwitch.isChecked = form.legacyCodec
            debugSwitch.isChecked = form.debug
            save.text = getString(R.string.action_edit)
            applyTransport(form.transport)
        } else {
            applyTransport(TransportType.yandex)
        }
        codecLabel.setText(codecLabelOf(codecSwitch.isChecked))
        debugLabel.setText(if (debugSwitch.isChecked) R.string.on else R.string.off)

        view.findViewById<View>(R.id.select_transport_layout).setOnClickListener {
            it.showTransportDropdown(::applyTransport)
        }

        codecSwitch.setOnCheckedChangeListener { _, checked -> codecLabel.setText(codecLabelOf(checked)) }
        debugSwitch.setOnCheckedChangeListener { _, checked ->
            debugLabel.setText(if (checked) R.string.on else R.string.off)
        }
        view.findViewById<View>(R.id.codecSelector).setOnClickListener { codecSwitch.toggle() }
        view.findViewById<View>(R.id.debugSelector).setOnClickListener { debugSwitch.toggle() }

        key.doAfterTextChanged { keyContainer.error = null }

        save.setOnClickListener {
            val tunnelName = name.text.trim().toString()
            if (tunnelName.isEmpty()) {
                toast(R.string.name_required)
                return@setOnClickListener
            }

            val payload = TunnelPayload.build(
                TunnelPayload.Form(
                    transport = transport,
                    url = docUrl.text.trim().toString(),
                    maxToken = maxToken.text.trim().toString(),
                    maxUid = maxUid.text.trim().toString(),
                    legacyCodec = codecSwitch.isChecked,
                    debug = debugSwitch.isChecked,
                )
            )
            if (payload == null) {
                toast(R.string.fields_required)
                return@setOnClickListener
            }

            val rawKey = key.text.toString()
            if (rawKey.isNotBlank() && !EncryptionKey.isValid(rawKey)) {
                keyContainer.error = getString(R.string.encryption_key_too_short)
                return@setOnClickListener
            }

            val newTunnel = Tunnel(
                id = editing?.id ?: Random(System.currentTimeMillis()).nextLong(),
                name = tunnelName,
                transportType = transport.name,
                transportConnPayload = payload,
                encryptionKey = rawKey.takeIf { it.isNotBlank() }?.let(EncryptionKey::normalize),
            )

            val old = editing
            if (old != null) {
                vm.updateTunnel(old, newTunnel)
                toast(R.string.config_saved)
            } else {
                vm.addTunnel(newTunnel)
            }

            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun applyTransport(selected: TransportType) {
        transport = selected
        transportLabel.setText(labelOf(selected))
        urlContainer.isVisible = selected.usesUrl
        maxContainer.isVisible = !selected.usesUrl
        urlContainer.hint = getString(
            when (selected) {
                TransportType.mailru -> R.string.mailru_url
                TransportType.cupsonline -> R.string.cupsonline_rooms
                else -> R.string.document_url
            }
        )
        urlContainer.helperText = getString(
            if (selected == TransportType.cupsonline) R.string.cupsonline_rooms_helper else R.string.url_must_match
        )
        cupsWarning.isVisible = selected == TransportType.cupsonline
    }

    @StringRes
    private fun labelOf(type: TransportType): Int = when (type) {
        TransportType.yandex -> R.string.yandex_docs_backend
        TransportType.vyandex -> R.string.vyandex_backend
        TransportType.max -> R.string.max_messenger_backend
        TransportType.cupsonline -> R.string.cupsonline_backend
        TransportType.mailru -> R.string.mailru_backend
    }

    @StringRes
    private fun codecLabelOf(legacy: Boolean): Int =
        if (legacy) R.string.legacy_codec_on else R.string.legacy_codec_off

    private fun toast(@StringRes message: Int) =
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    override fun onNewEvent(ev: AppEvent) = Unit

    private fun View.showTransportDropdown(onSelect: (TransportType) -> Unit) {
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

        val options = listOf(
            R.id.option_yandex to TransportType.yandex,
            R.id.option_vyandex to TransportType.vyandex,
            R.id.option_max to TransportType.max,
            R.id.option_cupsonline to TransportType.cupsonline,
            R.id.option_mailru to TransportType.mailru,
        )
        for ((id, type) in options) {
            popupView.findViewById<View>(id)?.setOnClickListener {
                onSelect(type)
                popup.dismiss()
            }
        }

        // Five options no longer fit above the selector, which sits near the top.
        popup.showAsDropDown(this, 0, 8.dpToPx(context))
    }
}
