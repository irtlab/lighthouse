package io.sece.vlc;

import java.util.BitSet;


/**
 * An abtract base class for all modulators and demodulators. The base class
 * declares API to modulate a bit set into a LED signal and to demodulate a
 * LED signal into a bit set. The type of LED signal is configurable and is
 * meant to be set by implementations inheriting from this class.
 *
 * Each modulate/demodulate function is implemented in two variants: with and
 * without an offset. The offset parameters allows reading/storing the bits at
 * a non-zero index in an existing BitSet buffer.
 *
 * Each modulator also exports an attribute bits which determines how many
 * bits at a time the modulator consumers or generates.
 */
public abstract class Modulator<V> {
    public int bits;

    public BitSet demodulate(BitSet data, V value) {
        return demodulate(data, 0, value);
    }

    public V modulate(BitSet data) {
        return modulate(data, 0);
    }

    public abstract BitSet demodulate(BitSet data, int offset, V value);
    public abstract V modulate(BitSet data, int offset);
}