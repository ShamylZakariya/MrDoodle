package org.zakariya.mrdoodle.ui;

import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.zakariya.mrdoodle.R;

import butterknife.Bind;
import butterknife.ButterKnife;
import icepick.Icepick;

/**
 * Created by shamyl on 12/28/15.
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

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_about, container, false);
		ButterKnife.bind(this, v);

		try {
			PackageInfo packageInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);

			if (!TextUtils.isEmpty(packageInfo.versionName)) {
				versionTextView.setText(getString(R.string.about_version_version_and_build, packageInfo.versionName, packageInfo.versionCode));
			} else {
				versionTextView.setText(getString(R.string.about_version_build, packageInfo.versionCode));
			}

		} catch (Exception e) {
			Log.e(TAG, "Unable to get app version info!?");
		}

		return v;
	}
}
