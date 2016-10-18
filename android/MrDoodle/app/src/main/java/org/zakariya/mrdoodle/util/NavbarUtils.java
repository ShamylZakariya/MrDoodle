package org.zakariya.mrdoodle.util;

import android.content.res.Configuration;
import android.content.res.Resources;

/**
 * Measures size of navbar.
 * http://stackoverflow.com/questions/21057035/detect-android-navigation-bar-orientation
 */

public class NavbarUtils {
	public static boolean hasNavBar (Resources resources)
	{
		int id = resources.getIdentifier("config_showNavigationBar", "bool", "android");
		if (id > 0)
			return resources.getBoolean(id);
		else
			return false;
	}

	public static int getNavigationBarHeight (Resources resources)
	{
		if (!NavbarUtils.hasNavBar(resources)) {
			return 0;
		}

		int orientation = resources.getConfiguration().orientation;

		//Only phone between 0-599 has navigation bar can move
		boolean isSmartphone = resources.getConfiguration().smallestScreenWidthDp < 600;
		if (isSmartphone && Configuration.ORIENTATION_LANDSCAPE == orientation) {
			return 0;
		}

		int id = resources.getIdentifier(orientation == Configuration.ORIENTATION_PORTRAIT ? "navigation_bar_height" : "navigation_bar_height_landscape", "dimen", "android");
		if (id > 0) {
			return resources.getDimensionPixelSize(id);
		}

		return 0;
	}

	public static int getNavigationBarWidth (Resources resources)
	{
		if (!NavbarUtils.hasNavBar(resources)) {
			return 0;
		}

		int orientation = resources.getConfiguration().orientation;

		//Only phone between 0-599 has navigationbar can move
		boolean isSmartphone = resources.getConfiguration().smallestScreenWidthDp < 600;

		if (orientation == Configuration.ORIENTATION_LANDSCAPE && isSmartphone)
		{
			int id = resources.getIdentifier("navigation_bar_width", "dimen", "android");
			if (id > 0) {
				return resources.getDimensionPixelSize(id);
			}
		}

		return 0;
	}
}
