# JSON Object Definitions for Databasing

All `data.json` files for each game are a `DataObject`.

## `DataObject` structure
```json
{
  "id": "lowerCamelCaseID",
  "deprecatedIDs": [],
  "name": "Human-Readable Name",
  "requiresVersion": "v3.0.0",
  "objects": []
}
```

The `id` field is the name of the folder this JSON file is in.
It should be lowerCamelCase.

The `name` field is a properly Title Case capitalized name. This is in English.
If this game appears in multiple series, for example both in RHDS and Megamix,
the name of the series used should be in parentheses after the name of the game,
and the name of the minigame should use the most recent English-localized name.
Example: `Space Dance (Megamix)` and `Space Dance (GBA)`.

* Series' names are as follows:
  * Rhythm Tengoku - GBA
  * Rhythm Heaven (Gold) - DS
  * Rhythm Heaven Fever - Fever
  * Rhythm Heaven Megamix - Megamix

The `requiresVersion` field is the minimum version of the program needed to
parse this file.

The `objects` array is an array of various object types, which will be
explained below. It is **very important** that each object type contain
the `type` field, which is used by the JSON deserializer to determine
what object type to deserialize at runtime. This is called *polymorphism*.

`deprecatedIDs` is an array of old IDs that are no longer used, but refer
to this current object for older save files. This field is always the same
even in other object types, and is always present whenever there is an
`id` field **EXCEPT FOR** metadata-like objects like `CuePointerObject`.

## `CuePointerObject` structure
```json
{
  "id": "cueID",
  "beat": 1.0,
  // optional fields after this comment
  "duration": 1.0,
  "semitone": 0,
  "track": 0
}
```

This object is special. First of all, it **doesn't** have deprecated IDs
like other ID-based objects do. This is specifically an object that
"points" to a `CueObject`. It's basically the definition for spawning
cue objects. The usage of this object WILL vary from other object types,
and if you need more data on what's used/isn't used per object, see the
specific comments. For example, `PatternObject` (below) uses the base
implementation of `CuePointerObject`.

As a result, this object will never appear by itself. The fields are similar
to `CueObject`'s own cues, so you can look there. **Note that** the only
fields shown here ARE the ones it has, and not every field from `CueObject`
is inherited.

## `CueObject` structure
```json
{
  "type": "cue",
  "id": "dataObjectID/lowerCamelCaseID",
  "deprecatedIDs": [],
  "name": "human-readable cue name",
  "duration": 1.0,
  // optional fields after this comment
  "stretchable": false,
  "repitchable": false,
  "fileExtension": "ogg",

  "introSound": "other/ID",
  "endingSound": "other/ID2"
}
```

A `CueObject` defines a sound to be loaded by the editor. It also contains
metadata such as the duration and its abilities.

The `id` field is structured like this: `dataObjectID/lowerCamelCaseSoundFileName`.
If the parent data object's ID is `spaceDance`, and this sound's name is `turnRight`,
therefore this ID is `spaceDance/turnRight`. If there are more folders, you
should include them in the path separated by more forward slashes. Example:
`flipperFlop/appreciation/nice` is the sound file `nice.ogg` inside the folder `appreciation/`
which has a parent folder of `flipperFlop`.

The `name` field is a name. This is in English, except for the
romanization of foreign language words. Avoid capitals. The only
time you should be using capitals are for the following: proper noun I,
"Remix X", "Fever" (in the context of the game).
If this is syllabic (part of a longer sound cue), you should add a hyphen with
spaces surrounding it to break up words. The program will automatically convert these
into newlines. **Do not use newline characters.**
Example (First Contact): `alien - 1`, `alien - 2`, etc.

The `duration` field is the duration of the cue in beats.

The `stretchable` field is a boolean indicating if the cue can be stretched or not.

The `repitchable` field is a boolean indicating if the cue can have its pitch changed.

The `fileExtension` field is a string indicating the file extension. This is
**ONLY** for backwards compatibility, and will print out a warning when used.
The default format is Ogg Vorbis (file extension `ogg`).

The `introSound` and `endingSound` fields are optional string IDs to indicate
sounds that should be played at the beginning and end of the main sound cue,
respectively. Cues that are either intro or ending sounds will not be pickable.
These are useful for cues like Glee Club, where there is an intro singing sound,
and an ending sound (mouth shut).

### `TempoBasedCueObject` subtype
```json
{
  "type": "tempoBasedCue",
  // etc...
  "baseBpm": 120.0
}
```

This cue subtype has a `baseBpm` field. The sound effect will be pitched
accordingly during playback based on the current tempo relative to the value
set in `baseBpm`. The duration of the sound relative to the original duration
will also affect the pitch (only applicable if the cue is `stretchable`).

### `FillbotsFillCueObject` subtype
```json
{
  "type": "fillbotsFillCue",
  // etc...
}
```

This simply indicates that the pitch should be modified during playback,
as it does in Fillbots for the filling sound effect.

