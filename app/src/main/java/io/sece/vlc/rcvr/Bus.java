package io.sece.vlc.rcvr;

import android.os.Handler;
import android.os.Looper;

import com.google.common.eventbus.AsyncEventBus;

/**
 * An event bus based on Guava's AsyncEventBus that always delivers messages on the UI thread.
 * Thus, it is guaranteed that the method annotated with @Subscribe will be executing on the UI
 * thread and there is no need to wrap it in runOnUiThread.
 */
public class Bus extends AsyncEventBus {
    private static Bus instance;
    private static Handler uiThread = new Handler(Looper.getMainLooper());

    public static class Event {
    }

    private Bus() {
        super(runnable -> {
            if (Looper.myLooper() == Looper.getMainLooper()) runnable.run();
            else uiThread.post(runnable);
        });
    }

    public static Bus getInstance() {
        if (null == instance)
            instance = new Bus();
        return instance;
    }

    public static void send(Object object) {
        getInstance().post(object);
    }

    public static void subscribe(Object object) {
        getInstance().register(object);
    }

    public static void unsubscribe(Object object) {
        getInstance().unregister(object);
    }
}