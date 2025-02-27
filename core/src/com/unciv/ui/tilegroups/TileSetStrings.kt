package com.unciv.ui.tilegroups

import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.tilesets.TileSetConfig
import com.unciv.ui.images.ImageAttempter
import com.unciv.ui.images.ImageGetter

/**
 * @param tileSet Name of the tileset. Defaults to active at time of instantiation.
 * @param fallbackDepth Maximum number of fallback tilesets to try. Used to prevent infinite recursion.
 * */
class TileSetStrings(tileSet: String = UncivGame.Current.settings.tileSet, fallbackDepth: Int = 1) {

    // this is so that when we have 100s of TileGroups, they won't all individually come up with all these strings themselves,
    // it gets pretty memory-intensive (10s of MBs which is a lot for lower-end phones)
    val tileSetLocation = "TileSets/$tileSet/"
    val tileSetConfig = TileSetCache[tileSet] ?: TileSetConfig()

    // These need to be by lazy since the orFallback expects a tileset, which it may not get.
    val hexagon: String by lazy { orFallback {tileSetLocation + "Hexagon"} }
    val hexagonList by lazy { listOf(hexagon) }
    val crosshatchHexagon by lazy { orFallback { tileSetLocation + "CrosshatchHexagon" } }
    val crosshair by lazy { orFallback { getString(tileSetLocation, "Crosshair") } }
    val highlight by lazy { orFallback { getString(tileSetLocation, "Highlight") } }
    val cityOverlay = tileSetLocation + "CityOverlay"
    val roadsMap = RoadStatus.values()
        .filterNot { it == RoadStatus.None }
        .associateWith { tileSetLocation + it.name }
    val naturalWonderOverlay = tileSetLocation + "NaturalWonderOverlay"

    val tilesLocation = tileSetLocation + "Tiles/"
    val bottomRightRiver by lazy { orFallback { tilesLocation + "River-BottomRight"} }
    val bottomRiver by lazy { orFallback { tilesLocation + "River-Bottom"} }
    val bottomLeftRiver  by lazy { orFallback { tilesLocation + "River-BottomLeft"} }
    val unitsLocation = tileSetLocation + "Units/"

    val bordersLocation = tileSetLocation + "Borders/"


    // There aren't that many tile combinations, and so we end up joining the same strings over and over again.
    // On large maps, this can end up as quite a lot of space, some tens of MB!
    // In order to save on space, we have this function that gets several strings and returns their concat,
    //  but is able to retrieve the existing concat if it exists, letting us essentially save each string exactly once.
    private val stringConcatHashmap = HashMap<Pair<String, String>, String>()
    fun getString(vararg strings: String): String {
        var currentString = ""
        for (str in strings) {
            if (currentString == "") {
                currentString = str
                continue
            }
            val pair = Pair(currentString, str)
            if (stringConcatHashmap.containsKey(pair)) currentString = stringConcatHashmap[pair]!!
            else {
                val newString = currentString + str
                stringConcatHashmap[pair] = newString
                currentString = newString
            }
        }
        return currentString
    }

    val overlay = "Overlay"
    val city = "City"
    val tag = "-"
    fun getTile(baseTerrain: String) = getString(tilesLocation, baseTerrain)
    fun getBaseTerrainOverlay(baseTerrain: String) = getString(tileSetLocation, baseTerrain, overlay)
    fun getTerrainFeatureOverlay(terrainFeature: String) = getString(tileSetLocation, terrainFeature, overlay)


    fun getBorder(borderShapeString: String, innerOrOuter:String) = getString(bordersLocation, borderShapeString, innerOrOuter)

