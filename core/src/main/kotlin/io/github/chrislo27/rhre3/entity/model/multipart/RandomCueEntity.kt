package io.github.chrislo27.rhre3.entity.model.multipart

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Rectangle
import io.github.chrislo27.rhre3.entity.model.ILoadsSounds
import io.github.chrislo27.rhre3.entity.model.MultipartEntity
import io.github.chrislo27.rhre3.registry.GameRegistry
import io.github.chrislo27.rhre3.registry.datamodel.Datamodel
import io.github.chrislo27.rhre3.registry.datamodel.impl.CuePointer
import io.github.chrislo27.rhre3.registry.datamodel.impl.RandomCue
import io.github.chrislo27.rhre3.track.Remix
import io.github.chrislo27.toolboks.util.gdxutils.random


class RandomCueEntity(remix: Remix, datamodel: RandomCue)
    : MultipartEntity<RandomCue>(remix, datamodel) {

    init {
        bounds.width = datamodel.cues.map(CuePointer::duration).max() ?:
                error("Datamodel ${datamodel.id} has no internal cues")
    }

    private fun getPossibleObjects(): List<Datamodel> {
        return datamodel.cues.mapNotNull {
            GameRegistry.data.objectMap[it.id]
        }
    }

    private fun reroll() {
        internal.clear()
        internal +=
                getPossibleObjects().takeIf {
                    it.isNotEmpty()
                }?.random()?.createEntity(remix, null)?.also { ent ->
                    ent.updateBounds {
                        ent.bounds.x = this@RandomCueEntity.bounds.x
                        ent.bounds.width = this@RandomCueEntity.bounds.width
                        ent.bounds.y = this@RandomCueEntity.bounds.y
                    }
                } ?: error("No valid entities found from randomization for object ${datamodel.id}")
    }

    override fun getRenderColor(): Color {
        return remix.editor.theme.entities.pattern
    }

    override fun loadSounds() {
        super.loadSounds()
        getPossibleObjects()
                .map { it.createEntity(remix, null) }
                .forEach {
                    if (it is ILoadsSounds) {
                        it.loadSounds()
                    }
                }
    }

    override fun unloadSounds() {
        super.unloadSounds()
        getPossibleObjects()
                .map { it.createEntity(remix, null) }
                .forEach {
                    if (it is ILoadsSounds) {
                        it.unloadSounds()
                    }
                }
    }

    override fun updateInternalCache(oldBounds: Rectangle) {
        translateInternal(oldBounds)
        if (internal.isEmpty())
            reroll()
    }

    override fun onStart() {
        reroll()
        super.onStart()
    }

    override fun copy(remix: Remix): RandomCueEntity {
        return RandomCueEntity(remix, datamodel).also {
            it.updateBounds {
                it.bounds.set(this.bounds)
            }
            it.semitone = this.semitone
        }
    }
}