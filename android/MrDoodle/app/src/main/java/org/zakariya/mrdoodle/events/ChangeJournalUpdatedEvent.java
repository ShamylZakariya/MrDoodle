package org.zakariya.mrdoodle.events;

import org.zakariya.mrdoodle.sync.ChangeJournal;

/**
 * Created by shamyl on 8/13/16.
 */
public class ChangeJournalUpdatedEvent {
	ChangeJournal changeJournal;

	public ChangeJournalUpdatedEvent(ChangeJournal changeJournal) {
		this.changeJournal = changeJournal;
	}

	public ChangeJournal getChangeJournal() {
		return changeJournal;
	}
}
