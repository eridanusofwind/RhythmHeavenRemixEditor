package io.github.chrislo27.rhre3.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Align
import io.github.chrislo27.rhre3.PreferenceKeys
import io.github.chrislo27.rhre3.RHRE3
import io.github.chrislo27.rhre3.RHRE3Application
import io.github.chrislo27.rhre3.editor.Editor
import io.github.chrislo27.rhre3.editor.stage.EditorStage
import io.github.chrislo27.rhre3.entity.Entity
import io.github.chrislo27.rhre3.entity.model.ILoadsSounds
import io.github.chrislo27.rhre3.entity.model.ModelEntity
import io.github.chrislo27.rhre3.registry.GameRegistry
import io.github.chrislo27.rhre3.stage.GenericStage
import io.github.chrislo27.rhre3.stage.LoadingIcon
import io.github.chrislo27.rhre3.track.Remix
import io.github.chrislo27.rhre3.util.JavafxStub
import io.github.chrislo27.rhre3.util.attemptRememberDirectory
import io.github.chrislo27.rhre3.util.err.MusicLoadingException
import io.github.chrislo27.rhre3.util.getDefaultDirectory
import io.github.chrislo27.rhre3.util.persistDirectory
import io.github.chrislo27.toolboks.ToolboksScreen
import io.github.chrislo27.toolboks.i18n.Localization
import io.github.chrislo27.toolboks.registry.AssetRegistry
import io.github.chrislo27.toolboks.registry.ScreenRegistry
import io.github.chrislo27.toolboks.ui.*
import javafx.application.Platform
import javafx.stage.FileChooser
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import java.io.File
import java.util.zip.ZipFile
import javax.sound.midi.MidiSystem


