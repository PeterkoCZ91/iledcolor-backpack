package com.batoh.core.domain.model

data class InterestTag(
    val name: String,
    val searchQuery: String,
    val group: String
)

object PredefinedInterests {
    val all = listOf(
        // Hudba
        InterestTag("DnB", "drum and bass", "Hudba"),
        InterestTag("EDM", "edm music", "Hudba"),
        InterestTag("Techno", "techno", "Hudba"),
        InterestTag("Hip Hop", "hip hop", "Hudba"),
        InterestTag("Rock", "rock music", "Hudba"),
        InterestTag("Lo-Fi", "lofi", "Hudba"),
        // Vizual
        InterestTag("Pixel Art", "pixel art", "Vizual"),
        InterestTag("Cyberpunk", "cyberpunk", "Vizual"),
        InterestTag("Neon", "neon lights", "Vizual"),
        InterestTag("Vaporwave", "vaporwave", "Vizual"),
        InterestTag("Synthwave", "synthwave", "Vizual"),
        InterestTag("Glitch", "glitch art", "Vizual"),
        // Fun
        InterestTag("Memes", "memes", "Fun"),
        InterestTag("Funny Cats", "funny cats", "Fun"),
        InterestTag("Gaming", "gaming", "Fun"),
        InterestTag("Anime", "anime", "Fun"),
        // Priroda
        InterestTag("Fire", "fire", "Priroda"),
        InterestTag("Rain", "rain", "Priroda"),
        InterestTag("Stars", "stars space", "Priroda"),
        InterestTag("Ocean", "ocean waves", "Priroda"),
        // Symboly
        InterestTag("Skull", "skull", "Symboly"),
        InterestTag("Heart", "heart", "Symboly"),
        InterestTag("Lightning", "lightning", "Symboly"),
        InterestTag("Rainbow", "rainbow", "Symboly"),
    )

    val groups: Map<String, List<InterestTag>> = all.groupBy { it.group }
}
