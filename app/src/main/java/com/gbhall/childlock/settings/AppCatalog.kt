package com.gbhall.childlock.settings

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Which auto-lock moments make sense for a given app, and the best default.
 * Curated packages first, then the category the app declares to Android,
 * then a keyword guess, then "everything" for the unknown.
 */
object AppCatalog {
    enum class Kind { VIDEO, CALLING, GAME, AUDIO, SOCIAL, OTHER }

    data class Profile(val kind: Kind, val options: List<AutoLockTrigger>, val default: AutoLockTrigger)

    private val VIDEO_OPTIONS = listOf(AutoLockTrigger.FULLSCREEN_PLAYBACK, AutoLockTrigger.PLAYBACK, AutoLockTrigger.OPEN)
    private val CALL_OPTIONS = listOf(AutoLockTrigger.VIDEO_CALL, AutoLockTrigger.VOICE_CALL, AutoLockTrigger.CALL, AutoLockTrigger.OPEN)
    private val GAME_OPTIONS = listOf(AutoLockTrigger.OPEN)
    private val AUDIO_OPTIONS = listOf(AutoLockTrigger.PLAYBACK, AutoLockTrigger.OPEN)
    private val ALL_OPTIONS = listOf(
        AutoLockTrigger.OPEN, AutoLockTrigger.FULLSCREEN_PLAYBACK, AutoLockTrigger.PLAYBACK,
        AutoLockTrigger.VIDEO_CALL, AutoLockTrigger.VOICE_CALL, AutoLockTrigger.CALL,
    )

    val VIDEO = Profile(Kind.VIDEO, VIDEO_OPTIONS, AutoLockTrigger.FULLSCREEN_PLAYBACK)
    val CALLING = Profile(Kind.CALLING, CALL_OPTIONS, AutoLockTrigger.VIDEO_CALL)
    val GAME = Profile(Kind.GAME, GAME_OPTIONS, AutoLockTrigger.OPEN)
    val AUDIO = Profile(Kind.AUDIO, AUDIO_OPTIONS, AutoLockTrigger.PLAYBACK)
    val OTHER = Profile(Kind.OTHER, ALL_OPTIONS, AutoLockTrigger.OPEN)
    val SOCIAL = Profile(Kind.SOCIAL, listOf(AutoLockTrigger.OPEN, AutoLockTrigger.VIDEO_CALL, AutoLockTrigger.CALL), AutoLockTrigger.OPEN)
    val PLAYER = Profile(Kind.VIDEO, VIDEO_OPTIONS, AutoLockTrigger.PLAYBACK)
    val LIBRARY = Profile(Kind.VIDEO, VIDEO_OPTIONS, AutoLockTrigger.OPEN)

    /** Well-known packages. Kept small and confident; the category fallback covers the rest. */
    val curated: Map<String, Profile> = mapOf(
        // Video
        "com.google.android.youtube" to VIDEO,
        "com.google.android.apps.youtube.kids" to VIDEO,
        "com.netflix.mediaclient" to VIDEO,
        "bbc.iplayer.android" to VIDEO,
        "air.ITVMobilePlayer" to VIDEO,
        "com.channel4.ondemand" to VIDEO,
        "com.disney.disneyplus" to VIDEO,
        "com.amazon.avod.thirdpartyclient" to VIDEO,
        "com.nowtv.uk" to VIDEO,
        "com.bskyb.nowtv.beta" to VIDEO,
        "com.bskyb.skykids" to VIDEO,
        "com.mxtech.videoplayer.ad" to PLAYER,
        "com.amazon.tahoe" to LIBRARY,
        "com.bskyb.skygo" to VIDEO,
        "tv.twitch.android.app" to VIDEO,
        "com.plexapp.android" to VIDEO,
        "org.videolan.vlc" to PLAYER,
        "uk.co.bbc.cbeebiesplaytime" to VIDEO,
        "org.pbskids.video" to VIDEO,
        "com.google.android.apps.photos" to LIBRARY,
        "com.apple.atve.androidtv.appletv" to VIDEO,
        "com.hulu.plus" to VIDEO,
        "com.wbd.stream" to VIDEO,
        // Calling
        "com.whatsapp" to CALLING,
        "com.whatsapp.w4b" to CALLING,
        "com.facebook.orca" to CALLING,
        "com.facebook.mlite" to CALLING,
        "com.google.android.apps.tachyon" to CALLING,
        "com.google.android.apps.meetings" to CALLING,
        "com.microsoft.teams" to CALLING,
        "us.zoom.videomeetings" to CALLING,
        "com.skype.raider" to CALLING,
        "org.thoughtcrime.securesms" to CALLING,
        "org.telegram.messenger" to CALLING,
        "com.viber.voip" to CALLING,
        "com.discord" to CALLING,
        "com.facebook.talk" to CALLING,
        "org.jitsi.meet" to CALLING,
        "com.cisco.webex.meetings" to CALLING,
        "com.instagram.android" to SOCIAL,
        "com.snapchat.android" to SOCIAL,
        "com.facebook.katana" to SOCIAL,
        "com.google.android.dialer" to CALLING,
        "com.samsung.android.dialer" to CALLING,
        // Audio
        "com.spotify.music" to AUDIO,
        "com.google.android.apps.youtube.music" to AUDIO,
        "com.amazon.mp3" to AUDIO,
        "com.bbc.sounds" to AUDIO,
        "com.audible.application" to AUDIO,
        "com.spotify.kids" to AUDIO,
        // Games and kids' apps
        "com.roblox.client" to GAME,
        "com.mojang.minecraftpe" to GAME,
        "com.tocaboca.tocalifeworld" to GAME,
        "com.king.candycrushsaga" to GAME,
        "org.khankids.android" to GAME,
        "uk.co.bbc.cbeebiesplaytimeisland" to GAME,
        "uk.co.bbc.cbeebiesgoexplore" to GAME,
        "uk.co.bbc.cbeebiesstorytime" to GAME,
    )

    fun profile(context: Context, packageName: String): Profile {
        curated[packageName]?.let { return it }
        val category = try {
            context.packageManager.getApplicationInfo(packageName, 0).category
        } catch (e: Exception) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        when (category) {
            ApplicationInfo.CATEGORY_VIDEO -> return VIDEO
            ApplicationInfo.CATEGORY_GAME -> return GAME
            ApplicationInfo.CATEGORY_AUDIO -> return AUDIO
            ApplicationInfo.CATEGORY_SOCIAL -> return SOCIAL
        }
        val p = packageName.lowercase()
        if (listOf("whatsapp", "tachyon", "teams", "zoom", "skype", "signal", "telegram", "viber", "messenger", "dialer", "telecom", "voip", "webex", "jitsi").any { it in p }) return CALLING
        if (listOf("youtube", "netflix", "iplayer", "disney", "primevideo", "avod", "twitch", "plex", "vlc", "itv", "channel4", "hulu", "hbo", "paramount", "peacock", "cbeebies", "pbskids", "nowtv", "skygo").any { it in p }) return VIDEO
        if (listOf("game", "games", "rovio", "supercell", "king.", "toca", "lego", "sago", "minecraft", "roblox").any { it in p }) return GAME
        return OTHER
    }

    /** Options for a rule that already exists: always include its current choice so it stays editable. */
    fun optionsFor(profile: Profile, current: AutoLockTrigger?): List<AutoLockTrigger> =
        if (current == null || current in profile.options) profile.options else profile.options + current
}
