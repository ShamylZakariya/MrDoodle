#Currently


#BUGS
When deleting a bunch of items quickly - looks like the previously removed doc gets deleted. I assume snackbar is a singleton and is flushing its ondismissed queue or something to that effect.

We need to do something like detect if a delete is requested while another delete is still showing the undo button, and in that case, force that delete to run??? Or is the problem that that's happening automatically, and correctly???

	java.lang.IllegalStateException: Object is no longer managed by Realm. Has it been deleted?
	  at io.realm.internal.InvalidRow.getStubException(InvalidRow.java:192)
	  at io.realm.internal.InvalidRow.getString(InvalidRow.java:88)
	  at io.realm.PhotoDoodleDocumentRealmProxy.getUuid(PhotoDoodleDocumentRealmProxy.java:74)
	  at org.zakariya.photodoodle.adapters.DoodleDocumentAdapter.contains(DoodleDocumentAdapter.java:356)
	  at org.zakariya.photodoodle.fragments.DoodleDocumentGridFragment$5.onDismissed(DoodleDocumentGridFragment.java:264)
	  at android.support.design.widget.Snackbar.onViewHidden(Snackbar.java:603)

#TODO

- Divide brush radius by current zoom level. this will allow for delicate hairlines when zoomed in.

- Alpha Blending
	Will require 3 new full-screen bitmaps.
	Since this is expensive, should only be active when alpha < 255
	- livePathBitmap - is cleared and has the live stroke drawn in full alpha each live stroke change
	- staticPathBitmap - gets the static paths drawn into it in full alpha
	- pathCompositeBitmap - livePathBitmap and staticPathBitmap are drawn at full alpha, then, pathCompositeBitmap is drawn to screen (later to backing store when stroke is complete) at the stroke's alpha.

- Chunking

	Line caps cause overlap-darkening at chunk intersections when drawing in partial alpha. this should not be a surprise.
	- Possible solution: Have 2 bitmaps. A backing store, and a bitmap for the current stroke? The current stroke is rendered live on screen, static paths into the current stroke bitmap. When stroke is finished, that bitmap is rendered into the backing store.
		-- this won't fix the overlaps

- Nota Bene
	Android hardware accelerated drawing does not pass the clip rect to the Canvas in onDraw when invalidate(Rect) is called. Can't use for pruning.
