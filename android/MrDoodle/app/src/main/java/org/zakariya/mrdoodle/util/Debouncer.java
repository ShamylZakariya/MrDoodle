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

public class Debouncer<T> {

	private final Subject<T,T> bus;
	private final Observable<T> observable;
	private final Subscription subscription;

	public Debouncer(int millis, Action1<T> callable) {
		this(millis, TimeUnit.MILLISECONDS, callable);
	}

	public Debouncer(int scalar, TimeUnit units, Action1<T> callable) {
		Subject<T,T> sub = PublishSubject.create();
		bus = new SerializedSubject<>(sub);

		observable = bus.debounce(scalar, units, AndroidSchedulers.mainThread())
				.observeOn(AndroidSchedulers.mainThread());

		subscription = observable.subscribe(callable);
	}

	public void send(T o) {
		bus.onNext(o);
	}

	public void destroy() {
		if (subscription != null && !subscription.isUnsubscribed()) {
			subscription.unsubscribe();
		}
	}

}
