package cn.happyoyster.opensdk.demo.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.config.SdkDemoConfig
import cn.happyoyster.opensdk.demo.config.SdkDemoLanguage
import cn.happyoyster.opensdk.demo.ui.OptionRow
import cn.happyoyster.opensdk.demo.ui.DemoButton
import cn.happyoyster.opensdk.demo.ui.DemoCard
import cn.happyoyster.opensdk.demo.ui.DemoPageHeader
import cn.happyoyster.opensdk.demo.ui.SelectButton
import cn.happyoyster.opensdk.demo.ui.isApiHost
import cn.happyoyster.opensdk.demo.ui.isHttpUrl

@Composable
internal fun ProfileTab(
    config: SdkDemoConfig,
    onConfigChange: (SdkDemoConfig) -> Unit,
    onApplyConfiguration: (gatewayBaseUrl: String, sdkApiHost: String) -> Unit,
) {
    var gatewayBaseUrlDraft by remember(config.gatewayBaseUrl) {
        mutableStateOf(config.gatewayBaseUrl)
    }
    var sdkApiHostDraft by remember(config.sdkApiHost) {
        mutableStateOf(config.sdkApiHost)
    }
    val configDraftValid = gatewayBaseUrlDraft.isHttpUrl() && sdkApiHostDraft.isApiHost()
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DemoPageHeader(
            title = stringResource(R.string.config_title),
            description = stringResource(R.string.config_description),
        )
        if (!configDraftValid) {
            Text(
                text = stringResource(R.string.config_required_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DemoCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.environment), fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = gatewayBaseUrlDraft,
                    onValueChange = { gatewayBaseUrlDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.gateway_base_url)) },
                    supportingText = { Text(stringResource(R.string.gateway_base_url_help)) },
                )
                OutlinedTextField(
                    value = sdkApiHostDraft,
                    onValueChange = { sdkApiHostDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sdk_api_host)) },
                    supportingText = { Text(stringResource(R.string.sdk_api_host_help)) },
                )
                DemoButton(
                    onClick = {
                        onApplyConfiguration(
                            gatewayBaseUrlDraft.trim(),
                            sdkApiHostDraft.trim(),
                        )
                    },
                    enabled = configDraftValid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.apply_configuration))
                }
            }
        }
        DemoCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionRow(stringResource(R.string.language)) {
                    SelectButton(
                        label = stringResource(R.string.language_chinese),
                        selected = config.language == SdkDemoLanguage.Chinese,
                    ) {
                        onConfigChange(config.copy(language = SdkDemoLanguage.Chinese))
                    }
                    SelectButton(
                        label = stringResource(R.string.language_english),
                        selected = config.language == SdkDemoLanguage.English,
                    ) {
                        onConfigChange(config.copy(language = SdkDemoLanguage.English))
                    }
                }
            }
        }
    }
}
