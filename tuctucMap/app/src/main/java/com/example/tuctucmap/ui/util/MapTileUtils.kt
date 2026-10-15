package com.example.tuctucmap.ui.util

import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * Fuente de mosaicos en tiempo real de OpenStreetMap para TucTuc Map.
 * Al usar el identificador exclusivo "TucTucOsmLive", OSMDroid genera un almacén
 * de caché independiente, evitando completamente cualquier mosaico previo bloqueado (403)
 * o con marcas de agua.
 */
val AppTileSource = XYTileSource(
    "TucTucOsmLive",
    0,
    19,
    256,
    ".png",
    arrayOf("https://tile.openstreetmap.org/"),
    "© OpenStreetMap contributors"
)

