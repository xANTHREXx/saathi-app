package app.saathi.core

/** One settings screen inside a phone maker's own app: its package and the activity class to open. */
data class SettingsTarget(val pkg: String, val cls: String)

/**
 * Phone makers whose own battery manager kills background apps and foreground services even after
 * the Android battery-optimisation exemption. Each lists the screens (from dontkillmyapp.com) where
 * she can stop that, in the order to try them: names move between versions of the maker's software,
 * so the first one the phone knows wins. OTHER covers stock-like phones (Motorola, Pixel, Nokia),
 * which need nothing extra.
 */
enum class Oem(val targets: List<SettingsTarget>) {
    XIAOMI(listOf(
        SettingsTarget("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    )),
    VIVO(listOf(
        SettingsTarget("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        SettingsTarget("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        SettingsTarget("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
    )),
    OPPO(OPPO_TARGETS),
    REALME(OPPO_TARGETS),
    ONEPLUS(listOf(
        SettingsTarget("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
    )),
    SAMSUNG(listOf(
        SettingsTarget("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
    )),
    HUAWEI(listOf(
        SettingsTarget("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        SettingsTarget("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    )),
    OTHER(emptyList());

    /** Whether Saathi shows the "Keep Saathi running" card for this maker. */
    val needsCard: Boolean get() = this != OTHER

    companion object {
        /** Read from `Build.MANUFACTURER` and `Build.BRAND`; the brand wins, since Redmi, POCO and iQOO phones carry the parent's manufacturer. */
        fun from(manufacturer: String?, brand: String?): Oem {
            for (raw in listOf(brand, manufacturer)) {
                when (raw?.trim()?.lowercase()) {
                    "xiaomi", "redmi", "poco" -> return XIAOMI
                    "vivo", "iqoo" -> return VIVO
                    "oppo" -> return OPPO
                    "realme" -> return REALME
                    "oneplus" -> return ONEPLUS
                    "samsung" -> return SAMSUNG
                    "huawei", "honor" -> return HUAWEI
                }
            }
            return OTHER
        }
    }
}

private val OPPO_TARGETS = listOf(
    SettingsTarget("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
    SettingsTarget("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
    SettingsTarget("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
)
