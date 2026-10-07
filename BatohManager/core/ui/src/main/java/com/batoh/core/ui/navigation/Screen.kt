package com.batoh.core.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search?initialQuery={initialQuery}") {
        const val ARG_INITIAL_QUERY = "initialQuery"
        fun createRoute(initialQuery: String = "") =
            if (initialQuery.isNotBlank()) "search?initialQuery=${android.net.Uri.encode(initialQuery)}"
            else "search"
    }
    object Library : Screen("library")
    data object GifEditor : Screen("gif-editor?gif_uri={gif_uri}") {
        const val ARG_GIF_URI = "gif_uri"
        fun createRoute(uri: String) = "gif-editor?gif_uri=${android.net.Uri.encode(uri)}"
    }
    data object GifChain : Screen("gif-chain?gif_uris={gif_uris}") {
        const val ARG_GIF_URIS = "gif_uris"
        /** Jednotlivá URI se enkódují a spojí čárkou; celek se enkóduje ještě jednou pro trasu. */
        fun createRoute(uris: List<String>) =
            "gif-chain?gif_uris=${android.net.Uri.encode(uris.joinToString(",") { android.net.Uri.encode(it) })}"
    }
    object Categories : Screen("categories")
    data object Convert : Screen("convert")
    data object TextBanner : Screen("text-banner")
    data object Backpack : Screen("backpack?auto_test={auto_test}&gif_uri={gif_uri}") {
        const val ARG_AUTO_TEST = "auto_test"
        const val ARG_GIF_URI = "gif_uri"
        fun uploadRoute(gifUri: String) = "backpack?gif_uri=${android.net.Uri.encode(gifUri)}"
        fun createRoute(autoTest: Boolean = false) =
            if (autoTest) "backpack?auto_test=true" else "backpack"
    }
    object Detail : Screen("detail/{gifUrl}") {
        const val ARG_GIF_URL = "gifUrl"
        const val ARG_MP4_URL = "mp4Url"
        fun createRoute(gifUrl: String, mp4Url: String = "") =
            if (mp4Url.isNotBlank()) "detail/${android.net.Uri.encode(gifUrl)}?$ARG_MP4_URL=${android.net.Uri.encode(mp4Url)}"
            else "detail/${android.net.Uri.encode(gifUrl)}"
    }
}
