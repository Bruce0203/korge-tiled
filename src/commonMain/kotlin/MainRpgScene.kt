import korlibs.time.*
import korlibs.event.*
import korlibs.korge.scene.*
import korlibs.korge.tiled.*
import korlibs.korge.view.*
import korlibs.korge.view.animation.*
import korlibs.korge.view.camera.*
import korlibs.korge.view.filter.*
import korlibs.image.atlas.*
import korlibs.image.format.*
import korlibs.image.tiles.tiled.*
import korlibs.io.file.std.*
import korlibs.korge.view.collision.*
import korlibs.math.geom.*
import korlibs.math.geom.collider.*

class MainRpgScene : ScaledScene(512, 512) {
    override suspend fun SContainer.sceneMain() {
        val rootSceneContainer = sceneContainer()

        rootSceneContainer.changeTo({ RpgIngameScene() },
            transition = MaskTransition(transition = TransitionFilter.Transition.CIRCULAR, reversed = false, filtering = true),
            //transition = AlphaTransition,
            time = 0.5.seconds
        )
    }
}

class RpgIngameScene : Scene() {
    val atlas = MutableAtlasUnit(2048, 2048)
    lateinit var tilemap: TiledMap
    lateinit var characters: ImageDataContainer

    override suspend fun SContainer.sceneInit() {
        val sw = Stopwatch().start()

		println("start resources loading...")

        tilemap = resourcesVfs["BasicTilemap/untitled.tmx"].readTiledMap(atlas = atlas)
        characters = resourcesVfs["vampire.ase"].readImageDataContainer(ASE.toProps(), atlas = atlas)

        println("loaded resources in ${sw.elapsed}")
    }

    override suspend fun SContainer.sceneMain() {
        container {
            scale(2.0)

            lateinit var character: ImageDataView
            lateinit var tiledMapView: TiledMapView

            val cameraContainer = cameraContainer(
                Size(256.0, 256.0), clip = true,
                block = {
                    clampToBounds = true
                }
            ) {
                tiledMapView = tiledMapView(tilemap, smoothing = false, showShapes = false)
				tiledMapView.filter = IdentityFilter(false)

                println("tiledMapView[\"start\"]=${tiledMapView["start"].firstOrNull}")
                val npcs = tiledMapView.tiledMap.data.getObjectByType("npc")
                for (obj in tiledMapView.tiledMap.data.objectLayers.objects) {
                    println("- obj = $obj")
                }
                println("NPCS=$npcs")
                println(tiledMapView.firstDescendantWith { it.getTiledPropString("type") == "start" })
                val startPos = tiledMapView["start"].firstOrNull?.pos ?: Point(50, 50)
                val charactersLayer = tiledMapView["characters"].first as Container

                println("charactersLayer=$charactersLayer")

                // Keep zIndex in sync with y
                charactersLayer.addUpdater {
                    charactersLayer.forEachChild {
                        it.zIndex = it.y
                    }
                }

                for (obj in tiledMapView.tiledMap.data.getObjectByType("npc")) {
                    val npc = charactersLayer.imageDataView(
                        characters[obj.str("skin")],
                        "down",
                        playing = false,
                        smoothing = false
                    ) {
                        xy(obj.x, obj.y)
                    }
                }
                character =
                    charactersLayer.imageDataView(characters["vampire"], "right", playing = false, smoothing = false) {
                        xy(startPos)
                    }
            }

            cameraContainer.cameraViewportBounds = tiledMapView.getLocalBounds()

            stage!!.controlWithKeyboard(character, tiledMapView)

            cameraContainer.follow(character, setImmediately = true)

            //cameraContainer.tweenCamera(cameraContainer.getCameraRect(Rectangle(200, 200, 100, 100)))
        }
    }
}

fun Stage.controlWithKeyboard(
    char: ImageDataView,
    collider: HitTestable,
    up: Key = Key.UP,
    right: Key = Key.RIGHT,
    down: Key = Key.DOWN,
    left: Key = Key.LEFT,
) {
	addUpdater { dt ->
		val speed = 2.0 * (dt / 16.0.milliseconds)
		var dx = 0.0
		var dy = 0.0
		val pressingLeft = keys[left]
		val pressingRight = keys[right]
		val pressingUp = keys[up]
		val pressingDown = keys[down]
		if (pressingLeft) dx = -1.0
		if (pressingRight) dx = +1.0
		if (pressingUp) dy = -1.0
		if (pressingDown) dy = +1.0
		if (dx != 0.0 || dy != 0.0) {
			val dpos = Point(dx, dy).normalized * speed
			char.moveWithHitTestable(collider, dpos.xD, dpos.yD)
		}
		char.animation = when {
			pressingLeft -> "left"
			pressingRight -> "right"
			pressingUp -> "up"
			pressingDown -> "down"
			else -> char.animation
		}
		if (pressingLeft || pressingRight || pressingUp || pressingDown) {
			char.play()
		} else {
			char.stop()
			char.rewind()
		}
	}
}
