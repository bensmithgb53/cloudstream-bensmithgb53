package com.lagradost.cloudstream3.ui.player

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.animation.addListener
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.text.toSpanned
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Format.NO_VALUE
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.ui.PlayerNotificationManager.EXTRA_INSTANCE_ID
import androidx.media3.ui.PlayerNotificationManager.MediaDescriptionAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.databinding.DialogOnlineSubtitlesBinding
import com.lagradost.cloudstream3.databinding.FragmentPlayerBinding
import com.lagradost.cloudstream3.databinding.PlayerSelectSourceAndSubsBinding
import com.lagradost.cloudstream3.databinding.PlayerSelectTracksBinding
import com.lagradost.cloudstream3.isAnimeOp
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.isLiveStream
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.subtitleProviders
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup
import com.lagradost.cloudstream3.ui.player.CS3IPlayer.Companion.preferredAudioTrackLanguage
import com.lagradost.cloudstream3.ui.player.CustomDecoder.Companion.updateForcedEncoding
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper.Companion.toSubtitleMimeType
import com.lagradost.cloudstream3.ui.player.source_priority.LinkSource
import com.lagradost.cloudstream3.ui.player.source_priority.ProfileSettings
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper.getLinkPriority
import com.lagradost.cloudstream3.ui.player.source_priority.QualityProfileDialog
import com.lagradost.cloudstream3.ui.result.ACTION_CLICK_DEFAULT
import com.lagradost.cloudstream3.ui.result.EpisodeAdapter
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.result.ResultFragment.bindLogo
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.SyncViewModel
import com.lagradost.cloudstream3.ui.setLinearListLayout
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.subtitles.SUBTITLE_AUTO_SELECT_KEY
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.getAutoSelectLanguageTagIETF
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragmentDialog2
import com.lagradost.cloudstream3.utils.AppContextUtils.getShortSeasonText
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.AppContextUtils.sortSubs
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToEnglishLanguageName
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToLanguageName
import com.lagradost.cloudstream3.utils.SubtitleHelper.languages
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideProgress
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.showProgress
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.downloader.DownloadUtils.getImageBitmapFromUrl
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp
import com.lagradost.safefile.SafeFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Serializable
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
class GeneratorPlayer : FullScreenPlayer() {
    companion object {
        const val NOTIFICATION_ID = 2326
        const val CHANNEL_ID = 7340
        const val STOP_ACTION = "stopcs3"

        private val generators = ConcurrentHashMap<String, VideoGenerator<*>>()
        fun newInstance(
            generator: VideoGenerator<*>,
            index: Int,
            syncData: HashMap<String, String>? = null
        ): Bundle {
            Log.i(TAG, "newInstance = $syncData")
            val uuid = UUID.randomUUID().toString()
            generators[uuid] = generator
            return Bundle().apply {
                putString("uuid", uuid)
                putInt("index", index)
                if (syncData != null) putSerializable("syncData", syncData)
            }
        }

        val subsProviders = subtitleProviders
        val subsProvidersIsActive
            get() = subsProviders.isNotEmpty()
    }


    private var limitTitle = 0
    private var showTitle = false
    private var showName = false
    private var showResolution = false
    private var showMediaInfo = false

    private lateinit var viewModel: PlayerGeneratorViewModel //by activityViewModels()
    private lateinit var sync: SyncViewModel

    private var currentSelectedLink: Pair<ExtractorLink?, ExtractorUri?>? = null
    private var currentSelectedSubtitles: SubtitleData? = null
    private val currentMeta: Any? get() = viewModel.state.generatorState?.meta
    private val nextMeta: Any? get() = viewModel.state.generatorState?.nextMeta

    private var isPlayerActive: AtomicBoolean = AtomicBoolean(false)
    private var isNextEpisode: Boolean = false // this is used to reset the watch time

    private var preferredAutoSelectSubtitles: String? = null // null means do nothing, "" means none
    private val allMeta: List<ResultEpisode>?
        get() = viewModel.state.generatorState?.allMeta?.filterIsInstance<ResultEpisode>()
            ?.map { episode ->
                // Refresh all the episodes watch duration
                getViewPos(episode.id)?.let { data ->
                    episode.copy(position = data.position, duration = data.duration)
                } ?: episode
            }

