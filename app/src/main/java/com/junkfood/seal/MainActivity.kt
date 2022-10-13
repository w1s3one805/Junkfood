package com.junkfood.seal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.junkfood.seal.BaseApplication.Companion.context
import com.junkfood.seal.connectivity.ConnectivityObserver
import com.junkfood.seal.connectivity.NetworkConnectivityObserver
import com.junkfood.seal.ui.common.LocalDarkTheme
import com.junkfood.seal.ui.common.LocalDynamicColorSwitch
import com.junkfood.seal.ui.common.LocalSeedColor
import com.junkfood.seal.ui.common.SettingsProvider
import com.junkfood.seal.ui.page.HomeEntry
import com.junkfood.seal.ui.page.download.DownloadViewModel
import com.junkfood.seal.ui.theme.SealTheme
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.PreferenceUtil.NETWORK_STATUS
import com.junkfood.seal.util.TextUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val downloadViewModel: DownloadViewModel by viewModels()
    private lateinit var connectivityObserver: ConnectivityObserver

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        runBlocking {
            if (Build.VERSION.SDK_INT < 33)
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(PreferenceUtil.getLanguageConfiguration())
                )
        }
        context = this.baseContext
        connectivityObserver = NetworkConnectivityObserver(this)
        setContent {
            val isUrlSharingTriggered =
                downloadViewModel.stateFlow.collectAsState().value.isUrlSharingTriggered
            val windowSizeClass = calculateWindowSizeClass(this)
            SettingsProvider(windowSizeClass.widthSizeClass) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    seedColor = LocalSeedColor.current,
                    isDynamicColorEnabled = LocalDynamicColorSwitch.current,
                ) {
                    HomeEntry(
                        downloadViewModel,
                        isUrlSharingTriggered
                    )
                    val status by connectivityObserver.observe().collectAsState(initial = ConnectivityObserver.Status.Unavaliable)
                    downloadViewModel.updateConnectivityState(status)
                }
            }
        }
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        handleShareIntent(intent)
        super.onNewIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        Log.d(TAG, "handleShareIntent: $intent")
        if (Intent.ACTION_SEND == intent.action)
            intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { sharedContent ->
                    intent.removeExtra(Intent.EXTRA_TEXT)
                    TextUtil.matchUrlFromSharedText(sharedContent)
                        .let { matchedUrl ->
                            if (sharedUrl != matchedUrl) {
                                sharedUrl = matchedUrl
                                downloadViewModel.updateUrl(sharedUrl, true)

                            }
                        }
                }
    }

    companion object {
        private const val TAG = "MainActivity"
        private var sharedUrl = ""
        var isServiceRunning = false
        private val connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                val binder = service as DownloadService.DownloadServiceBinder
                isServiceRunning = true
            }

            override fun onServiceDisconnected(arg0: ComponentName) {
            }
        }

        fun startService() {
            if (isServiceRunning) return
            Intent(context.applicationContext, DownloadService::class.java).also { intent ->
                context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }

        fun stopService() {
            if (!isServiceRunning) return
            try {
                isServiceRunning = false
                context.applicationContext.unbindService(connection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun setLanguage(locale: String) {
            Log.d(TAG, "setLanguage: $locale")
            val localeListCompat =
                if (locale.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(locale)
            BaseApplication.applicationScope.launch(Dispatchers.Main) {
                AppCompatDelegate.setApplicationLocales(localeListCompat)
            }
        }

    }

}