### `LoopingCueObject` subtype
```json
{
  "type": "loopingCue",
  // etc...
  // optional fields after this comment
  "introSound": "soundCueID"
}
```

This cue subtype loops. It has an optional `introSound` field
which is the sound cue ID to play **once** at the start of the cue. This
is used for cues like Screwbot Factory's "whirring" noise.

## `PatternObject` and `PatternCueObject` structure
```json
{
  "type": "pattern",
  "id": "gameID_lowerCamelCaseID",
  "deprecatedIDs": [],
  "name": "human-readable pattern name",
  "cues": [ // array of CuePointerObjects
    {
      // see CuePointerObject
    }
  ]
}
```

A `PatternObject` is a series of cues bundled together. They contain default
settings for each cue, such as its position (in beats and track number), its
pitch adjustment (`semitone`), and `duration` (defaults to original duration).

The `id` field is structured like this: `dataObjectID_lowerCamelCase`.
If the parent data object's ID is `spaceDance`, and this pattern's ID is `turnRight`,
therefore this ID is `spaceDance_turnRight`. You'll notice this is similar to
the cue ID naming convention, but it always has underscores and never has forward
slashes.

The array of `CuePointerObject`s uses the standard cue pointer object fields.

## `EquidistantObject` structure

```json
{
  "type": "equidistant",
  "id": "gameID_lowerCamelCaseID",
  "deprecatedIDs": [],
  "name": "human-readable name",
  "distance": 1.0,
  "stretchable": false,
  "cues": [ // ORDERED array of CuePointerObjects
    {
      // see CuePointerObject
      // fields after this comment DON'T EXIST
      "beat", "duration"
    }
  ]
}
```

The `EquidistantObject` represents a pattern where each cue is
*equidistant* from each other. That is to say, if the `distance` is 2.0
beats, each cue will be 2.0 beats apart. The `stretchable` field indicates
if this entity is stretchable or not (ex: Bouncy Road).

See `PatternObject` for the ID and name structure.

The `CuePointerObjects` used *are in order* and do **NOT** have these fields:
`beat`, `duration`.

## `KeepTheBeatObject` structure

```json
{
  "type": "keepTheBeat",
  "id": "gameID_lowerCamelCaseID",
  "deprecatedIDs": [],
  "name": "human-readable name",
  "duration": 2.0,
  "cues": [ // ORDERED array of CuePointerObjects
    {
      // see CuePointerObject
    }
  ]
}
```

The `KeepTheBeatObject` is similar to the `EquidistantObject`, but it
repeats over and over (up to its `duration`). This is for things like
Lockstep marching patterns, or Flipper-Flop.

See `PatternObject` for the ID and name structure.

The `CuePointerObjects` used *are in order* and are not changed.

## `CallAndResponseObject` structure

```json
{
  "type": "callAndResponse",
  "id": "gameID_lowerCamelCaseID",
  "deprecatedIDs": [],
  "name": "human-readable name",
  "duration": 2.0,
  "stretchable": false,
  "counterparts": [
    ["firstID", "secondID"],
    ["thirdID", "fourthID"],
    ["callID", "responseID"]
  ],
  // optional fields after this comment
  "middle": [ // ORDERED array of CuePointerObjects
    {
      // see CuePointerObject
    }
  ],
  "end": [ // ORDERED array of CuePointerObjects
    {
      // see CuePointerObject
    }
  ],
}
```

The `CallAndResponseObject` is the keystone object for "follow me" games
like First Contact, Working Dough, Drummer Duel, or Wizard Waltz. It
affects all cues below it. See this image: ![Call and Response object diagram](callResponseUmbrella.png)

The `counterparts` array is an array of string arrays. Each internal string array
has *two or more* cue IDs in it. The first ID is the call ID, and the
second (or third, fourth, etc) IDs are the possible response IDs for that
call ID. First Contact will definitely use multiple response IDs for its
randomization of speech.

See `PatternObject` for the ID and name structure.

The `CuePointerObjects` used *are in order* and are not changed. `middle`
is an array of cues that go in between sections. This should be used
in games like First Contact as the passover cue. `end` is an array
of cues put at the end, used for games like First Contact or Rhythm
Tweezers as the "success" cue.

The `duration` field is the length of **one section** of the object.

The `stretchable` field is a boolean indicating whether or not it is stretchable.

## `RandomCueObject` structure

```json
{
  "type": "randomCue",
  "id": "gameID_lowerCamelCaseID",
  "deprecatedIDs": [],
  "name": "human-readable name, usually 'random X'",
  "cues": [ // array of CuePointerObjects
    {
      // see CuePointerObject
      // "beat" field ignored
    }
  ]
}
```

The `RandomCueObject` is like a pattern except it only chooses one of the
cues in the `cues` array at random **when first placed**.
The `CuePointerObject` is unchanged, but the `beat` field inside
will be ignored (always zero).

See `PatternObject` for the `id` and `name` fields.