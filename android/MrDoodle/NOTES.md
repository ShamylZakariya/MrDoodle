#BUGS

Test inability to connect more thoroughly. Try assigning an invalid IP address.

#CURRENTLY

- serviceStatus
	- we need some kind of local persistent store for service status, which defaults to assuming service is running and not discontinued
	- sync settings screen should show the discontinued or offline message if needed


- design better icon
- design better empty graphic



#TODO

- Figure out how to show the about/syncsettings/modeloverview as dialogs not fullscreen activities when on tablet
	- it looks like the supported way to do this is with a fragment dialog
	- this means those items would have to be fragments, not activities
	- need to maintain state on rotation, since the dialogs would just disappear

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
