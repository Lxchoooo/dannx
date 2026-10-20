package com.dannx.walls.data

object DemoWallpapers {
    val items: List<Wallpaper> = listOf(
        demo(1, "Aurora", "Naturaleza", featured = true, tags = listOf("cielo", "aurora", "noche")),
        demo(2, "Duna", "Abstracto", featured = true, tags = listOf("arena", "desierto")),
        demo(3, "Nocturno", "Ciudad", featured = true, tags = listOf("noche", "ciudad", "luces")),
        demo(4, "Floración", "Naturaleza", featured = true, tags = listOf("flores", "primavera")),
        demo(5, "Órbita", "Espacio", tags = listOf("espacio", "planeta")),
        demo(6, "Papel", "Minimalista", tags = listOf("minimal", "textura")),
        demo(7, "Marea", "Naturaleza", tags = listOf("agua", "playa")),
        demo(8, "Callejón", "Ciudad", tags = listOf("ciudad", "noche")),
        demo(9, "Musgo", "Texturas", tags = listOf("verde", "naturaleza")),
        demo(10, "Solar", "Espacio", tags = listOf("sol", "espacio")),
        demo(11, "Silencio", "Minimalista", tags = listOf("minimal", "calma")),
        demo(12, "Espejismo", "Abstracto", tags = listOf("color", "abstracto")),
    )

    val collections: List<WallpaperCollection> = listOf(
        WallpaperCollection(
            id = "demo-collection-immortal",
            name = "Immortal Edition",
            description = "Una edición personal con luz, naturaleza y ciudad.",
            ownerId = "demo-owner",
            ownerName = "DannX Studio",
            isPublic = true,
            createdAt = "2026-01-10",
            wallpaperIds = listOf("demo-1", "demo-4", "demo-7"),
        ),
        WallpaperCollection(
            id = "demo-collection-flowers",
            name = "Flowers",
            description = "Flores, texturas y detalles naturales.",
            ownerId = "demo-owner",
            ownerName = "DannX Studio",
            isPublic = true,
            createdAt = "2026-01-11",
            wallpaperIds = listOf("demo-4", "demo-7", "demo-9"),
        ),
        WallpaperCollection(
            id = "demo-collection-night",
            name = "Nocturno",
            description = "Fondos para la madrugada.",
            ownerId = "demo-owner",
            ownerName = "DannX Studio",
            isPublic = true,
            createdAt = "2026-01-12",
            wallpaperIds = listOf("demo-1", "demo-3", "demo-8"),
        ),
    )

    private fun demo(
        number: Int,
        title: String,
        category: String,
        featured: Boolean = false,
        tags: List<String> = emptyList(),
    ) = Wallpaper(
        id = "demo-$number",
        title = title,
        category = category,
        imageUrl = "https://picsum.photos/seed/dannx-$number/1080/1920",
        thumbUrl = "https://picsum.photos/seed/dannx-$number/540/960",
        author = "DannX Studio",
        width = 1080,
        height = 1920,
        featured = featured,
        createdAt = "2026-01-${number.toString().padStart(2, '0')}",
        tags = tags,
    )
}
