package com.example

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Shared entry point for iOS (Compose Multiplatform / UIKit).
 * In Swift, called via: MainViewControllerKt.mainViewController()
 */
fun mainViewController(): UIViewController = ComposeUIViewController {
    SpaceDodgerApp()
}