    private fun setSubtitles(subtitle: SubtitleData?, userInitiated: Boolean): Boolean {
        // If subtitle is changed and user initiated -> Save the language
        if (subtitle != currentSelectedSubtitles && userInitiated) {
            val subtitleLanguageTagIETF = if (subtitle == null) {
                "" // -> No Subtitles
            } else {
                subtitle.getIETF_tag()
            }

            if (subtitleLanguageTagIETF != null) {
                Log.i(TAG, "Set SUBTITLE_AUTO_SELECT_KEY to '$subtitleLanguageTagIETF'")
                setKey(SUBTITLE_AUTO_SELECT_KEY, subtitleLanguageTagIETF)
                preferredAutoSelectSubtitles = subtitleLanguageTagIETF
            }
        }

        currentSelectedSubtitles = subtitle
        //Log.i(TAG, "setSubtitles = $subtitle")
        return player.setPreferredSubtitles(subtitle)
    }

    override fun embeddedSubtitlesFetched(subtitles: List<SubtitleData>) {
        viewModel.addSubtitles(subtitles.toSet())
    }

    override fun onTracksInfoChanged() {
        val tracks = player.getVideoTracks()
        playerBinding?.playerTracksBtt?.isVisible =
            tracks.allVideoTracks.size > 1 || tracks.allAudioTracks.size > 1
        // Only set the preferred language if it is available.
        // Otherwise, it may give some users audio track init failed!
        if (tracks.allAudioTracks.any { it.language == preferredAudioTrackLanguage }) {
            player.setPreferredAudioTrack(preferredAudioTrackLanguage)
        }
        updatePlayerInfo()
    }

    override fun playerStatusChanged() {
        super.playerStatusChanged()
        if (player.getIsPlaying()) {
            viewModel.forceClearCache = false
        }
    }

    private fun noSubtitles(): Boolean {
        return setSubtitles(null, true)
    }

    private fun getPos(): Long {
        val durPos = getViewPos(viewModel.state.generatorState?.id) ?: return 0L
        if (durPos.duration == 0L) return 0L
        if (durPos.position * 100L / durPos.duration > 95L) {
            return 0L
        }
        return durPos.position
    }

    private var currentVerifyLink: Job? = null

    private fun loadExtractorJob(extractorLink: ExtractorLink?) {
        currentVerifyLink?.cancel()

        extractorLink?.let { link ->
            currentVerifyLink = ioSafe {
                if (link.extractorData != null) {
                    getApiFromNameNull(link.source)?.extractorVerifierJob(link.extractorData)
                }
            }
        }
    }

    // https://github.com/androidx/media/blob/main/libraries/ui/src/main/java/androidx/media3/ui/PlayerNotificationManager.java#L1517
    private fun createBroadcastIntent(
        action: String,
        context: Context,
        instanceId: Int
    ): PendingIntent {
        val intent: Intent = Intent(action).setPackage(context.packageName)
        intent.putExtra(EXTRA_INSTANCE_ID, instanceId)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else PendingIntent.FLAG_UPDATE_CURRENT

        return PendingIntent.getBroadcast(context, instanceId, intent, pendingFlags)
    }

    private var cachedPlayerNotificationManager: PlayerNotificationManager? = null