class OpenRemixScreen(main: RHRE3Application)
    : ToolboksScreen<RHRE3Application, OpenRemixScreen>(main) {

    private val editorScreen: EditorScreen by lazy { ScreenRegistry.getNonNullAsType<EditorScreen>("editor") }
    private val editor: Editor
        get() = editorScreen.editor
    override val stage: Stage<OpenRemixScreen> = GenericStage(main.uiPalette, null, main.defaultCamera)

    @Volatile private var isChooserOpen = false
        set(value) {
            field = value
            stage as GenericStage
            stage.backButton.enabled = !isChooserOpen
        }
    @Volatile private var isLoading = false
    @Volatile private var isLoadingSounds = false

    private enum class RemixType {
        RHRE3, RHRE2, MIDI
    }

    private fun createFileChooser()
            = FileChooser().apply {
        this.initialDirectory = attemptRememberDirectory(main,
                                                         PreferenceKeys.FILE_CHOOSER_LOAD) ?: getDefaultDirectory()

        this.extensionFilters.clear()
        val filter = FileChooser.ExtensionFilter(Localization["screen.open.fileFilterSupported"],
                                                 "*.${RHRE3.REMIX_FILE_EXTENSION}",
                                                 "*.brhre2",
                                                 "*.mid")

        this.extensionFilters += filter
        this.selectedExtensionFilter = this.extensionFilters.first()

        this.title = Localization["screen.open.fileChooserTitle"]
    }

    private val loadButton: LoadButton
    private val mainLabel: TextLabel<OpenRemixScreen>
    @Volatile private var remix: Remix? = null
        set(value) {
            field = value
            loadButton.visible = field != null
        }

    private fun List<Entity>.applyFilter(): List<ModelEntity<*>> {
        return filter { entity -> entity is ILoadsSounds }
                .filterIsInstance<ModelEntity<*>>()
                .distinctBy { it.datamodel.id }
    }

    init {
        stage as GenericStage
        stage.titleIcon.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_folder"))
        stage.titleLabel.text = "screen.open.title"
        stage.backButton.visible = true
        stage.onBackButtonClick = {
            if (!isChooserOpen && !isLoading) {
                editor.remix.entities.applyFilter().forEach { entity ->
                    if (entity is ILoadsSounds) {
                        entity.loadSounds()
                    }
                }
                main.screen = ScreenRegistry.getNonNull("editor")
            }
        }

        val palette = main.uiPalette

        stage.centreStage.elements += object : TextLabel<OpenRemixScreen>(palette, stage.centreStage,
                                                                          stage.centreStage) {
            override fun frameUpdate(screen: OpenRemixScreen) {
                super.frameUpdate(screen)
                this.visible = isChooserOpen
            }
        }.apply {
            this.location.set(screenHeight = 0.125f)
            this.textAlign = Align.center
            this.isLocalizationKey = true
            this.text = "closeChooser"
            this.visible = false
        }
        mainLabel = TextLabel(palette, stage.centreStage, stage.centreStage).apply {
            this.location.set(screenHeight = 0.875f, screenY = 0.125f)
            this.textAlign = Align.center
            this.isLocalizationKey = false
            this.text = ""
        }
        stage.centreStage.elements += mainLabel
        stage.centreStage.elements += object : LoadingIcon<OpenRemixScreen>(palette, stage.centreStage) {
            override var visible: Boolean = true
                get() = super.visible && (isLoading || isLoadingSounds)
        }.apply {
            this.renderType = ImageLabel.ImageRendering.ASPECT_RATIO
            this.location.set(screenHeight = 0.125f, screenY = 0.125f / 2f)
        }
        loadButton = LoadButton(palette, stage.bottomStage, stage.bottomStage).apply {
            this.location.set(screenX = 0.25f, screenWidth = 0.5f)
            this.addLabel(TextLabel(palette, this, this.stage).apply {
                this.textAlign = Align.center
                this.text = "screen.open.button"
                this.isLocalizationKey = true
            })
            this.visible = false
        }
        stage.bottomStage.elements += loadButton

        stage.updatePositions()
    }

    override fun renderUpdate() {
        super.renderUpdate()

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            (stage as GenericStage).onBackButtonClick()
        }
    }

    @Synchronized
    private fun openPicker() {
        if (!isChooserOpen) {
            Platform.runLater {
                isChooserOpen = true
                val fileChooser = createFileChooser()
                val file: File? = fileChooser.showOpenDialog(null)
                isChooserOpen = false
                if (file != null && main.screen == this) {
                    isLoading = true
                    fileChooser.initialDirectory = if (!file.isDirectory) file.parentFile else file
                    persistDirectory(main, PreferenceKeys.FILE_CHOOSER_LOAD, fileChooser.initialDirectory)
                    launch(CommonPool) {
                        try {
                            remix = null
                            System.gc()

                            val newRemix = editor.createRemix()
                            val remixType: RemixType
                            val result = if (file.extension.equals("mid", ignoreCase = true)) {
                                remixType = RemixType.MIDI
                                Remix.fromMidiSequence(newRemix, MidiSystem.getSequence(file))
                            } else {
                                val zipFile = ZipFile(file)
                                val isRHRE2 = zipFile.getEntry("remix.json") == null

                                remixType = if (isRHRE2) RemixType.RHRE2 else RemixType.RHRE3

                                if (isRHRE2)
                                    Remix.unpackRHRE2(newRemix, zipFile)
                                else
                                    Remix.unpack(newRemix, zipFile)
                            }

                            val toLoad = result.remix.entities.applyFilter()
                            val toLoadIDs = toLoad.map { it.datamodel.id }
                            val toUnload = editor.remix.entities.applyFilter().filter { it.datamodel.id !in toLoadIDs }

                            val coroutine: Job = launch(CommonPool) {
                                isLoadingSounds = true
                                toUnload.forEach { entity ->
                                    if (entity is ILoadsSounds) {
                                        entity.unloadSounds()
                                    }
                                }
                                toLoad.forEach { entity ->
                                    if (entity is ILoadsSounds) {
                                        entity.loadSounds()
                                    }
                                }
                                isLoadingSounds = false
                            }

                            fun goodBad(str: String, bad: Boolean, badColour: String = "ORANGE"): String {
                                return if (bad) "[$badColour]$str[]" else "[LIGHT_GRAY]$str[]"
                            }

                            loadButton.alsoDo = {
                                runBlocking {
                                    coroutine.join()
                                }

                                if (!result.isAutosave) {
                                    editor.prepAutosaveFile(FileHandle(file))
                                }
                            }

                            remix = result.remix
                            val remix = remix!!
                            val missingAssets = result.missing
                            val databaseStr = if (remixType == RemixType.RHRE2)
                                goodBad(remix.version.toString(), true)
                            else
                                goodBad(remix.databaseVersion.toString(),
                                        remix.databaseVersion != GameRegistry.data.version)

                            mainLabel.text = ""

                            if (result.isAutosave) {
                                mainLabel.text += Localization["screen.open.autosave"] + "\n\n"
                            }

                            mainLabel.text += if (remixType == RemixType.MIDI) {
                                Localization["screen.open.info.midi"]
                            } else {
                                Localization["screen.open.info",
                                        goodBad(remix.version.toString(), remix.version != RHRE3.VERSION),
                                        databaseStr,
                                        goodBad(missingAssets.first.toString(), missingAssets.first > 0, "RED"),
                                        goodBad(if (remixType != RemixType.RHRE3) "?" else missingAssets.second.toString(),
                                                missingAssets.second > 0, "RED")]
                            }
                            if (GameRegistry.data.version < remix.databaseVersion) {
                                mainLabel.text += "\n" + Localization["screen.open.oldDatabase"]
                            } else if (remix.version < RHRE3.VERSION) {
                                mainLabel.text += "\n" +
                                        Localization[if (remixType == RemixType.RHRE2)
                                            "screen.open.rhre2Warning"
                                        else
                                            "screen.open.oldWarning"]
                            }
                            if (remix.version > RHRE3.VERSION) {
                                mainLabel.text += "\n" + Localization["screen.open.oldWarning2"]
                            }
                            isLoading = false
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            mainLabel.text = when (t) {
                                is MusicLoadingException -> t.getLocalizedText()
                                else -> Localization["screen.open.failed", t::class.java.canonicalName]
                            }
                            remix?.dispose()
                            remix = null
                            isLoading = false
                        }
                    }
                } else {
                    loadButton.alsoDo = {}
                    (stage as GenericStage).onBackButtonClick()
                }
            }
        }
    }

    override fun show() {
        super.show()
        openPicker()
        remix = null
    }

    override fun hide() {
        super.hide()
        mainLabel.text = ""
        remix = null
        loadButton.alsoDo = {}
        isLoadingSounds = false
    }

    override fun dispose() {
    }

    override fun tickUpdate() {
    }

    inner class LoadButton(palette: UIPalette, parent: UIElement<OpenRemixScreen>,
                           stage: Stage<OpenRemixScreen>)
        : Button<OpenRemixScreen>(palette, parent, stage) {

        @Volatile
        var alsoDo = {}

        override fun onLeftClick(xPercent: Float, yPercent: Float) {
            super.onLeftClick(xPercent, yPercent)
            val remix = remix ?: return
            editor.remix = remix
            alsoDo()
            editor.remix.recomputeCachedData()
            editor.stage.updateSelected(EditorStage.DirtyType.SEARCH_DIRTY)
            (this@OpenRemixScreen.stage as GenericStage).onBackButtonClick()
            Gdx.app.postRunnable {
                System.gc()
            }
        }
    }
}
