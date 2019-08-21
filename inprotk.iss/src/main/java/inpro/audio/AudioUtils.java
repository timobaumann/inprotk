package inpro.audio;

//import gov.nist.sphere.jaudio.SphereFileReader;
import inpro.synthesis.hts.VocodingAudioStream;
import marytts.util.data.BaseDoubleDataSource;
import marytts.util.data.DoubleDataSource;
import marytts.util.data.audio.AudioConverterUtils;
import marytts.util.data.audio.DDSAudioInputStream;
//import org.jflac.sound.spi.FlacAudioFileReader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;
import java.io.IOException;
import java.net.URL;

public class AudioUtils {

/*	public static AudioInputStream getAudioStreamForURL(URL audioFileURL) throws UnsupportedAudioFileException, IOException {
		AudioInputStream ais;
		String lowerCaseURL = audioFileURL.getFile().toLowerCase();
		if (lowerCaseURL.endsWith(".sph") ||
			lowerCaseURL.endsWith(".nis")) {
			AudioFileReader sfr = new SphereFileReader(); 
			ais = sfr.getAudioInputStream(audioFileURL);
		} else if (lowerCaseURL.endsWith(".flac")) {
			ais = null;
			FlacAudioFileReader fafr = new FlacAudioFileReader();
			ais = fafr.getAudioInputStream(audioFileURL);
		} else {
//			System.err.println(AudioSystem.getAudioFileFormat(audioFileURL));
	        ais = AudioSystem.getAudioInputStream(audioFileURL);
		}
		return ais;
	}*/
	
	public static AudioInputStream get16kAudioStreamForVocodingStream(VocodingAudioStream source) {
		if (source.getSamplingRate() == 48000) {
			System.err.println("WARNING: crude downsampling from 48000!");
			// handle 48000 incrementally by averaging or picking every third sample
			DoubleDataSource skippingSource = new SkipStream(source, 3);
			return new DDSAudioInputStream(skippingSource, new AudioFormat(16000, 16, 1, true, false));
		} else if (source.getSamplingRate() == 32000) {
			// handle 32000 incrementally by averaging or picking every second sample
			System.err.println("WARNING: crude downsampling from 32000!");
			DoubleDataSource skippingSource = new SkipStream(source, 2);
			return new DDSAudioInputStream(skippingSource, new AudioFormat(16000, 16, 1, true, false));
		} else {
			DDSAudioInputStream ddsStream = new DDSAudioInputStream(source, new AudioFormat(source.getSamplingRate(), 16, 1, true, false));
			if (source.getSamplingRate() == 16000) {
				return ddsStream;
			} else {
				try {
					// FIXME: add a warning that the following doesn't work incrementally!!
					System.err.println("WARNING: downsampling is non-incremental!");
					return AudioConverterUtils.downSampling(ddsStream, 16000);
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}
		}
	}
	
	// this is a dirty hack:
	private static class SkipStream extends BaseDoubleDataSource {
		DoubleDataSource source;
		int skip;
		public SkipStream(VocodingAudioStream source, int skip) {
			this.source = source;
			this.skip = skip;
		}

	    @Override
	    public int getData(double[] target, int targetPos, int length) {
	    	if (available() == 0) {
	    		try { Thread.sleep(2); // take it easy, buddy.
				} catch (InterruptedException e) { e.printStackTrace();	}
	    	}
	        int outputAmount = Math.min(available() / skip, length);
	        int inputAmount = outputAmount * skip;
	        double[] inputValues = new double[inputAmount];
	        source.getData(inputValues, 0, inputAmount);
	        for (int i = 0; i < outputAmount; i++) {
	        	target[i] = inputValues[i * skip];
	        }
	        return outputAmount;
	    }
	    
		@Override
		public boolean hasMoreData() {
			return source.hasMoreData();
		}
		
	    @Override
	    public int available() {
	        return source.available() / 2;
	    }

		@Override
	    public long getDataLength() {
    		return source.getDataLength() == DoubleDataSource.NOT_SPECIFIED ? DoubleDataSource.NOT_SPECIFIED : source.getDataLength() / 2; // not specified
	    }
	}
}
