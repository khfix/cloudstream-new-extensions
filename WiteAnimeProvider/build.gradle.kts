dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jsoup:jsoup:1.18.3")
    
}

// Use an integer for version numbers
version = 1

cloudstream {
    // All of these propee optional, you can safely remove any of them.

    description = "WiteAnime is a provider for anime content, offering a wide range of titles and genres."
    authors = listOf("khfix")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1 // Will be 3 if unspecified

    tvTypes = listOf("Anime", "AnimeMovie", "OVA")

    requiresResources = true
    language = "ar"

    iconUrl = "https://witanime.uno/wp-content/uploads/2023/08/cropped-Logo-WITU-32x32.png"
    
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}