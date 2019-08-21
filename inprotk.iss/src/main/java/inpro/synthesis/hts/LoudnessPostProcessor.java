package inpro.synthesis.hts;

import inpro.incremental.unit.SysSegmentIU;

/** 
 * a gross approximation to modelling intensity/loudness related speech features
 * adapts the loudness of every frame of every segment in the utterance.
 * has to be set as vocodingframepostprocessor for every segment it is supposed to influence
 * also, needs to be set as ChangeListener of the corresponding slider
 */
public class LoudnessPostProcessor implements VocodingFramePostProcessor {

	/** 0.5 for breathy voice, 2 for clear voicing */
	double voicingFactor = 1.0;
	double spectralEmphasis = 1.0; 
	double energy = 1.0;
	
	@Override
	public FullPFeatureFrame postProcess(SysSegmentIU segment, FullPFeatureFrame frame) {
		FullPFeatureFrame fout = new FullPFeatureFrame(frame);
		if (segment.isVowel()) {
			// increase voicing factor:
			for (int j = 0; j < 5; j++) 
				fout.getStrParVec()[j] = Math.pow(fout.getStrParVec()[j],1 / voicingFactor);
			// increase energy in higher frequency components:
			for (int j = 1; j < 25; j++) 
				fout.getMcepParVec()[j] *= Math.pow(spectralEmphasis, j);
		}
		// increase loudness:
		fout.getMcepParVec()[0] *= energy; // * energy;
		return fout;
	}
	/** source value should be in the interval [-100;100] */
	public void setLoudness(int value) {
		if (value < -100 || value > 100) 
			throw new IllegalArgumentException("argument must be within [-100;100], but it was " + value);
		voicingFactor =  value / 50; // normalize to [-2;+2]
		voicingFactor = Math.exp(voicingFactor * Math.log(2)); // convert to [-.25;4]
		spectralEmphasis = 1f + (value / 4000f); // [0.975;1.025]
		energy = .9f + (value / 1000f);
	}
}
