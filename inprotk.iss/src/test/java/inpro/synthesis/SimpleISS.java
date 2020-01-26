package inpro.synthesis;

import inpro.audio.AudioUtils;
import inpro.audio.DispatchStream;
import inpro.incremental.unit.IU;
import inpro.synthesis.hts.IUBasedFullPStream;
import inpro.synthesis.hts.VocodingAudioStream;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class SimpleISS {

    @Test
    public void testDE() {
        System.setProperty("inpro.tts.language", "de");
        System.setProperty("inpro.tts.voice", "bits1-hsmm");
        //System.setProperty("inpro.tts.language", "en_US");
        //System.setProperty("inpro.tts.voice", "cmu-rms-hsmm");

        DispatchStream d = DispatchStream.drainingDispatchStream();
        d.playStream(MaryAdapter.getInstance().text2audio("Dies ist ein Satz, mach Platz."), true);
        // wait for synthesis:
        d.waitUntilDone();
        d.playStream(MaryAdapter.getInstance().text2audio("Dies ist noch ein Satz, mach Platz."), true);
        // wait for synthesis:
        d.waitUntilDone();
        List<? extends IU> wordIUs = MaryAdapter.getInstance().text2WordIUs("Dies ist noch ein Satz, mach Platz.");
        d.playStream(AudioUtils.get16kAudioStreamForVocodingStream(new VocodingAudioStream(new IUBasedFullPStream(wordIUs.get(0)), MaryAdapter5internal.getDefaultHMMData(), true)), true);
        // wait for synthesis:
        d.waitUntilDone();
        assertTrue(true);
    }


}
