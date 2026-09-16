package io.github.p1neapplexpress.openflux.data

enum class TransportType {
    yandex,
    vyandex,
    max,
    cupsonline,
    mailru;

    companion object {
        fun from(raw: String): TransportType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: yandex
    }
}
