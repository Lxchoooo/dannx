package com.dannx.walls.data

object DemoWallpapers {
    val items: List<Wallpaper> = listOf(
        demo(1, "Aurora", "Naturaleza", featured = true),
        demo(2, "Duna", "Abstracto", featured = true),
        demo(3, "Nocturno", "Ciudad", featured = true),
        demo(4, "Floración", "Naturaleza", featured = true),
        demo(5, "Órbita", "Espacio"),
        demo(6, "Papel", "Minimalista"),
        demo(7, "Marea", "Naturaleza"),
        demo(8, "Callejón", "Ciudad"),
        demo(9, "Musgo", "Texturas"),
        demo(10, "Solar", "Espacio"),
        demo(11, "Silencio", "Minimalista"),
        demo(12, "Espejismo", "Abstracto"),
    )

    private fun demo(
        number: Int,
        title: String,
        category: String,
        featured: Boolean = false,
    ) = Wallpaper(
        id = "demo-$number",
        title = title,
        category = category,
        imageUrl = "https://picsum.photos/seed/dannx-$number/1080/1920",
        author = "DannX Studio",
        width = 1080,
        height = 1920,
        featured = featured,
        createdAt = "2026-01-${number.toString().padStart(2, '0')}",
    )
}
