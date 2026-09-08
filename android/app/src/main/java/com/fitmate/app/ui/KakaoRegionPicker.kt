package com.fitmate.app.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitmate.app.data.remote.FitMateApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Kakao may return a legal village; keep only its parent town, never an address or building. */
internal fun postcodeRegionCode(bcode: String): String {
    require(Regex("[0-9]{10}").matches(bcode)) { "주소의 지역 코드를 확인할 수 없습니다." }
    return "KR-LAW-${bcode.take(8)}00"
}

@Composable internal fun KakaoRegionPicker(api: FitMateApiClient, label: String, choose: (String, String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Text("활동 지역", style = MaterialTheme.typography.labelLarge)
    if (label.isNotBlank()) Text(label)
    OutlinedButton({ error = null; open = true }, enabled = !loading) {
        Text(if (loading) "지역 확인 중…" else if (label.isBlank()) "주소로 지역 검색" else "활동 지역 변경")
    }
    Text("주소를 검색하면 시·군·구와 읍·면·동만 저장해요. 도로명·번지·건물명·상세주소는 저장하지 않아요.", style = MaterialTheme.typography.bodySmall)
    ErrorText(error)
    if (open) KakaoPostcodeDialog({ open = false }) { bcode ->
        open = false; loading = true; error = null
        scope.launch {
            try {
                val code = postcodeRegionCode(bcode)
                val region = withContext(Dispatchers.IO) { api.items("regions?code=$code", false) }
                    .singleOrNull { it.text("code") == code && it.text("level") == "TOWN" }
                    ?: throw IllegalStateException("선택한 지역이 아직 등록되지 않았습니다. 다른 주소를 검색하거나 잠시 후 다시 시도해 주세요.")
                choose(region.text("id"), region.text("name"))
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = failure.message ?: "지역을 확인하지 못했습니다. 다시 검색해 주세요."
            } finally { loading = false }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable private fun KakaoPostcodeDialog(close: () -> Unit, selected: (String) -> Unit) {
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val onSelected by rememberUpdatedState(selected)
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("주소로 활동 지역 찾기", modifier = Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.titleMedium)
                    TextButton(close) { Text("닫기") }
                }
                Text("검색은 카카오에서 제공해요. FitMate에는 읍·면·동까지만 반영됩니다.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                if (error) {
                    Text("주소 검색을 불러오지 못했어요. 인터넷 연결을 확인해 주세요.", Modifier.padding(16.dp))
                    TextButton({ error = false; retry++ }) { Text("다시 시도") }
                }
                key(retry) {
                    AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
                        WebView(context).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.domStorageEnabled = false
                            settings.saveFormData = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val uri = request.url
                                    if (request.isForMainFrame) {
                                        if (uri.scheme == "https" && uri.host == "fitmate.invalid") {
                                            when (uri.path) {
                                                "/postcode-result" -> uri.getQueryParameter("code")?.let { code ->
                                                    if (Regex("[0-9]{10}").matches(code)) onSelected(code) else error = true
                                                }
                                                "/postcode-error" -> error = true
                                            }
                                        }
                                        return true
                                    }
                                    return uri.scheme != "https" && uri.scheme != "about"
                                }
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                                    if (request.isForMainFrame) error = true
                                }
                            }
                            loadDataWithBaseURL("https://fitmate.invalid/", PostcodeHtml, "text/html", "UTF-8", null)
                        }
                    }, onRelease = { it.stopLoading(); it.destroy() })
                }
            }
        }
    }
}

// Only the legal region code crosses into the app; full addresses remain inside this disposable WebView.
private val PostcodeHtml = """
<!doctype html><html lang="ko"><head><meta name="viewport" content="width=device-width,initial-scale=1.0">
<style>html,body{margin:0;width:100%;height:100vh;}#search{position:fixed;inset:0;}</style></head><body><div id="search"></div>
<script src="https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"
onerror="location.href='https://fitmate.invalid/postcode-error'"></script>
<script>
try {
  new kakao.Postcode({width:'100%',height:'100%',oncomplete:function(data){
    location.href='https://fitmate.invalid/postcode-result?code='+encodeURIComponent(data.bcode);
  }}).embed(document.getElementById('search'));
} catch(e) { location.href='https://fitmate.invalid/postcode-error'; }
</script></body></html>
""".trimIndent()
