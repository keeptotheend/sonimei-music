package com.dede.sonimei.module.local

import android.Manifest
import android.annotation.SuppressLint
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.dede.sonimei.R
import com.dede.sonimei.base.BaseFragment
import com.dede.sonimei.data.local.LocalSong
import com.dede.sonimei.module.home.MainActivity
import com.dede.sonimei.util.HanziToPinyin
import com.dede.sonimei.util.ScreenHelper
import com.dede.sonimei.util.extends.to
import com.tbruyelle.rxpermissions2.RxPermissions
import com.turingtechnologies.materialscrollbar.CustomIndicator
import com.turingtechnologies.materialscrollbar.ICustomAdapter
import kotlinx.android.synthetic.main.fragment_local_music.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.info
import org.jetbrains.anko.support.v4.toast
import org.jetbrains.anko.uiThread

class LocalMusicFragment : BaseFragment() {

    override fun getLayoutId() = R.layout.fragment_local_music

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        val metadataRetriever = MediaMetadataRetriever()
//        var metadata = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
//        metadata = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
//        metadata = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
//        metadata = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
//        metadata = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    }

    private val listAdapter by lazy { LocalMusicListAdapter() }

    @SuppressLint("CheckResult")
    override fun initView(savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        activity!!.to<AppCompatActivity>().setSupportActionBar(tool_bar)
        // 重新回到栈顶时paddingTop会被置为0，原因未知
        tool_bar.setPadding(0, ScreenHelper.getFrameTopMargin(activity), 0, 0)

        swipe_refresh.setColorSchemeResources(R.color.colorPrimary, R.color.colorAccent)
        swipe_refresh.setOnRefreshListener { loadPlayList() }

        val emptyView = LayoutInflater.from(context).inflate(R.layout.layout_local_music_empty, recycler_view_local, false)
        listAdapter.emptyView = emptyView
        recycler_view_local.adapter = listAdapter
        listAdapter.setOnItemClickListener { adapter, view, position ->
            val song = listAdapter.data[position]
            asActivity<MainActivity>().playSongs(listAdapter.data, song)
        }
        scroll_bar.setIndicator(CustomIndicator(context), true)

        RxPermissions(activity!!)
                .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe({
                    if (!it) {
                        toast(R.string.permission_sd_error)
                        return@subscribe
                    }
                    loadPlayList()
                }) { it.printStackTrace() }
    }

    private fun loadPlayList() {
        swipe_refresh.isRefreshing = true
        doAsync {
            val list = ArrayList<LocalSong>()
            fun load(cursor: Cursor?) {
                if (cursor == null) return
                val pinyin = HanziToPinyin.getInstance()
                while (cursor.moveToNext()) {
                    val duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION))
                    if (duration < 1000 * 30) {//忽略时间小于30s的歌曲
                        continue
                    }
                    val id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID))
                    val title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                    val album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                    val artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                    val path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                    val localSong = LocalSong(id, title, artist, album, duration, path)
                    list.add(localSong)
                    val pinYinList = pinyin.get(title)
                    if (pinYinList != null && pinYinList.size >= 1) {
                        val token = pinYinList[0]
                        if (token.type != HanziToPinyin.Token.UNKNOWN)
                            localSong.pinyin = token.target.substring(0, 1).toUpperCase()
                        else
                            localSong.pinyin = "#"
                    }
                    info(localSong.title + "   " + localSong.pinyin)
                }
                cursor.close()
            }
            load(context!!.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    null, null, null, null))
            load(context!!.contentResolver.query(MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                    null, null, null, null))
            list.sortWith(Comparator { o1, o2 -> o1?.pinyin?.compareTo(o2?.pinyin ?: "#") ?: 0 })
            uiThread {
                swipe_refresh.isRefreshing = false
                listAdapter.setNewData(list)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_local_music, menu)
    }

    inner class LocalMusicListAdapter : BaseQuickAdapter<LocalSong, BaseViewHolder>(R.layout.item_local_music), ICustomAdapter {

        override fun getCustomStringForElement(element: Int): String {
            return getItem(element)?.pinyin ?: "#"
        }

        override fun convert(helper: BaseViewHolder?, item: LocalSong?) {
            helper?.setText(R.id.tv_name, item?.getName())
                    ?.setText(R.id.tv_singer_album, item?.author)
        }
    }
}