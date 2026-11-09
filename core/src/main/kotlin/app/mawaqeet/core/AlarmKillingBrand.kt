package app.mawaqeet.core

/**
 * Phone brands whose battery savers are known to delay or kill exact alarms (dontkillmyapp.com). Onboarding shows
 * their battery step and Home the "Fix alerts" tip only on these; every other phone gets the general instructions
 * in Settings → Alerts → Fix alerts. The app never asks for a battery-optimisation exemption itself (Play policy).
 */
enum class AlarmKillingBrand {
    SAMSUNG,

    /** Xiaomi, Redmi and POCO (MIUI / HyperOS). */
    XIAOMI,

    /** Huawei and Honor (EMUI / MagicOS). */
    HUAWEI,

    /** Oppo, Realme and OnePlus (ColorOS). */
    OPPO,

    VIVO;

    companion object {
        /** The brand for [manufacturer] (`Build.MANUFACTURER`, any case), or null for phones that keep alarms. */
        fun of(manufacturer: String): AlarmKillingBrand? = when (manufacturer.trim().lowercase()) {
            "samsung" -> SAMSUNG
            "xiaomi", "redmi", "poco" -> XIAOMI
            "huawei", "honor" -> HUAWEI
            "oppo", "realme", "oneplus" -> OPPO
            "vivo", "iqoo" -> VIVO
            else -> null
        }
    }
}
