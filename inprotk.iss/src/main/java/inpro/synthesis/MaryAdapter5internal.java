package inpro.synthesis;

import inpro.incremental.unit.PhraseIU;
import inpro.incremental.unit.WordIU;
import inpro.incremental.util.TTSUtil;
import inpro.synthesis.hts.InteractiveHTSEngine;
import inpro.synthesis.hts.PHTSParameterGeneration;
import marytts.LocalMaryInterface;
import marytts.MaryInterface;
import marytts.datatypes.MaryDataType;
import marytts.exceptions.MaryConfigurationException;
import marytts.htsengine.HMMData;
import marytts.htsengine.HMMVoice;
import marytts.modules.ModuleRegistry;
import marytts.modules.synthesis.Voice;
import marytts.server.Request;
import marytts.util.MaryUtils;
import org.apache.log4j.Logger;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class MaryAdapter5internal extends MaryAdapter {

	private MaryInterface maryInterface;

	private static Logger logger = Logger.getLogger(MaryAdapter5internal.class);

	protected MaryAdapter5internal() {
		maryInterface = null;
		try {
			maryInterface = new LocalMaryInterface();
		} catch (MaryConfigurationException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	/** 
	 * implement the main inprotk<->marytts interaction via mary's interface. 
	 * (additional data for HMM synthesis is exchanged via subclassing HTSEngine) 
	 */ 
	@Override
	protected ByteArrayOutputStream process(String query, String inputType,
			String outputType, String audioType) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		MaryDataType input = MaryDataType.get(inputType);
		MaryDataType output = MaryDataType.get(outputType);
		assert !(inputType.equals("MBROLA") || outputType.equals("MBROLA")) : 
			"There's no MBROLA support in internalized Mary 5, please use an external Mary 4 server";

		Locale mLocale = MaryUtils.string2locale(System.getProperty("inpro.tts.language", "de"));
		String voiceName = System.getProperty("inpro.tts.voice", System.getProperty("inpro.tts.voice", "bits1-hsmm"));
		maryInterface.setVoice(voiceName);
		Voice voice = Voice.getVoice(voiceName);
		AudioFormat audioFormat = voice.dbAudioFormat();

		audioFormat = new AudioFormat(16000, audioFormat.getSampleSizeInBits(), audioFormat.getChannels(), true, audioFormat.isBigEndian());
		assert audioFormat.getSampleRate() == 16000f : "InproTK cannot handle voices with sample rates other than 16000Hz, your's is " + audioFormat.getSampleRate();
		logger.debug("audioFormat is " + audioFormat);
		logger.debug("query is " + query);
		assert voice != null : "Cannot find the Mary voice " + voiceName;

		AudioFileFormat.Type audioFileFormatType = AudioFileFormat.Type.WAVE;
		logger.trace("audioFileFormatType is " + audioFileFormatType);
		AudioFileFormat audioFileFormat = new AudioFileFormat(
				audioFileFormatType, audioFormat, AudioSystem.NOT_SPECIFIED);
		logger.trace("audioFileFormat is " + audioFileFormat);

		Request req = new Request(input, output, mLocale, voice, null,
                null, 1, audioFileFormat);

		 try {
		        req.setInputData(query);
		        req.process();
		        req.writeOutputData(baos);
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }
	        return baos;
	}

	/** needs to be overridden for IHTSE access */
	@Override
	public List<PhraseIU> fullySpecifiedMarkup2PhraseIUs(String markup) {
		InteractiveHTSEngine ihtse = (InteractiveHTSEngine) ModuleRegistry.getModule(InteractiveHTSEngine.class);
		ihtse.resetUttHMMstore();
		ihtse.synthesizeAudio = false;
		InputStream is = fullySpecifiedMarkup2maryxml(markup);
		// useful for looking at Mary's XML (for debugging): 
		//printStream(is); ihtse.resetUttHMMstore(); is = fullyCompleteMarkup2maryxml(markup);
		List<PhraseIU> groundedIn = TTSUtil.phraseIUsFromMaryXML(is, ihtse.getUttData(), true);
		return groundedIn;
	}

	/** needs to be overridden for IHTSE access */
	@Override
	protected synchronized List<? extends WordIU> text2IUs(String tts, boolean keepPhrases, boolean connectPhrases) {
		InteractiveHTSEngine ihtse = (InteractiveHTSEngine) ModuleRegistry.getModule(InteractiveHTSEngine.class);
		assert ihtse != null : "maybe there is a problem with your classpath ordering?";
		ihtse.resetUttHMMstore();
		ihtse.synthesizeAudio = false;
		InputStream is = text2maryxml(tts);
		//printStream(is); ihtse.resetUttHMMstore(); is = markup2maryxml(markup);
		try {
			return createIUsFromInputStream(is, ihtse.getUttData(), keepPhrases, connectPhrases);
		} catch (AssertionError ae) {
			is = text2maryxml(tts);
			java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(is));
			String line = null;
			try {
				while((line = in.readLine()) != null) {
				  System.err.println(line);
				}
			} catch (IOException e) { }
			throw new RuntimeException(ae);
		}
	}
	
	public static HMMData getDefaultHMMData() {
		String defaultVoiceName = System.getProperty("inpro.tts.voice", System.getProperty("inpro.tts.voice", "bits1-hsmm"));
		Voice voice = Voice.getVoice(defaultVoiceName);
		assert (voice instanceof HMMVoice);
		return ((HMMVoice) voice).getHMMData();
	}

	public static PHTSParameterGeneration getNewParamGen() {
		return new PHTSParameterGeneration(getDefaultHMMData());
	}

    /** print Mary's XML to stderr */ 
	@SuppressWarnings("unused")
	private void printStream(InputStream is) {
		java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(is));
		String line = null;
		try {
			while((line = in.readLine()) != null) {
			  System.err.println(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
