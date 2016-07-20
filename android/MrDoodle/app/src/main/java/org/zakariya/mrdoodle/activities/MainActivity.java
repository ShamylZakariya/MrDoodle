package org.zakariya.mrdoodle.activities;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import org.zakariya.mrdoodle.fragments.DoodleDocumentGridFragment;

/**
 * Created by shamyl on 12/16/15.
 */
public class MainActivity extends SingleFragmentActivity {

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected Fragment createFragment() {
		return new DoodleDocumentGridFragment();
	}
}
