package org.zakariya.mrdoodle.util;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

/**
 * A simple debouncer
 */

// TODO Figure out how to make this generic on <T>
public class Debouncer {

	public interface Listener {
		void call(Object o);
	}

	private final Subject<Object,Object> bus;
	private final Observable<Object> observable;
	private final Subscription subscription;

	public Debouncer(int millis, Listener listener) {
		this(millis, TimeUnit.MILLISECONDS, listener);
	}

	public Debouncer(int scalar, TimeUnit units, final Listener listener) {
		bus = new SerializedSubject<>(PublishSubject.create());

		observable = bus.debounce(scalar, units, AndroidSchedulers.mainThread())
				.observeOn(AndroidSchedulers.mainThread());

		subscription = observable.subscribe(new Action1<Object>() {
			@Override
			public void call(Object o) {
				listener.call(o);
			}
		});
	}

	public void send(Object o) {
		bus.onNext(o);
	}

	public void destroy() {
		if (subscription != null && !subscription.isUnsubscribed()) {
			subscription.unsubscribe();
		}
	}

}
