#CURRENTLY

- switch placeholder graphics from PNGs to vector
- update GSM deps

- Add a killswitch file on another server, say, shamylzakariya.github.io. it would be a JSON file with contents like: {
	"discontinued": null || "MrDoodle sync services have been discontinued, sorry"
}

#NEXT
Landscape layout for sync settings, about screen

#BUGS

- still have this issue:
10-29 14:00:44.510 22263-22263/org.zakariya.mrdoodle E/AndroidRuntime: FATAL EXCEPTION: main
                                                                       Process: org.zakariya.mrdoodle, PID: 22263
                                                                       java.lang.RuntimeException: Could not dispatch event: class org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent to handler [EventHandler public void org.zakariya.mrdoodle.ui.DoodleDocumentGridFragment.onDoodleDocumentCreated(org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent)]: Illegal State: Object is no longer valid to operate on. Was it deleted by another thread?
                                                                           at com.squareup.otto.Bus.throwRuntimeException(Bus.java:460)
                                                                           at com.squareup.otto.Bus.dispatch(Bus.java:387)
                                                                           at com.squareup.otto.Bus.dispatchQueuedEvents(Bus.java:368)
                                                                           at com.squareup.otto.Bus.post(Bus.java:337)
                                                                           at org.zakariya.mrdoodle.MrDoodleApplication$1.run(MrDoodleApplication.java:177)
                                                                           at android.os.Handler.handleCallback(Handler.java:739)
                                                                           at android.os.Handler.dispatchMessage(Handler.java:95)
                                                                           at android.os.Looper.loop(Looper.java:148)
                                                                           at android.app.ActivityThread.main(ActivityThread.java:5417)
                                                                           at java.lang.reflect.Method.invoke(Native Method)
                                                                           at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:726)
                                                                           at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:616)
                                                                        Caused by: java.lang.IllegalStateException: Illegal State: Object is no longer valid to operate on. Was it deleted by another thread?
                                                                           at io.realm.internal.UncheckedRow.nativeGetTimestamp(Native Method)
                                                                           at io.realm.internal.UncheckedRow.getDate(UncheckedRow.java:148)
                                                                           at io.realm.DoodleDocumentRealmProxy.realmGet$creationDate(DoodleDocumentRealmProxy.java:109)
                                                                           at org.zakariya.mrdoodle.model.DoodleDocument.getCreationDate(DoodleDocument.java:302)
                                                                           at org.zakariya.mrdoodle.adapters.DoodleDocumentAdapter$1.compare(DoodleDocumentAdapter.java:108)
                                                                           at org.zakariya.mrdoodle.adapters.DoodleDocumentAdapter$1.compare(DoodleDocumentAdapter.java:105)
                                                                           at java.util.TimSort.countRunAndMakeAscending(TimSort.java:320)
                                                                           at java.util.TimSort.sort(TimSort.java:185)
                                                                           at java.util.Arrays.sort(Arrays.java:1998)
                                                                           at java.util.Collections.sort(Collections.java:1900)
                                                                           at org.zakariya.mrdoodle.adapters.DoodleDocumentAdapter.sortDocuments(DoodleDocumentAdapter.java:386)
                                                                           at org.zakariya.mrdoodle.adapters.DoodleDocumentAdapter.onItemAdded(DoodleDocumentAdapter.java:224)
                                                                           at org.zakariya.mrdoodle.ui.DoodleDocumentGridFragment.onDoodleDocumentCreated(DoodleDocumentGridFragment.java:451)
                                                                           at java.lang.reflect.Method.invoke(Native Method)
                                                                           at com.squareup.otto.EventHandler.handleEvent(EventHandler.java:89)
                                                                           at com.squareup.otto.Bus.dispatch(Bus.java:385)
                                                                           at com.squareup.otto.Bus.dispatchQueuedEvents(Bus.java:368) 
                                                                           at com.squareup.otto.Bus.post(Bus.java:337) 
                                                                           at org.zakariya.mrdoodle.MrDoodleApplication$1.run(MrDoodleApplication.java:177) 
                                                                           at android.os.Handler.handleCallback(Handler.java:739) 
                                                                           at android.os.Handler.dispatchMessage(Handler.java:95) 
                                                                           at android.os.Looper.loop(Looper.java:148) 
                                                                           at android.app.ActivityThread.main(ActivityThread.java:5417) 
                                                                           at java.lang.reflect.Method.invoke(Native Method) 
                                                                           at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:726) 
                                                                           at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:616) 


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