    private fun getMediaNotification(context: Context): PlayerNotificationManager {
        val cache = cachedPlayerNotificationManager
        if (cache != null) return cache
        return PlayerNotificationManager.Builder(
            context,
            NOTIFICATION_ID,
            CHANNEL_ID.toString()
        )
            .setChannelNameResourceId(R.string.player_notification_channel_name)
            .setChannelDescriptionResourceId(R.string.player_notification_channel_description)
            .setMediaDescriptionAdapter(object : MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence {
                    return when (val meta = currentMeta) {
                        is ResultEpisode -> {
                            meta.headerName
                        }

                        is ExtractorUri -> {
                            meta.headerName ?: meta.name
                        }

                        else -> null
                    } ?: "Unknown"
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    // Open the app without creating a new task to resume playback seamlessly
                    return PendingIntentCompat.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        0,
                        false
                    )
                }

                override fun getCurrentContentText(player: Player): CharSequence? {
                    return when (val meta = currentMeta) {
                        is ResultEpisode -> {
                            meta.name
                        }

                        is ExtractorUri -> {
                            if (meta.headerName == null) {
                                null
                            } else {
                                meta.name
                            }
                        }

                        else -> null
                    }
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? {
                    ioSafe {
                        val url = when (val meta = currentMeta) {
                            is ResultEpisode -> {
                                meta.poster
                            }

                            else -> null
                        }
                        // if we have a poster url try with it first
                        if (url != null) {
                            val urlBitmap = context.getImageBitmapFromUrl(url)
                            if (urlBitmap != null) {
                                callback.onBitmap(urlBitmap)
                                return@ioSafe
                            }
                        }

                        // retry several times with a preview in case the preview generator is slow
                        repeat(10) {
                            val preview = this@GeneratorPlayer.player.getPreview(0.5f)
                            if (preview != null) {
                                callback.onBitmap(preview)
                                return@repeat
                            }
                            delay(1000L)
                        }
                    }

                    // return null as we want to use the callback
                    return null
                }
            }).setCustomActionReceiver(object : PlayerNotificationManager.CustomActionReceiver {
                // we have to use a custom action for stop if we want to exit the player instead of just stopping playback
                override fun createCustomActions(
                    context: Context,
                    instanceId: Int
                ): MutableMap<String, NotificationCompat.Action> {
                    return mutableMapOf(
                        STOP_ACTION to NotificationCompat.Action(
                            R.drawable.baseline_stop_24,
                            @SuppressLint("PrivateResource")
                            context.getString(androidx.media3.ui.R.string.exo_controls_stop_description),
                            createBroadcastIntent(STOP_ACTION, context, instanceId)
                        )
                    )
                }

                override fun getCustomActions(player: Player): MutableList<String> {
                    return mutableListOf(STOP_ACTION)
                }

                override fun onCustomAction(player: Player, action: String, intent: Intent) {
                    when (action) {
                        STOP_ACTION -> {
                            exitPlayer()
                        }
                    }
                }
            })
            .setPlayActionIconResourceId(R.drawable.ic_baseline_play_arrow_24)
            .setPauseActionIconResourceId(R.drawable.netflix_pause)
            .setSmallIconResourceId(R.drawable.baseline_headphones_24)
            .setStopActionIconResourceId(R.drawable.baseline_stop_24)
            .setRewindActionIconResourceId(R.drawable.go_back_30)
            .setFastForwardActionIconResourceId(R.drawable.go_forward_30)
            .setNextActionIconResourceId(R.drawable.ic_baseline_skip_next_24)
            .setPreviousActionIconResourceId(R.drawable.baseline_skip_previous_24)
            .build().apply {
                setColorized(true) // Color
                setUseChronometer(true) // Seekbar

                // Don't show the prev episode button
                setUsePreviousAction(false)
                setUsePreviousActionInCompactView(false)

                // Don't show the next episode button
                setUseNextAction(false)
                setUseNextActionInCompactView(false)

                // Show the skip 30s in both modes
                setUseFastForwardAction(true)
                setUseFastForwardActionInCompactView(true)

                // Only show rewind in expanded
                setUseRewindAction(true)
                setUseFastForwardActionInCompactView(false)

                // Use custom stop action
                setUseStopAction(false)
            }
            .also { cachedPlayerNotificationManager = it }
    }

    override fun playerUpdated(player: Any?) {
        super.playerUpdated(player)

        // Cancel the notification when released
        if (player == null) {
            cachedPlayerNotificationManager?.setPlayer(null)
            cachedPlayerNotificationManager = null
            return
        }

        // setup the notification when starting the player
        if (player is ExoPlayer) {
            val ctx = context ?: return
            getMediaNotification(ctx).apply {
                setPlayer(player)
                mMediaSession?.platformToken?.let {
                    setMediaSessionToken(it)
                }
            }
        }
    }

    override fun onDownload(event: DownloadEvent) {
        super.onDownload(event)
        showDownloadProgress(event)
    }

    private fun showDownloadProgress(event: DownloadEvent) {
        activity?.runOnUiThread {
            playerBinding?.downloadedProgress?.apply {
                val indeterminate = event.totalBytes <= 0 || event.downloadedBytes <= 0
                isIndeterminate = indeterminate
                if (!indeterminate) {
                    max = (event.totalBytes / 1000).toInt()
                    progress = (event.downloadedBytes / 1000).toInt()
                }
            }
            playerBinding?.downloadedProgressText.setText(
                txt(
                    R.string.download_size_format,
                    android.text.format.Formatter.formatShortFileSize(
                        context,
                        event.downloadedBytes
                    ),
                    android.text.format.Formatter.formatShortFileSize(context, event.totalBytes)
                )
            )
            val downloadSpeed =
                android.text.format.Formatter.formatShortFileSize(context, event.downloadSpeed)
            playerBinding?.downloadedProgressSpeedText?.text =
                    // todo string fmt
                event.connections?.let { connections ->
                    "%s/s - %d Connections".format(downloadSpeed, connections)
                } ?: downloadSpeed

            // don't display when done
            playerBinding?.downloadedProgressSpeedText?.isGone =
                event.downloadedBytes != 0L && event.downloadedBytes - 1024 >= event.totalBytes
        }
    }

    private fun loadLink(link: VideoLink?, sameEpisode: Boolean) {
        if (link == null) return
        isPlayerActive.set(true)
        // manage UI
        binding?.playerLoadingOverlay?.isVisible = false
        val isTorrent =
            link.first?.type == ExtractorLinkType.MAGNET || link.first?.type == ExtractorLinkType.TORRENT

        playerBinding?.downloadHeader?.isVisible = false
        playerBinding?.downloadHeaderToggle?.isVisible = isTorrent
        if (!isLayout(PHONE)) {
            playerBinding?.downloadBothHeader?.isVisible = isTorrent
        }

        showDownloadProgress(DownloadEvent(0, 0, 0, null))

        // uiReset() // Removed due to UX

        currentSelectedLink = link
        //  setEpisodes(viewModel.getAllMeta() ?: emptyList())
        setPlayerDimen(null)
        setTitle()
        if (!sameEpisode)
            hasRequestedStamps = false

        loadExtractorJob(link.first)
        // load player
        context?.let { ctx ->
            val (url, uri) = link
            val subtitles = viewModel.state.subtitles
            player.loadPlayer(
                ctx,
                sameEpisode,
                url,
                uri,
                startPosition = if (sameEpisode) null else {
                    if (isNextEpisode) 0L else getPos()
                },
                subtitles,
                (if (sameEpisode) currentSelectedSubtitles else null) ?: getAutoSelectSubtitle(
                    subtitles, settings = true, downloads = true
                ),
                preview = true
            )
        }

        if (!sameEpisode) {
            player.addTimeStamps(emptyList()) // clear stamps
            // Resets subtitle delay, as we watch some other content
            player.setSubtitleOffset(0)
        }
    }

    data class TempMetaData(
        var episode: Int? = null,
        var season: Int? = null,
        var name: String? = null,
        var imdbId: String? = null,
    )

    private fun getMetaData(): TempMetaData {
        val meta = TempMetaData()

        when (val newMeta = currentMeta) {
            is ResultEpisode -> {
                if (!newMeta.tvType.isMovieType()) {
                    meta.episode = newMeta.episode
                    meta.season = newMeta.season
                }
                meta.name = newMeta.headerName
            }

            is ExtractorUri -> {
                if (newMeta.tvType?.isMovieType() == false) {
                    meta.episode = newMeta.episode
                    meta.season = newMeta.season
                }
                meta.name = newMeta.headerName
            }
        }
        return meta
    }

    fun getName(entry: AbstractSubtitleEntities.SubtitleEntity, withLanguage: Boolean): String {
        if (entry.lang.isBlank() || !withLanguage) {
            return entry.name
        }
        val language = fromTagToLanguageName(entry.lang.trim()) ?: entry.lang
        return "$language ${entry.name}"
    }

    private fun configureDialogHalfScreen(dialog: Dialog) {
        dialog.window?.apply {
            val params = attributes
            params.width = (resources.displayMetrics.widthPixels * 0.6f).toInt() // 60% of screen
            params.x = (resources.displayMetrics.widthPixels * 0.4f).toInt() // Position on right (40% from left)
            attributes = params
        }
    }

    override fun openOnlineSubPicker(
        context: Context, loadResponse: LoadResponse?, dismissCallback: (() -> Unit)
    ) {
        val providers = subsProviders.toList()
        val isSingleProvider = subsProviders.size == 1

        val dialog = Dialog(context, R.style.DialogFullscreenPlayer)
        val binding =
            DialogOnlineSubtitlesBinding.inflate(LayoutInflater.from(context), null, false)
        dialog.setContentView(binding.root)
        fixSystemBarsPadding(binding.root)

        var currentSubtitles: List<AbstractSubtitleEntities.SubtitleEntity> = emptyList()
        var currentSubtitle: AbstractSubtitleEntities.SubtitleEntity? = null

        val layout = R.layout.sort_bottom_single_choice_double_text
        val arrayAdapter =
            object : ArrayAdapter<AbstractSubtitleEntities.SubtitleEntity>(dialog.context, layout) {
                fun setHearingImpairedIcon(
                    imageViewEnd: ImageView?, position: Int
                ) {
                    if (imageViewEnd == null) return
                    val isHearingImpaired =
                        currentSubtitles.getOrNull(position)?.isHearingImpaired ?: false

                    val drawableEnd = if (isHearingImpaired) {
                        ContextCompat.getDrawable(
                            context, R.drawable.ic_baseline_hearing_24
                        )?.apply {
                            setTint(
                                ContextCompat.getColor(
                                    context, R.color.textColor
                                )
                            )
                        }
                    } else null

                    imageViewEnd.setImageDrawable(drawableEnd)
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context).inflate(layout, null)

                    val item = getItem(position)

                    val mainTextView = view.findViewById<TextView>(R.id.main_text)
                    val secondaryTextView = view.findViewById<TextView>(R.id.secondary_text)
                    val drawableEnd = view.findViewById<ImageView>(R.id.drawable_end)

                    mainTextView?.text = item?.let { getName(it, false) }

                    val language =
                        item?.let { fromTagToLanguageName(it.lang) ?: it.lang } ?: ""
                    val providerSuffix =
                        if (isSingleProvider || item == null) "" else " · ${item.source}"
                    @SuppressLint("SetTextI18n")
                    secondaryTextView?.text = language + providerSuffix

                    setHearingImpairedIcon(drawableEnd, position)
                    return view
                }
            }

        dialog.show()
        configureDialogHalfScreen(dialog)
        binding.cancelBtt.setOnClickListener {
            dialog.dismissSafe()
        }

        binding.subtitleAdapter.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        binding.subtitleAdapter.adapter = arrayAdapter

        binding.subtitleAdapter.setOnItemClickListener { _, _, position, _ ->
            currentSubtitle = currentSubtitles.getOrNull(position) ?: return@setOnItemClickListener
        }

        var currentLanguageTagIETF: String = getAutoSelectLanguageTagIETF()


        fun setSubtitlesList(list: List<AbstractSubtitleEntities.SubtitleEntity>) {
            currentSubtitles = list
            arrayAdapter.clear()
            arrayAdapter.addAll(currentSubtitles)
        }

        val currentTempMeta = getMetaData()

        // bruh idk why it is not correct
        val color =
            ColorStateList.valueOf(context.colorFromAttribute(androidx.appcompat.R.attr.colorAccent))
        binding.searchLoadingBar.progressTintList = color
        binding.searchLoadingBar.indeterminateTintList = color

        observeNullable(viewModel.currentSubtitleYear) {
            // When year is changed search again
            binding.subtitlesSearch.setQuery(binding.subtitlesSearch.query, true)
            binding.yearBtt.text = it?.toString() ?: txt(R.string.none).asString(context)
        }

        binding.yearBtt.setOnClickListener {
            val none = txt(R.string.none).asString(context)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val earliestYear = 1900

            val years = (currentYear downTo earliestYear).toList()
            val options = listOf(none) + years.map {
                it.toString()
            }

            val selectedIndex = viewModel.currentSubtitleYear.value
                ?.let {
                    // + 1 since none also takes a space
                    years.indexOf(it) + 1
                }
                ?.takeIf { it >= 0 } ?: 0

            activity?.showDialog(
                options,
                selectedIndex,
                txt(R.string.year).asString(context),
                true, {
                }, { index ->
                    viewModel.setSubtitleYear(years.getOrNull(index - 1))
                }
            )
        }

        binding.subtitlesSearch.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                binding.searchLoadingBar.show()
                ioSafe {
                    val search =
                        SubtitleSearch(
                            query = query ?: return@ioSafe,
                            imdbId = loadResponse?.getImdbId(),
                            tmdbId = loadResponse?.getTMDbId()?.toInt(),
                            malId = loadResponse?.getMalId()?.toInt(),
                            aniListId = loadResponse?.getAniListId()?.toInt(),
                            epNumber = currentTempMeta.episode,
                            seasonNumber = currentTempMeta.season,
                            lang = currentLanguageTagIETF.ifBlank { null },
                            year = viewModel.currentSubtitleYear.value
                        )

                    // TODO Make ui a lot better, like search with tabs
                    val results = providers.amap {
                        when (val response = Resource.fromResult(it.search(search))) {
                            is Resource.Success -> {
                                response.value
                            }

                            is Resource.Loading -> {
                                emptyList()
                            }

                            is Resource.Failure -> {
                                showToast(response.errorString)
                                emptyList()
                            }
                        }
                    }
                    val max = results.maxOfOrNull { it.size } ?: return@ioSafe

                    // very ugly
                    val items = ArrayList<AbstractSubtitleEntities.SubtitleEntity>()
                    val arrays = results.size
                    for (index in 0 until max) {
                        for (i in 0 until arrays) {
                            items.add(results[i].getOrNull(index) ?: continue)
                        }
                    }

                    // ugly ik
                    activity?.runOnUiThread {
                        setSubtitlesList(items)
                        binding.searchLoadingBar.hide()
                    }
                }

                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })

        binding.searchFilter.setOnClickListener { view ->
            val languagesTagName =
                languages
                    .map { Pair(it.IETF_tag, it.nameNextToFlagEmoji()) }
                    .sortedBy {
                        it.second.substringAfter("\u00a0").lowercase()
                    } // name ignoring flag emoji
            val (langTagsIETF, langNames) = languagesTagName.unzip()

            activity?.showDialog(
                langNames,
                langTagsIETF.indexOf(currentLanguageTagIETF),
                view?.context?.getString(R.string.subs_subtitle_languages)
                    ?: return@setOnClickListener,
                true,
                { }) { index ->
                currentLanguageTagIETF = langTagsIETF[index]
                binding.subtitlesSearch.setQuery(binding.subtitlesSearch.query, true)
            }
        }

        binding.applyBtt.setOnClickListener {
            val currentSubtitle = currentSubtitle
            if (currentSubtitle == null) {
                dialog.dismissSafe()
                return@setOnClickListener
            }

            val api = providers.firstOrNull { it.idPrefix == currentSubtitle.idPrefix }
            if (api == null) {
                dialog.dismissSafe()
                return@setOnClickListener
            }

            binding.applyBtt.showProgress()
            ioSafe {
                val apiResource =
                    Resource.fromResult(api.resource(currentSubtitle))
                binding.applyBtt.hideProgress()
                when (apiResource) {
                    is Resource.Success -> {
                        val subtitles = apiResource.value.getSubtitles().map { resource ->
                            SubtitleData(
                                originalName = resource.name ?: getName(
                                    currentSubtitle,
                                    true
                                ),
                                nameSuffix = "",
                                url = resource.url,
                                origin = resource.origin,
                                mimeType = resource.url.toSubtitleMimeType(),
                                headers = currentSubtitle.headers,
                                languageCode = currentSubtitle.lang
                            )
                        }
                        if (subtitles.isEmpty()) {
                            showToast(R.string.no_subtitles)
                            return@ioSafe
                        }
                        dialog.dismissSafe()
                        runOnMainThread {
                            addAndSelectSubtitles(*subtitles.toTypedArray())
                        }
                    }

                    is Resource.Failure -> {
                        showToast(apiResource.errorString)
                    }

                    is Resource.Loading -> {
                        // not possible
                    }
                }
            }
        }

        dialog.setOnDismissListener {
            dismissCallback.invoke()
        }

        binding.subtitlesSearch.setQuery(currentTempMeta.name, true)
        //TODO: Set year text from currently loaded movie on Player
        //dialog.subtitles_search_year?.setText(currentTempMeta.year)
    }

    private fun openSubPicker() {
        try {
            subsPathPicker.launch(
                arrayOf(
                    "text/plain",
                    "text/str",
                    "application/octet-stream",
                    MimeTypes.TEXT_UNKNOWN,
                    MimeTypes.TEXT_VTT,
                    MimeTypes.TEXT_SSA,
                    MimeTypes.APPLICATION_TTML,
                    MimeTypes.APPLICATION_MP4VTT,
                    MimeTypes.APPLICATION_SUBRIP,
                )
            )
        } catch (e: Exception) {
            logError(e)
        }
    }

    @MainThread
    private fun addAndSelectSubtitles(
        vararg subtitleData: SubtitleData
    ) {
        if (subtitleData.isEmpty()) return
        val ctx = context ?: return
        val selectedSubtitle = subtitleData.first()
        viewModel.addSubtitles(subtitleData.toSet())

        // this is used instead of observe(viewModel._currentSubs), because observe is too slow
        player.setActiveSubtitles(viewModel.state.subtitles)

        // Save current time as to not reset player to 00:00
        player.saveData()
        player.reloadPlayer(ctx)

        setSubtitles(selectedSubtitle, false)

        selectSourceDialog?.dismissSafe()
        selectSourceDialog = null

        showToast(
            String.format(ctx.getString(R.string.player_loaded_subtitles), selectedSubtitle.name),
            Toast.LENGTH_LONG
        )
    }

    // Open file picker
    private val subsPathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            safe {
                // It lies, it can be null if file manager quits.
                if (uri == null) return@safe
                val ctx = context ?: CloudStreamApp.context ?: return@safe
                // RW perms for the path
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                val file = SafeFile.fromUri(ctx, uri)
                val fileName = file?.name()
                println("Loaded subtitle file. Selected URI path: $uri - Name: $fileName")
                // DO NOT REMOVE THE FILE EXTENSION FROM NAME, IT'S NEEDED FOR MIME TYPES
                val name = fileName ?: uri.toString()

                val subtitleData = SubtitleData(
                    name,
                    "",
                    uri.toString(),
                    SubtitleOrigin.DOWNLOADED_FILE,
                    name.toSubtitleMimeType(),
                    emptyMap(),
                    null
                )

                addAndSelectSubtitles(subtitleData)
            }
        }

    /** Will toast both when an error is found and when a subtitle is selected,
     * so only use from a user click and not a background process */
    private fun addFirstSub(query: SubtitleSearch) =
        viewModel.viewModelScope.launch {
            // async should not have a race condition if they are on the same group
            var hasSelectASubtitle = false

            // first come first served with these subtitles
            // we might want to change it to prefer different sources when used multiple times,
            // however caching might make this random after the first click too
            subsProviders.toList().amap { provider ->
                val success = when (val result = Resource.fromResult(
                    provider.search(
                        query = query
                    )
                )) {
                    is Resource.Failure -> {
                        // scope might cancel, so we do an extra check
                        if (this.isActive) {
                            showToast("${provider.idPrefix}${result.errorString}")
                        }
                        return@amap
                    }

                    is Resource.Loading -> {
                        // unreachable
                        return@amap
                    }

                    is Resource.Success -> {
                        result.value
                    }
                }

                // try to add every subtitle until we have added a new subtitle file
                for (subtitleEntry in success) {
                    if (hasSelectASubtitle || !this.isActive) {
                        break
                    }

                    val subtitleResources = provider.resource(subtitleEntry).getOrNull() ?: continue

                    val subtitles = subtitleResources.getSubtitles().map { resource ->
                        SubtitleData(
                            originalName = resource.name ?: getName(subtitleEntry, true),
                            nameSuffix = "",
                            url = resource.url,
                            origin = resource.origin,
                            mimeType = resource.url.toSubtitleMimeType(),
                            headers = subtitleEntry.headers,
                            languageCode = subtitleEntry.lang,
                        )
                    }

                    // checks for both a race condition and if any of the subs generated is new
                    if (this.isActive && !viewModel.state.subtitles.containsAll(subtitles) && !hasSelectASubtitle) {
                        hasSelectASubtitle = true
