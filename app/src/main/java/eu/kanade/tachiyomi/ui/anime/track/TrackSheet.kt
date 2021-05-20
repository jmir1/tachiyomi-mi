package eu.kanade.tachiyomi.ui.anime.track

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.data.database.models.Anime
import eu.kanade.tachiyomi.databinding.TrackControllerBinding
import eu.kanade.tachiyomi.ui.anime.AnimeController
import eu.kanade.tachiyomi.ui.base.controller.openInBrowser
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.widget.sheet.BaseBottomSheetDialog

class TrackSheet(
    val controller: AnimeController,
    val anime: Anime
) : BaseBottomSheetDialog(controller.activity!!),
    TrackAdapter.OnClickListener,
    SetTrackStatusDialog.Listener,
    SetTrackEpisodesDialog.Listener,
    SetTrackScoreDialog.Listener,
    SetTrackWatchingDatesDialog.Listener {

    private lateinit var binding: TrackControllerBinding

    private lateinit var adapter: TrackAdapter

    override fun createView(inflater: LayoutInflater): View {
        binding = TrackControllerBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = TrackAdapter(this)
        binding.trackRecycler.layoutManager = LinearLayoutManager(context)
        binding.trackRecycler.adapter = adapter

        adapter.items = controller.presenter.trackList
    }

    override fun onStart() {
        super.onStart()
        sheetBehavior.skipCollapsed = true
    }

    override fun show() {
        super.show()
        controller.presenter.refreshTrackers()
        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun onNextTrackers(trackers: List<TrackItem>) {
        if (this::adapter.isInitialized) {
            adapter.items = trackers
            adapter.notifyDataSetChanged()
        }
    }

    override fun onLogoClick(position: Int) {
        val track = adapter.getItem(position)?.track ?: return

        if (track.tracking_url.isNotBlank()) {
            controller.openInBrowser(track.tracking_url)
        }
    }

    override fun onSetClick(position: Int) {
        val item = adapter.getItem(position) ?: return
        TrackSearchDialog(controller, item.service).showDialog(controller.router, TAG_SEARCH_CONTROLLER)
    }

    override fun onTitleLongClick(position: Int) {
        adapter.getItem(position)?.track?.title?.let {
            controller.activity?.copyToClipboard(it, it)
        }
    }

    override fun onStatusClick(position: Int) {
        val item = adapter.getItem(position) ?: return
        if (item.track == null) return

        SetTrackStatusDialog(controller, this, item).showDialog(controller.router)
    }

    override fun onEpisodesClick(position: Int) {
        val item = adapter.getItem(position) ?: return
        if (item.track == null) return

        SetTrackEpisodesDialog(controller, this, item).showDialog(controller.router)
    }

    override fun onScoreClick(position: Int) {
        val item = adapter.getItem(position) ?: return
        if (item.track == null) return

        SetTrackScoreDialog(controller, this, item).showDialog(controller.router)
    }

    override fun onStartDateClick(position: Int) {
        val item = adapter.getItem(position) ?: return
        if (item.track == null) return

        SetTrackWatchingDatesDialog(controller, this, SetTrackWatchingDatesDialog.ReadingDate.Start, item).showDialog(controller.router)
    }

    override fun onFinishDateClick(position: Int) {
        val item = adapter.getItem(position) ?: return
        if (item.track == null) return

        SetTrackWatchingDatesDialog(controller, this, SetTrackWatchingDatesDialog.ReadingDate.Finish, item).showDialog(controller.router)
    }

    override fun setStatus(item: TrackItem, selection: Int) {
        controller.presenter.setTrackerStatus(item, selection)
    }

    override fun setEpisodesRead(item: TrackItem, episodesRead: Int) {
        controller.presenter.setTrackerLastEpisodeRead(item, episodesRead)
    }

    override fun setScore(item: TrackItem, score: Int) {
        controller.presenter.setTrackerScore(item, score)
    }

    override fun setReadingDate(item: TrackItem, type: SetTrackWatchingDatesDialog.ReadingDate, date: Long) {
        when (type) {
            SetTrackWatchingDatesDialog.ReadingDate.Start -> controller.presenter.setTrackerStartDate(item, date)
            SetTrackWatchingDatesDialog.ReadingDate.Finish -> controller.presenter.setTrackerFinishDate(item, date)
        }
    }

    fun getSearchDialog(): TrackSearchDialog? {
        return controller.router.getControllerWithTag(TAG_SEARCH_CONTROLLER) as? TrackSearchDialog
    }

    private companion object {
        const val TAG_SEARCH_CONTROLLER = "track_search_controller"
    }
}
