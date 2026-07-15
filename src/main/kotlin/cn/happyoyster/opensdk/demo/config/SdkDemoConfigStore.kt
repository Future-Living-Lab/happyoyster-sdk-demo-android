package cn.happyoyster.opensdk.demo.config

import android.content.Context
import cn.happyoyster.opensdk.demo.ui.isApiHost
import cn.happyoyster.opensdk.demo.ui.isHttpUrl
import java.util.Locale

internal data class SdkDemoConfig(
    val gatewayBaseUrl: String,
    val sdkApiHost: String,
    val token: String,
    val tokenExpiresAtSec: Long,
    val language: SdkDemoLanguage,
)

internal enum class SdkDemoLanguage(val storageValue: String) {
    Chinese("zh"),
    English("en"),
    ;

    val locale: Locale
        get() = when (this) {
            Chinese -> Locale.SIMPLIFIED_CHINESE
            English -> Locale.US
        }
}

internal class SdkDemoConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SdkDemoConfig =
        SdkDemoConfig(
            gatewayBaseUrl = prefs.validUrlOrDefault(KEY_GATEWAY_BASE_URL, DEFAULT_GATEWAY_BASE_URL),
            sdkApiHost = prefs.validApiHostOrDefault(KEY_SDK_API_HOST, DEFAULT_SDK_API_HOST),
            token = prefs.getString(KEY_TOKEN, "").orEmpty(),
            tokenExpiresAtSec = prefs.getLong(KEY_TOKEN_EXPIRES_AT_SEC, 0L),
            language = prefs.languageOrDefault(),
        )

    fun save(config: SdkDemoConfig) {
        prefs.edit()
            .putString(KEY_GATEWAY_BASE_URL, config.gatewayBaseUrl)
            .putString(KEY_SDK_API_HOST, config.sdkApiHost)
            .putString(KEY_TOKEN, config.token)
            .putLong(KEY_TOKEN_EXPIRES_AT_SEC, config.tokenExpiresAtSec)
            .putString(KEY_LANGUAGE, config.language.storageValue)
            .apply()
    }

    fun clearToken() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT_SEC)
            .apply()
    }

    private companion object {
        private const val PREFS_NAME = "happy_oyster_sdk_demo"
        private const val KEY_GATEWAY_BASE_URL = "gateway_base_url"
        private const val KEY_SDK_API_HOST = "sdk_api_host"
        private const val KEY_TOKEN = "token"
        private const val KEY_TOKEN_EXPIRES_AT_SEC = "token_expires_at_sec"
        private const val KEY_LANGUAGE = "language"

        private const val DEFAULT_GATEWAY_BASE_URL = ""
        private const val DEFAULT_SDK_API_HOST = ""

        private fun android.content.SharedPreferences.validUrlOrDefault(
            key: String,
            defaultValue: String,
        ): String {
            val value = getString(key, defaultValue).orEmpty()
            return if (value.isHttpUrl()) value else defaultValue
        }

        private fun android.content.SharedPreferences.validApiHostOrDefault(
            key: String,
            defaultValue: String,
        ): String {
            val value = getString(key, defaultValue).orEmpty()
            return if (value.isApiHost()) value else defaultValue
        }

        private fun android.content.SharedPreferences.languageOrDefault(): SdkDemoLanguage {
            val value = getString(KEY_LANGUAGE, SdkDemoLanguage.Chinese.storageValue)
            return SdkDemoLanguage.entries.firstOrNull { it.storageValue == value }
                ?: SdkDemoLanguage.Chinese
        }
    }
}
