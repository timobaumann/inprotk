package inpro.synthesis.hts;

import marytts.datatypes.MaryData;
import marytts.htsengine.HMMData;
import marytts.htsengine.HMMVoice;
import marytts.htsengine.HTSParameterGeneration;
import marytts.htsengine.HTSUttModel;
import marytts.modules.HTSEngine;
import marytts.modules.synthesis.Voice;
import marytts.unitselection.select.Target;
import marytts.util.data.DoubleDataSource;
import marytts.util.data.audio.AppendableSequenceAudioInputStream;
import marytts.util.data.audio.DDSAudioInputStream;
import org.w3c.dom.Element;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.util.ArrayList;
import java.util.List;

public class InteractiveHTSEngine extends HTSEngine {

    public boolean synthesizeAudio = true; 
    
    public static boolean returnIncrementalAudioStream = false;
    
    public final List<SynthesisPayload> uttData = new ArrayList<SynthesisPayload>();
    
    @Override
    public MaryData process(MaryData d, List<Target> targetFeaturesList, List<Element> segmentsAndBoundaries, List<Element> tokensAndBoundaries)
    throws Exception
    {

        /** The utterance model, um, is a Vector (or linked list) of Model objects. 
         * It will contain the list of models for current label file. */
        /* Process label file of Mary context features and creates UttModel um */
    	Voice voice = d.getDefaultVoice();
    	assert (voice instanceof HMMVoice) : "Not an HMM voice: " + voice;
    	HMMData hmmData = ((HMMVoice) voice).getHMMData();
    	assert hmmData != null : "can't synthesize from a HMMVoice without HMM data...";
    	HTSUttModel um = processTargetList(targetFeaturesList, segmentsAndBoundaries, hmmData);
        assert um.getNumModel() == targetFeaturesList.size() : "UttModel size is " + um.getNumModel() + ", but targetFeaturesList size is " + targetFeaturesList.size();
        for (int i = 0; i < um.getNumModel(); i++) {
        	uttData.add(new SynthesisPayload(targetFeaturesList.get(i).getFeatureVector(), um.getUttModel(i), hmmData));
        }
        
        MaryData output = new MaryData(outputType(), d.getLocale());
        if (synthesizeAudio) {
            AudioInputStream ais = null;
            /* Process UttModel */
            // Generate sequence of speech parameter vectors, generate parameters out of sequence of pdf's  
            // # non-incremental MaryTTS version:
            /**/ HTSParameterGeneration npdf2par = new HTSParameterGeneration();
            npdf2par.htsMaximumLikelihoodParameterGeneration(um, hmmData);
            FullPStream pstream = new HTSFullPStream(npdf2par); /**/
            // # incremental pHTS version:
            /* PHTSParameterGeneration pdf2par = MaryAdapter5internal.getNewParamGen(); // new PHTSParameterGeneration(hmmv.getHMMData());
            FullPStream pstream = pdf2par.buildFullPStreamFor(uttHMMs); /**/
	        /* Vocode speech waveform out of sequence of parameters */
	        DoubleDataSource dds = new VocodingAudioStream(pstream, hmmData, returnIncrementalAudioStream);
	        float sampleRate = 16000.0F;  //8000,11025,16000,22050,44100
	        int sampleSizeInBits = 16;  //8,16
	        int channels = 1;     //1,2
	        boolean signed = true;    //true,false
	        boolean bigEndian = false;  //true,false
	        AudioFormat af = new AudioFormat(
	              sampleRate,
	              sampleSizeInBits,
	              channels,
	              signed,
	              bigEndian);
	        ais = new DDSAudioInputStream(dds, af);
	        if (d.getAudioFileFormat() != null) {
	            output.setAudioFileFormat(d.getAudioFileFormat());
	            if (d.getAudio() != null) {
	               // This (empty) AppendableSequenceAudioInputStream object allows a 
	               // thread reading the audio data on the other "end" to get to our data as we are producing it.
	                assert d.getAudio() instanceof AppendableSequenceAudioInputStream;
	                output.setAudio(d.getAudio());
	            }
	        }     
	        output.appendAudio(ais);
        }	       
        // set the actualDurations in tokensAndBoundaries
        if(tokensAndBoundaries != null)
            setRealisedProsody(tokensAndBoundaries, um);
        
        return output;
    }

    public List<SynthesisPayload> getUttData() {
		assert uttData != null : "You are calling getUttHMMs without having called my process() method before (Hint: you may think that it was called but the buildpath order might be in your way.)";
		return new ArrayList<SynthesisPayload>(uttData);
    }
    
	public void resetUttHMMstore() {
		uttData.clear();
	}
    
}