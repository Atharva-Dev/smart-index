package com.kuriousdragon.smartindex.buildlogic

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Pure checks on a merged `AndroidManifest.xml` (ADR-009, FR-016, NFR-006, HLD §7 Security). Used by the
 * Gradle tasks of `smartindex.android.application` and unit-tested in `build-logic` with fixture manifests.
 */
object ManifestChecks {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** Permissions that give network access or network state; none may appear in any variant (FR-016). */
    val NETWORK_PERMISSIONS: Set<String> = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_WIFI_STATE",
        "android.permission.CHANGE_WIFI_MULTICAST_STATE",
        "android.permission.NEARBY_WIFI_DEVICES",
    )

    /**
     * Exported components are allowed only when the system guards them with one of these permissions
     * (WorkManager's `SystemJobService` uses BIND_JOB_SERVICE; profileinstaller's receiver uses DUMP).
     */
    val SYSTEM_GUARD_PERMISSIONS: Set<String> = setOf(
        "android.permission.BIND_JOB_SERVICE",
        "android.permission.DUMP",
        // Default-SMS-role components of the debug-only :tools:seeder (harness LLD §3.5); only the system holds these.
        "android.permission.BROADCAST_SMS",
        "android.permission.BROADCAST_WAP_PUSH",
        "android.permission.SEND_RESPOND_VIA_MESSAGE",
    )

    /**
     * Unguarded exported components accepted by name: the seeder's SENDTO stub, required for `ROLE_SMS`
     * (harness LLD §3.5: "accepted exception to only the launcher is exported"). `:app` has none of these.
     */
    val ACCEPTED_EXPORTED: Set<String> = setOf(
        "com.kuriousdragon.smartindex.tools.seeder.role.ComposeStubActivity",
    )

    /** Exported test/tooling activities tolerated in debuggable variants only (Compose preview/test). */
    val DEBUG_ONLY_EXPORTED: Set<String> = setOf(
        "androidx.compose.ui.tooling.PreviewActivity",
        "androidx.activity.ComponentActivity",
    )

    private val COMPONENT_TAGS = listOf("activity", "activity-alias", "service", "receiver", "provider")

    /** Returns one message per network permission requested in [manifestXml]; empty = pass. */
    fun networkPermissionViolations(manifestXml: String): List<String> {
        val root = parse(manifestXml)
        return listOf("uses-permission", "uses-permission-sdk-23")
            .flatMap { tag -> root.children(tag) }
            .mapNotNull { it.androidAttr("name") }
            .filter { it in NETWORK_PERMISSIONS }
            .distinct()
            .map { "network permission requested: $it" }
    }

    /**
     * Returns one message per hardening violation in [manifestXml]; empty = pass:
     * - `<application android:allowBackup>` must be `false` and `android:dataExtractionRules` set;
     * - only the MAIN/LAUNCHER activity may be exported, except components guarded by a system
     *   permission ([SYSTEM_GUARD_PERMISSIONS]) and, when [debuggable], [DEBUG_ONLY_EXPORTED].
     */
    fun hardeningViolations(manifestXml: String, debuggable: Boolean): List<String> {
        val root = parse(manifestXml)
        val app = root.children("application").firstOrNull()
            ?: return listOf("no <application> element")
        val out = mutableListOf<String>()
        if (app.androidAttr("allowBackup") != "false") out += "application android:allowBackup must be \"false\""
        if (app.androidAttr("dataExtractionRules").isNullOrEmpty()) {
            out += "application android:dataExtractionRules must be set"
        }
        for (tag in COMPONENT_TAGS) {
            for (c in app.children(tag)) {
                if (c.androidAttr("exported") != "true") continue
                val name = c.androidAttr("name") ?: c.androidAttr("targetActivity") ?: "?"
                val launcher = (tag == "activity" || tag == "activity-alias") && c.isLauncher()
                val allowed = launcher ||
                    c.androidAttr("permission") in SYSTEM_GUARD_PERMISSIONS ||
                    name in ACCEPTED_EXPORTED ||
                    (debuggable && name in DEBUG_ONLY_EXPORTED)
                if (!allowed) out += "exported $tag is not allowed: $name"
            }
        }
        return out
    }

    private fun Element.isLauncher(): Boolean = children("intent-filter").any { f ->
        f.children("action").any { it.androidAttr("name") == "android.intent.action.MAIN" } &&
            f.children("category").any { it.androidAttr("name") == "android.intent.category.LAUNCHER" }
    }

    private fun parse(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }

    private fun Element.children(tag: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { it.tagName == tag }
    }

    private fun Element.androidAttr(name: String): String? = getAttributeNS(ANDROID_NS, name).ifEmpty { null }
}
