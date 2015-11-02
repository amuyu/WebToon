package com.pluu.webtoon.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import javax.inject.Inject;
import java.util.List;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.pluu.event.OttoBusHolder;
import com.pluu.support.impl.AbstractWeekApi;
import com.pluu.support.impl.ServiceConst;
import com.pluu.webtoon.AppController;
import com.pluu.webtoon.R;
import com.pluu.webtoon.adapter.MainListAdapter;
import com.pluu.webtoon.api.WebToonInfo;
import com.pluu.webtoon.common.Const;
import com.pluu.webtoon.db.InjectDB;
import com.pluu.webtoon.event.MainEpisodeLoadedEvent;
import com.pluu.webtoon.event.MainEpisodeStartEvent;
import com.pluu.webtoon.event.WebToonSelectEvent;
import com.squareup.otto.Subscribe;
import com.squareup.sqlbrite.BriteDatabase;
import rx.functions.Action1;

/**
 * Main WebToon List Fragment
 * Created by PLUUSYSTEM-NEW on 2015-10-27.
 */
@SuppressLint("ValidFragment")
public class WebtoonListFragment extends Fragment {
	private final String TAG = WebtoonListFragment.class.getSimpleName();

	private RecyclerView recyclerView;
	private GridLayoutManager manager;
	private int position;

	private static final int REQUEST_CODE = 1000;

	@Inject
	BriteDatabase db;

	private AbstractWeekApi serviceApi;
	private WebToonInfo selectInfo;
	private int columnCount;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ServiceConst.NAV_ITEM service = ServiceConst.getApiType(getArguments());
		serviceApi = AbstractWeekApi.getApi(service);
		position = getArguments().getInt(Const.EXTRA_POS);
		columnCount = getResources().getInteger(R.integer.webtoon_column_count);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		recyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_webtoon_list, null);
		manager = new GridLayoutManager(getActivity(), columnCount);
		recyclerView.setLayoutManager(manager);

		AppController.objectGraph(getActivity()).inject(this);
		return recyclerView;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		new AsyncTask<Void, Void, List<WebToonInfo>>() {
			@Override
			protected void onPreExecute() {
				super.onPreExecute();
				OttoBusHolder.get().post(new MainEpisodeStartEvent());
			}

			@Override
			protected List<WebToonInfo> doInBackground(Void... params) {
				Log.i(TAG, "Load pos=" + position);

				List<WebToonInfo> list = serviceApi.parseMain(getActivity(), position);

				for (final WebToonInfo item : list) {
					InjectDB
						.getEpisodeFavorite(db,
											serviceApi.getClass().getSimpleName(),
											item,
											new Action1<Boolean>() {
												@Override
												public void call(Boolean aBoolean) {
													item.setIsFavorite(aBoolean);
												}
											}
						);
				}

				return list;
			}

			@Override
			protected void onPostExecute(List<WebToonInfo> list) {
				recyclerView.setAdapter(new MainListAdapter(getActivity(), list) {
					@Override
					public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
						ViewHolder vh = super.onCreateViewHolder(viewGroup, i);
						setClickListener(vh);
						return vh;
					}
				});

				OttoBusHolder.get().post(new MainEpisodeLoadedEvent());
			}
		}.execute();
	}

	@Override
	public void onResume() {
		super.onResume();
		OttoBusHolder.get().register(this);
	}

	@Override
	public void onPause() {
		OttoBusHolder.get().unregister(this);
		super.onPause();
	}

	@Subscribe
	public void responseNetwork(WebToonSelectEvent result) {
		selectInfo = result.item;
		((MainListAdapter) recyclerView.getAdapter()).setSelectInfo(selectInfo);
	}

	private void setClickListener(final MainListAdapter.ViewHolder vh) {
		View v = vh.itemView;
		v.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				final WebToonInfo item = (WebToonInfo) vh.titleView.getTag();
				selectInfo = item;
				loadPalette(item);
			}
		});
	}

	private void loadPalette(final WebToonInfo item) {
		final Context context = getActivity();
		Glide.with(context)
			 .load(item.getImage())
			 .asBitmap()
			 .into(new SimpleTarget<Bitmap>() {
				 @Override
				 public void onResourceReady(Bitmap resource,
											 GlideAnimation<? super Bitmap> glideAnimation) {
					 asyncPalette(item, resource);
				 }
			 });
	}

	private void asyncPalette(final WebToonInfo item, Bitmap bitmap) {
		final Context context = getActivity();
		Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
			public void onGenerated(Palette p) {
				int bgColor = p.getDarkVibrantColor(
					Color.BLACK);
				int statusColor = p.getDarkMutedColor(
					ContextCompat.getColor(context, R.color.theme_primary_dark));
				moveEpisode(item, bgColor, statusColor);
			}
		});
	}

	private void moveEpisode(WebToonInfo item, int bgColor, int statusColor) {
		Intent intent = new Intent(getActivity(), EpisodesActivity.class);
		intent.putExtra(Const.EXTRA_API, serviceApi.getNaviItem());
		intent.putExtra(Const.EXTRA_EPISODE, item);
		intent.putExtra(Const.EXTRA_MAIN_COLOR, bgColor);
		intent.putExtra(Const.EXTRA_STATUS_COLOR, statusColor);
		getActivity().startActivityForResult(intent, REQUEST_CODE);
	}

	public void updateSpanCount() {
		int columnCount = getResources().getInteger(R.integer.webtoon_column_count);
		manager.setSpanCount(columnCount);
		recyclerView.getAdapter().notifyDataSetChanged();
	}

	public void update() {
		recyclerView.getAdapter().notifyDataSetChanged();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
			|| newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
			updateSpanCount();
		}
	}
}