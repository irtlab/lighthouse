package io.sece.vlc.trx;


import io.sece.vlc.trx.led.PiRgbLED;

public class WatchDog implements Runnable {

    private Thread thread;
    private int timeout;
    private PiRgbLED led;


    public WatchDog(Thread thread, int timeout, PiRgbLED led)
    {
        this.thread = thread;
        this.timeout = timeout;
        this.led = led;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(timeout * 1000);
            if(thread != null && thread.isAlive()) {
                thread.stop();
            }
            led.set(io.sece.vlc.Color.BLACK);

        }
        catch(Exception e)
        {
            System.out.println(e.fillInStackTrace());
        }
    }
}
