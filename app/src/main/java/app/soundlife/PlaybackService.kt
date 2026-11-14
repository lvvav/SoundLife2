package app.soundlife

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.IOException
import android.content.SharedPreferences
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

val CMD_LIKE = SessionCommand("app.soundlife.LIKE", Bundle.EMPTY)
val CMD_SHUFFLE = SessionCommand("app.soundlife.SHUFFLE", Bundle.EMPTY)

/**
 * Фоновый сервис плеера. Шторка и экран блокировки получают:
 * ♥ (лайк), 🔀 (перемешка) и стандартные ⏮ ⏯ ⏭.
 * Тумблер «поверх других приложений» отключает захват аудиофокуса.
 */
class PlaybackService : MediaSessionService() {
    /** Смешивание каналов для моно-звука. */
    private val monoMixer = ChannelMixingAudioProcessor()

    private fun applyMono() {
        val m = if (Settings.mono) ChannelMixingMatrix(2, 2, floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f))
        else ChannelMixingMatrix(2, 2, floatArrayOf(1f, 0f, 0f, 1f))
        monoMixer.putChannelMixingMatrix(m)
    }

    /** Рендереры с нашим процессором звука (вставляется в цепочку вывода). */
    private class MonoRenderers(ctx: Context, private val mixer: ChannelMixingAudioProcessor) : DefaultRenderersFactory(ctx) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(mixer))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
    }
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val main = Handler(Looper.getMainLooper())
    private var ticks = 0

    /** Раз в секунду, пока играет: считаем время прослушивания (как на сайте). */
    private val ticker = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                val dur = if (player.duration > 0) player.duration / 1000.0 else 0.0
                Stats.tick(player.currentMediaItem?.mediaId.orEmpty(), dur)
                if (++ticks % 30 == 0) Stats.saveIfDirty()
            }
            main.postDelayed(this, 1000)
        }
    }

    private val audioAttrs = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    // Ссылка хранится в поле, иначе слушатель настроек может быть собран GC
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Settings.KEY_MIX) {
            Settings.init(this)
            applyFocus()
        }
        if (key == Settings.KEY_MONO) {
            Settings.init(this)
            applyMono()
        }
    }

    private fun applyFocus() {
        // mix = true  -> фокус НЕ берём: музыка играет вместе с TikTok и т.п.
        // mix = false -> обычное поведение: чужое видео ставит музыку на паузу
        player.setAudioAttributes(audioAttrs, !Settings.mix)
    }

    private fun layout(): ImmutableList<CommandButton> {
        val liked = Favorites.has(player.currentMediaItem?.mediaId)
        val shuffle = player.shuffleModeEnabled
        val like = CommandButton.Builder()
            .setDisplayName(if (liked) "Убрать из любимого" else "В любимое")
            .setIconResId(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart)
            .setSessionCommand(CMD_LIKE)
            .build()
        val shuf = CommandButton.Builder()
            .setDisplayName(if (shuffle) "Перемешка: вкл" else "Перемешка: выкл")
            .setIconResId(if (shuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off)
            .setSessionCommand(CMD_SHUFFLE)
            .build()
        return ImmutableList.of(like, shuf)
    }

    private fun refresh() { session?.setCustomLayout(layout()) }

    override fun onCreate() {
        super.onCreate()
        Favorites.init(this)
        Settings.init(this)
        Auth.init(this)
        Stats.init(this)
        main.post(ticker)
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(MonoRenderers(this, monoMixer))
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ymDataFactory(this)))
            .build()
        applyFocus()
        applyMono()
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                refresh()
                NowBar.update(this@PlaybackService, player, session?.platformToken)
                Stats.leave(reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
                item?.mediaId?.let { Stats.started(it) }
            }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) = refresh()
            override fun onIsPlayingChanged(isPlaying: Boolean) = NowBar.update(this@PlaybackService, player, session?.platformToken)
            override fun onPlaybackStateChanged(state: Int) = NowBar.update(this@PlaybackService, player, session?.platformToken)
        })
        Favorites.onChange = { refresh() }
        prefs(this).registerOnSharedPreferenceChangeListener(prefListener)

        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession, controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(CMD_LIKE).add(CMD_SHUFFLE).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(cmds)
                        .setCustomLayout(layout())
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession, controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand, args: Bundle,
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CMD_LIKE.customAction -> player.currentMediaItem?.mediaId?.let { Favorites.toggle(it) }
                        CMD_SHUFFLE.customAction -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                    }
                    refresh()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
    }

    /** Адреса Яндекса временные: поток получаем в момент открытия (soundlife-ym://id → https-ссылка). */
    private fun ymDataFactory(ctx: Context) = ResolvingDataSource.Factory(
        DefaultDataSource.Factory(ctx),
        object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(spec: DataSpec): DataSpec {
                if (spec.uri.scheme != "soundlife-ym") return spec
                val url = try {
                    Ym.streamUrl(spec.uri.host.orEmpty())
                } catch (e: Exception) {
                    throw IOException(e.message)
                }
                return spec.buildUpon().setUri(Uri.parse(url)).build()
            }

            override fun resolveReportedUri(uri: Uri): Uri = uri
        },
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        Favorites.onChange = null
        main.removeCallbacks(ticker)
        Stats.saveIfDirty()
        session?.release(); session = null
        player.release()
        super.onDestroy()
    }
}
