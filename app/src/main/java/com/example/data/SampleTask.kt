package com.example.data

/**
 * Task configuration file.
 * This is the single constant file to configure the YouTube task.
 */
object SampleTask {
    // =========================================================================
    // TASK CONFIGURATION (Only place to edit your YouTube task)
    // =========================================================================
    
    // videoUrl: PASTE_MY_YOUTUBE_LINK_HERE
    // You can replace this with your YouTube link (e.g. "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    const val videoUrl = "PASTE_MY_YOUTUBE_LINK_HERE"

    // Default demo video used as fallback when the placeholder is untouched
    const val fallbackDemoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    // Reward given to local wallet upon successful task completion
    const val rewardCoins = 10

    // Required watch duration in seconds
    // set to 20 for quick testing (default: 180 = 3 minutes)
    const val requiredSeconds = 180
}
