#Currently

- AlertDialog theme on Nougat isn't being applied correctly
- Remember the pen and color used previously when loading DoodleActivity

#UX

- Sync Settings
	Move Status/Model/Sync/Reset&Sync to a menu
	Rethink UI, make it look more "account page"

- MainActivity
	Put a sync status type icon in the header?


#BUGS

- still have this issue:
java.lang.RuntimeException: Could not dispatch event: class org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent to handler [EventHandler public void org.zakariya.mrdoodle.ui.DoodleDocumentGridFragment.onDoodleDocumentCreated(org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent)]: Illegal State: Object is no longer valid to operate on. Was it deleted by another thread?

	I backgrounded and restored MrDoodle to connect to the server and reset the exponential reconnect timeout. I'm guessing the grid fragment never disconnected from the event bus????

#TODO

- Figure out how to show the about/syncsettings/modeloverview as dialogs not fullscreen activities when on tablet
	- it looks like the supported way to do this is with a fragment dialog
	- this means those items would have to be fragments, not activities
	- need to maintain state on rotation, since the dialogs would just disappear
	-

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
