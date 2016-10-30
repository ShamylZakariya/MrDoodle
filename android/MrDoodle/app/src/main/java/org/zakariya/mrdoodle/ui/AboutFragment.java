package org.zakariya.mrdoodle.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.zakariya.mrdoodle.MrDoodleApplication;
import org.zakariya.mrdoodle.R;

import butterknife.Bind;
import butterknife.ButterKnife;
import icepick.Icepick;

/**
 * Fragment that shows info about the application
 */
public class AboutFragment extends Fragment {

	private static final String TAG = "AboutFragment";

	@Bind(R.id.versionTextView)
	TextView versionTextView;

	@Bind(R.id.attributionsTextView)
	TextView attributionsTextView;

	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Icepick.restoreInstanceState(this, savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		Icepick.saveInstanceState(this, outState);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		// register with leak canary
 		MrDoodleApplication.getRefWatcher(getActivity()).watch(this);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_about, container, false);
		ButterKnife.bind(this, v);

		versionTextView.setText(MrDoodleApplication.getInstance().getVersionString());

		return v;
	}
}
