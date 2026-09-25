package com.folbazar.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.folbazar.admin.data.Session
import com.folbazar.admin.ui.FolBazarAdminApp
import com.folbazar.admin.ui.theme.FolBazarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Session.init(this)
        com.folbazar.admin.ui.theme.ThemePrefs.init(this)

        setContent {
            FolBazarTheme {
                FolBazarAdminApp()
            }
        }
    }
}
