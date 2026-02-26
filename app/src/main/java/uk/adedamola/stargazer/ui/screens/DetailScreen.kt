package uk.adedamola.stargazer.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.net.Uri
import coil3.compose.AsyncImage
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import uk.adedamola.stargazer.R
import uk.adedamola.stargazer.ui.theme.FactoryCyan
import uk.adedamola.stargazer.ui.theme.FactoryDarkGrey
import uk.adedamola.stargazer.ui.theme.FactoryOrange
import uk.adedamola.stargazer.ui.theme.FactoryYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repoName: String?,
    repoId: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(repoName) {
        if (repoName != null) {
            viewModel.loadRepository(repoName)
        }
    }

    Scaffold(
        containerColor = FactoryDarkGrey,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = repoName?.split("/")?.lastOrNull()?.uppercase() ?: "REPOSITORY",
                        fontFamily = FontFamily.Monospace
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FactoryDarkGrey,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = FactoryOrange)
                        Text(
                            text = "LOADING_REPOSITORY_DATA...",
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }

            is DetailUiState.Success -> {
                val repo = state.repository
                val readme = state.readme
                val uriHandler = LocalUriHandler.current
                with(sharedTransitionScope) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "card-$repoId"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                    ) {
                        // Header section (wraps content, does not scroll)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Owner Avatar and Name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AsyncImage(
                                    model = repo.owner.avatarUrl,
                                    contentDescription = "Owner avatar",
                                    modifier = Modifier
                                        .sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "avatar-$repoId"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                        .size(64.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = repo.owner.login,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 20.sp,
                                        color = FactoryCyan,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "owner-$repoId"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                    )
                                    Text(
                                        text = repo.name,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 24.sp,
                                        color = FactoryOrange,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "name-$repoId"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Description
                            if (repo.description != null) {
                                Text(
                                    text = repo.description,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }

                            // Stats Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "stars-$repoId"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                ) {
                                    Text(
                                        text = "STARS",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "\u2605 ${repo.stargazersCount}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FactoryYellow
                                    )
                                }
                                StatItem("FORKS", repo.forksCount.toString(), FactoryCyan)
                                StatItem("WATCHERS", repo.watchersCount.toString(), FactoryOrange)

                                IconButton(
                                    onClick = { uriHandler.openUri(repo.htmlUrl) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.White, CircleShape)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_github),
                                        contentDescription = "Open on GitHub",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        }

                        // README WebView section (fills remaining space, scrolls internally)
                        if (readme != null) {
                            ReadmeWebView(
                                html = readme,
                                repoFullName = repoName ?: "",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "NO_README_AVAILABLE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            is DetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ERROR: ${state.message}",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReadmeWebView(
    html: String,
    repoFullName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fullHtml = wrapInHtmlDocument(html)

    val webViewState = rememberWebViewStateWithHTMLData(
        data = fullHtml
    )

    val navigator = rememberWebViewNavigator(
        requestInterceptor = object : RequestInterceptor {
            override fun onInterceptUrlRequest(
                request: WebRequest,
                navigator: WebViewNavigator
            ): WebRequestInterceptResult {
                val url = request.url
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                    return WebRequestInterceptResult.Reject
                }
                return WebRequestInterceptResult.Allow
            }
        }
    )

    DisposableEffect(Unit) {
        webViewState.webSettings.apply {
            isJavaScriptEnabled = true
            androidWebSettings.apply {
                isAlgorithmicDarkeningAllowed = false
                safeBrowsingEnabled = true
                supportZoom = false
            }
        }
        onDispose { }
    }

    WebView(
        state = webViewState,
        modifier = modifier,
        captureBackPresses = false,
        navigator = navigator
    )
}

private fun wrapInHtmlDocument(htmlBody: String): String {
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
            * { box-sizing: border-box; }
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                font-size: 14px;
                line-height: 1.6;
                color: #F0F0F0;
                background-color: #1A1A1A;
                padding: 16px;
                margin: 0;
                word-wrap: break-word;
                overflow-wrap: break-word;
            }
            a { color: #FF6600; text-decoration: none; }
            a:hover { text-decoration: underline; }
            h1, h2, h3, h4, h5, h6 {
                color: #FFFFFF;
                border-bottom: 1px solid #444444;
                padding-bottom: 0.3em;
                margin-top: 24px;
                margin-bottom: 16px;
                font-weight: 600;
            }
            h1 { font-size: 1.6em; }
            h2 { font-size: 1.4em; }
            h3 { font-size: 1.2em; }
            code {
                background-color: #2D2D2D;
                color: #00CCFF;
                padding: 0.2em 0.4em;
                border-radius: 3px;
                font-family: 'Courier New', Courier, monospace;
                font-size: 0.9em;
            }
            pre {
                background-color: #2D2D2D;
                border: 1px solid #444444;
                border-radius: 6px;
                padding: 16px;
                overflow-x: auto;
                line-height: 1.45;
            }
            pre code {
                background-color: transparent;
                color: #F0F0F0;
                padding: 0;
            }
            blockquote {
                border-left: 4px solid #FF6600;
                color: #AAAAAA;
                padding: 0 16px;
                margin: 0 0 16px 0;
            }
            table { border-collapse: collapse; width: 100%; margin-bottom: 16px; }
            th, td { border: 1px solid #444444; padding: 6px 13px; }
            th { background-color: #2D2D2D; font-weight: 600; }
            tr:nth-child(even) { background-color: #222222; }
            img { max-width: 100%; height: auto; }
            hr { border: none; border-top: 1px solid #444444; margin: 24px 0; }
            ul, ol { padding-left: 2em; }
            li { margin-bottom: 4px; }
            .task-list-item { list-style-type: none; }
            .task-list-item input[type="checkbox"] { margin-right: 8px; }
        </style>
    </head>
    <body>
        $htmlBody
    </body>
    </html>
    """.trimIndent()
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
