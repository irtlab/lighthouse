package io.sece.vlc.trx;

import io.sece.vlc.CalibrationModem;
import io.sece.vlc.Color;
import io.sece.vlc.trx.led.PiRgbLED;

public class CalibrationTransmitter implements Runnable
{
    private int duration;
    private int[] hueValue;
    private int brightness = 100;
    private PiRgbLED led;


    public int getBrightness() {
        return brightness;
    }

    public int getDuration() {
        return duration;
    }

    public int[] getHueValue() {
        return hueValue;
    }

    public void setLed(PiRgbLED led) {
        this.led = led;
    }

    @Override
    public String toString() {
        return "FPS: " + duration + " - hueValue length: " + hueValue.length + " - brightness: " + brightness;
    }

    @Override
    public void run()
    {
        try {
            CalibrationModem mod;
            Transmitter<?> t;

            for(int i = 0; i < this.getHueValue().length; i++)
            {
                mod = new CalibrationModem(this.getHueValue()[i], 100, this.getBrightness());
                t = new Transmitter<>(led, mod, (this.getDuration() * 1000));
                String data = "1";
                // Transmit the data stored in the buffer.
                t.tx(data);
            }

            // Create an transmitter implementation which connects a particular
            // LEDInterface object to a particular Modulator. Note this should
            // enforce strict type checking and it should not be possible to
            // connect LEDs with incompatible modulators. That should generate a compile-time error.

            led.set(Color.BLACK);
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }

}