    /** Fallback [TileSetStrings] to use when the currently chosen tileset is missing an image. */
    val fallback by lazy {
        if (fallbackDepth <= 0 || tileSetConfig.fallbackTileSet == null)
            null
        else
            TileSetStrings(tileSetConfig.fallbackTileSet!!, fallbackDepth-1)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    /**
     * @param image An image path string, such as returned from an instance of [TileSetStrings].
     * @param fallbackImage A lambda function that will be run with the [fallback] as its receiver if the original image does not exist according to [ImageGetter.imageExists].
     * @return The original image path string if its image exists, or the return result of the [fallbackImage] lambda if the original image does not exist.
     * */
    fun orFallback(image: String, fallbackImage: TileSetStrings.() -> String): String {
        return if (fallback == null || ImageGetter.imageExists(image))
            image
        else fallback!!.run(fallbackImage)
    }

    /** @see orFallback */
    fun orFallback(image: TileSetStrings.() -> String)
            = orFallback(this.run(image), image)



    /** For caching image locations based on given parameters (era, style, etc)
     * Based on what the final image would look like if all parameters existed,
     * like "pikeman-Medieval era-France": "pikeman" */
    val imageParamsToImageLocation = HashMap<String,String>()


    /**
     * Image fallbacks work by precedence.
     * So currently, if you're france, it's the modern era, and you have a pikeman:
     * - If there's an era+style image of any era, take that
     * - Else, if there's an era-no-style image of any era, take that
     * - Only then check style-only
     * This means that if there's a "pikeman-France" and a "pikeman-Medieval era",
     * The era-based image wins out, even though it's not the current era.
     */

    private fun tryGetUnitImageLocation(unit:MapUnit): String? {
        val baseUnitIconLocation = this.unitsLocation + unit.name
        val civInfo = unit.civInfo
        val style = civInfo.nation.getStyleOrCivName()

        var imageAttempter = ImageAttempter(baseUnitIconLocation)
            // Era+style image: looks like  "pikeman-Medieval era-France"
            // More advanced eras default to older eras
            .tryEraImage(civInfo, baseUnitIconLocation, style, this)
            // Era-only image: looks like "pikeman-Medieval era"
            .tryEraImage(civInfo, baseUnitIconLocation, null, this)
            // Style era: looks like "pikeman-France" or "pikeman-European"
            .tryImage { "$baseUnitIconLocation-${civInfo.nation.getStyleOrCivName()}" }
            .tryImage { baseUnitIconLocation }

        if (unit.baseUnit.replaces != null)
            imageAttempter = imageAttempter.tryImage { getString(unitsLocation, unit.baseUnit.replaces!!) }

        return imageAttempter.getPathOrNull()
    }

    fun getUnitImageLocation(unit: MapUnit):String {
        val imageKey = getString(
            unit.name, tag,
            unit.civInfo.getEra().name, tag,
            unit.civInfo.nation.getStyleOrCivName()
        )
        // if in cache return that
        val currentImageMapping = imageParamsToImageLocation[imageKey]
        if (currentImageMapping!=null) return currentImageMapping

        val imageLocation = tryGetUnitImageLocation(unit)
            ?: fallback?.tryGetUnitImageLocation(unit)
            ?: ""
        imageParamsToImageLocation[imageKey] = imageLocation
        return imageLocation
    }

    private fun tryGetOwnedTileImageLocation(baseLocation:String, owner:CivilizationInfo): String? {
        val ownersStyle = owner.nation.getStyleOrCivName()
        return ImageAttempter(baseLocation)
            .tryEraImage(owner, baseLocation, ownersStyle, this)
            .tryEraImage(owner, baseLocation, null, this)
            .tryImage { getString(baseLocation, tag, ownersStyle) }
            .getPathOrNull()
    }

    fun getOwnedTileImageLocation(baseLocation:String, owner:CivilizationInfo): String {
        val imageKey = getString(baseLocation, tag,
            owner.getEra().name, tag,
            owner.nation.getStyleOrCivName())
        val currentImageMapping = imageParamsToImageLocation[imageKey]
        if (currentImageMapping!=null) return currentImageMapping

        val imageLocation = tryGetOwnedTileImageLocation(baseLocation, owner)
            ?: baseLocation

        imageParamsToImageLocation[imageKey] = imageLocation
        return imageLocation
    }
}
