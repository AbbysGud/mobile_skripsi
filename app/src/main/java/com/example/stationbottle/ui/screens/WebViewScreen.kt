package com.example.stationbottle.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(onCookieExtracted: (String) -> Unit) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)

                        val cookieManager = CookieManager.getInstance()
                        val cookies = cookieManager.getCookie(url)

                        if (!cookies.isNullOrEmpty()) {
                            val cookieList = cookies.split(";")
                            val testCookie = cookieList.find { it.trim().startsWith("__test=") }

                            testCookie?.let {
                                onCookieExtracted(testCookie)
                            }
                        }
                    }
                }

                // Load halaman yang akan mendapatkan cookie
                loadUrl("http://stationbottle-be.42web.io")
            }
        }
    )
}
